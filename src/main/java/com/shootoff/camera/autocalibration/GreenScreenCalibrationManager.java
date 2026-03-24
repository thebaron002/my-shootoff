package com.shootoff.camera.autocalibration;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shootoff.camera.CameraCalibrationListener;
import com.shootoff.camera.Frame;
import com.shootoff.camera.cameratypes.Camera;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;

public class GreenScreenCalibrationManager {
	private static final Logger logger = LoggerFactory.getLogger(GreenScreenCalibrationManager.class);

	private static final int MIN_GREEN_VALUE = 100;
	private static final double GREEN_DOMINANCE_RATIO = 1.3;
	private static final int MIN_GREEN_PIXELS = 200;
	private static final double MIN_CONTOUR_AREA_RATIO = 0.02;
	private static final long MINIMUM_INTERVAL = 150;
	private static final Size MORPH_KERNEL_SIZE = new Size(5, 5);

	private final CameraCalibrationListener calibrationListener;

	private Mat perspectiveMatrix = null;
	private Bounds boundingBox = null;
	private boolean calibrated = false;
	private long lastFrameCheck = 0;

	public GreenScreenCalibrationManager(final CameraCalibrationListener calibrationListener) {
		this.calibrationListener = calibrationListener;
	}

	public void reset() {
		perspectiveMatrix = null;
		boundingBox = null;
		calibrated = false;
		lastFrameCheck = 0;
	}

	public Optional<Bounds> getBoundingBox() {
		return Optional.ofNullable(boundingBox);
	}

	public void processFrame(final Frame frame) {
		if (calibrated || frame.getTimestamp() - lastFrameCheck < MINIMUM_INTERVAL) return;

		lastFrameCheck = frame.getTimestamp();

		final Optional<MatOfPoint2f> greenScreen = findGreenScreen(frame.getOriginalMat());
		if (!greenScreen.isPresent()) return;

		initializeWarpPerspective(frame.getOriginalMat(), greenScreen.get());

		if (boundingBox == null || boundingBox.getMinX() < 0 || boundingBox.getMinY() < 0
				|| boundingBox.getMaxX() > frame.getOriginalMat().cols()
				|| boundingBox.getMaxY() > frame.getOriginalMat().rows()) {
			logger.debug("Discarded green calibration because bounds are out of frame: {}", boundingBox);
			reset();
			return;
		}

		calibrated = true;
		calibrationListener.calibrate(boundingBox, Optional.<Dimension2D>empty(), false, -1);
	}

	public Frame undistortFrame(final Frame frame) {
		if (!calibrated) return frame;

		frame.setMat(warpPerspective(frame.getOriginalMat()));
		return frame;
	}

	public BufferedImage undistortFrame(final BufferedImage image) {
		if (!calibrated) return image;
		return Camera.matToBufferedImage(warpPerspective(Camera.bufferedImageToMat(image)));
	}

	public java.awt.Point undistortCoords(final int x, final int y) {
		if (!calibrated || perspectiveMatrix == null) return new java.awt.Point(x, y);

		final MatOfPoint2f point = new MatOfPoint2f();
		point.fromArray(new Point(x, y));
		Core.perspectiveTransform(point, point, perspectiveMatrix);

		return new java.awt.Point((int) point.get(0, 0)[0], (int) point.get(0, 0)[1]);
	}

	private Optional<MatOfPoint2f> findGreenScreen(final Mat sourceFrame) {
		final Mat mask = buildGreenMask(sourceFrame);
		if (mask.empty()) return Optional.empty();

		final List<MatOfPoint> contours = new ArrayList<>();
		Imgproc.findContours(mask.clone(), contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

		if (contours.isEmpty()) return Optional.empty();

		final double minimumContourArea = sourceFrame.rows() * sourceFrame.cols() * MIN_CONTOUR_AREA_RATIO;
		MatOfPoint largestContour = null;
		double largestArea = 0;

		for (final MatOfPoint contour : contours) {
			final double area = Imgproc.contourArea(contour);
			if (area > largestArea) {
				largestArea = area;
				largestContour = contour;
			}
		}

		if (largestContour == null || largestArea < minimumContourArea) return Optional.empty();

		final Point[] corners = locateCorners(largestContour.toArray());
		if (!hasFourDistinctCorners(corners)) return Optional.empty();

		final MatOfPoint contourCorners = new MatOfPoint(corners);
		if (Imgproc.contourArea(contourCorners) < minimumContourArea) return Optional.empty();

		final MatOfPoint2f sourceCorners = new MatOfPoint2f();
		sourceCorners.fromArray(corners);
		return Optional.of(sourceCorners);
	}

	private Mat buildGreenMask(final Mat sourceFrame) {
		final Mat bgrFrame = ensureBgr(sourceFrame);
		if (bgrFrame.empty()) return new Mat();

		final byte[] frameBytes = new byte[(int) (bgrFrame.total() * bgrFrame.channels())];
		bgrFrame.get(0, 0, frameBytes);

		final byte[] maskBytes = new byte[(int) bgrFrame.total()];
		int greenPixelCount = 0;

		for (int srcIndex = 0, maskIndex = 0; srcIndex < frameBytes.length; srcIndex += 3, maskIndex++) {
			final int blue = frameBytes[srcIndex] & 0xFF;
			final int green = frameBytes[srcIndex + 1] & 0xFF;
			final int red = frameBytes[srcIndex + 2] & 0xFF;

			if (green >= MIN_GREEN_VALUE && green > red * GREEN_DOMINANCE_RATIO
					&& green > blue * GREEN_DOMINANCE_RATIO) {
				maskBytes[maskIndex] = (byte) 255;
				greenPixelCount++;
			}
		}

		if (greenPixelCount < MIN_GREEN_PIXELS) return new Mat();

		final Mat mask = new Mat(bgrFrame.rows(), bgrFrame.cols(), CvType.CV_8UC1);
		mask.put(0, 0, maskBytes);

		final Mat morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, MORPH_KERNEL_SIZE);
		Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, morphKernel);
		Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, morphKernel);

		return mask;
	}

	private Mat ensureBgr(final Mat sourceFrame) {
		if (sourceFrame.channels() == 3) return sourceFrame;
		if (sourceFrame.channels() == 4) {
			final Mat bgrFrame = new Mat();
			Imgproc.cvtColor(sourceFrame, bgrFrame, Imgproc.COLOR_BGRA2BGR);
			return bgrFrame;
		}

		return new Mat();
	}

	private Point[] locateCorners(final Point[] points) {
		Point topLeft = null;
		Point topRight = null;
		Point bottomRight = null;
		Point bottomLeft = null;

		double minSum = Double.MAX_VALUE;
		double maxSum = -Double.MAX_VALUE;
		double minDiff = Double.MAX_VALUE;
		double maxDiff = -Double.MAX_VALUE;

		for (final Point point : points) {
			final double sum = point.x + point.y;
			final double diff = point.x - point.y;

			if (sum < minSum) {
				minSum = sum;
				topLeft = point;
			}

			if (sum > maxSum) {
				maxSum = sum;
				bottomRight = point;
			}

			if (diff < minDiff) {
				minDiff = diff;
				bottomLeft = point;
			}

			if (diff > maxDiff) {
				maxDiff = diff;
				topRight = point;
			}
		}

		return new Point[] { topLeft, topRight, bottomRight, bottomLeft };
	}

	private boolean hasFourDistinctCorners(final Point[] corners) {
		if (corners.length != 4) return false;

		final Set<String> distinctCorners = new HashSet<>();
		for (final Point corner : corners) {
			if (corner == null) return false;
			distinctCorners.add((int) corner.x + ":" + (int) corner.y);
		}

		return distinctCorners.size() == 4;
	}

	private void initializeWarpPerspective(final Mat frame, final MatOfPoint2f sourceCorners) {
		final RotatedRect boundsRect = Imgproc.minAreaRect(sourceCorners);
		final Rect rect = boundsRect.boundingRect();

		final MatOfPoint2f destCorners = new MatOfPoint2f();
		destCorners.fromArray(new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y),
				new Point(rect.x + rect.width, rect.y + rect.height), new Point(rect.x, rect.y + rect.height));

		perspectiveMatrix = Imgproc.getPerspectiveTransform(sourceCorners, destCorners);

		int width = rect.width;
		int height = rect.height;

		if ((width & 1) == 1) width++;
		if ((height & 1) == 1) height++;

		boundingBox = new BoundingBox(rect.x, rect.y, width, height);

		if (logger.isTraceEnabled()) {
			logger.trace("Green calibration corners {} {} {} {}", sourceCorners.get(0, 0), sourceCorners.get(1, 0),
					sourceCorners.get(2, 0), sourceCorners.get(3, 0));
			logger.trace("Green calibration bounds {}", boundingBox);
		}
	}

	private Mat warpPerspective(final Mat frame) {
		if (!calibrated || perspectiveMatrix == null) return frame;

		final Mat corrected = new Mat();
		Imgproc.warpPerspective(frame, corrected, perspectiveMatrix, frame.size(), Imgproc.INTER_LINEAR);
		return corrected;
	}
}
