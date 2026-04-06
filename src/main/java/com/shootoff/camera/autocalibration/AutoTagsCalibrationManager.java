package com.shootoff.camera.autocalibration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shootoff.camera.CameraCalibrationListener;
import com.shootoff.camera.Frame;

import boofcv.abst.fiducial.SquareHamming_to_FiducialDetector;
import boofcv.factory.fiducial.ConfigFiducialHammingDetector;
import boofcv.factory.fiducial.ConfigHammingMarker;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.factory.fiducial.HammingDictionary;
import boofcv.struct.image.GrayU8;
import georegression.struct.point.Point2D_F64;
import georegression.struct.shapes.Polygon2D_F64;
import javafx.geometry.Point2D;

public class AutoTagsCalibrationManager {
	private static final Logger logger = LoggerFactory.getLogger(AutoTagsCalibrationManager.class);
	private static final long MINIMUM_INTERVAL = 120;
	private static final int REQUIRED_STABLE_FRAMES = 4;
	private static final double MAX_CORNER_DELTA_PX = 12.0;
	private static final double QUIET_ZONE_TO_BODY_RATIO = (1.0 - 0.72) / (2.0 * 0.72);

	private final CameraCalibrationListener calibrationListener;
	private final SquareHamming_to_FiducialDetector<GrayU8> detector;

	private GrayU8 grayFrame = new GrayU8(1, 1);
	private long lastFrameCheck = 0;
	private boolean locked = false;
	private int stableFrameCount = 0;
	private Optional<List<Point2D>> lastDetectedCorners = Optional.empty();

	public AutoTagsCalibrationManager(CameraCalibrationListener calibrationListener) {
		this.calibrationListener = calibrationListener;
		final ConfigHammingMarker markerConfig = ConfigHammingMarker.loadDictionary(HammingDictionary.APRILTAG_36h11);
		markerConfig.targetWidth = 1.0;
		detector = FactoryFiducial.squareHamming(markerConfig, new ConfigFiducialHammingDetector(), GrayU8.class);
	}

	public void reset() {
		lastFrameCheck = 0;
		locked = false;
		stableFrameCount = 0;
		lastDetectedCorners = Optional.empty();
	}

	public void processFrame(Frame frame) {
		if (locked || frame.getTimestamp() - lastFrameCheck < MINIMUM_INTERVAL) return;
		lastFrameCheck = frame.getTimestamp();
		detector.detect(convertToGray(frame.getOriginalMat()));

		final Optional<List<Point2D>> detectedCorners = extractArenaCorners(frame.getOriginalMat().cols(),
				frame.getOriginalMat().rows());
		if (!detectedCorners.isPresent()) {
			stableFrameCount = 0;
			lastDetectedCorners = Optional.empty();
			return;
		}

		if (lastDetectedCorners.isPresent() && areCornersStable(lastDetectedCorners.get(), detectedCorners.get())) {
			stableFrameCount++;
		} else {
			stableFrameCount = 1;
		}

		lastDetectedCorners = Optional.of(copyCorners(detectedCorners.get()));
		if (stableFrameCount < REQUIRED_STABLE_FRAMES) return;

		locked = true;
		logger.info("Auto Tags locked after {} stable frames", stableFrameCount);
		calibrationListener.calibrationCornersDetected(copyCorners(detectedCorners.get()), false);
	}

	private GrayU8 convertToGray(Mat frame) {
		final int width = frame.cols();
		final int height = frame.rows();
		final int channels = frame.channels();
		grayFrame.reshape(width, height);
		final byte[] source = new byte[width * height * channels];
		frame.get(0, 0, source);

		if (channels == 1) {
			System.arraycopy(source, 0, grayFrame.data, 0, width * height);
			return grayFrame;
		}

		int grayIndex = 0;
		for (int sourceIndex = 0; sourceIndex < source.length; sourceIndex += channels) {
			final int blue = source[sourceIndex] & 0xFF;
			final int green = source[sourceIndex + 1] & 0xFF;
			final int red = source[sourceIndex + 2] & 0xFF;
			grayFrame.data[grayIndex++] = (byte) ((red * 299 + green * 587 + blue * 114) / 1000);
		}
		return grayFrame;
	}

	private Optional<List<Point2D>> extractArenaCorners(int frameWidth, int frameHeight) {
		final Map<Long, Point2D[]> markerCorners = new HashMap<>();

		for (int i = 0; i < detector.totalFound(); i++) {
			final long id = detector.getId(i);
			if (id < 0 || id > 3 || markerCorners.containsKey(id)) continue;
			final Polygon2D_F64 polygon = detector.getBounds(i, null);
			if (polygon == null || polygon.size() < 4) continue;
			final Point2D[] orderedCorners = canonicalizeCorners(polygon);
			if (!allCornersInBounds(orderedCorners, frameWidth, frameHeight)) continue;
			markerCorners.put(id, orderedCorners);
		}

		if (!markerCorners.keySet().containsAll(Arrays.asList(0L, 1L, 2L, 3L))) return Optional.empty();

		final List<Point2D> arenaCorners = new ArrayList<>(4);
		arenaCorners.add(clampToFrame(extrapolateScreenCorner(markerCorners.get(0L), 0), frameWidth, frameHeight));
		arenaCorners.add(clampToFrame(extrapolateScreenCorner(markerCorners.get(1L), 1), frameWidth, frameHeight));
		arenaCorners.add(clampToFrame(extrapolateScreenCorner(markerCorners.get(2L), 2), frameWidth, frameHeight));
		arenaCorners.add(clampToFrame(extrapolateScreenCorner(markerCorners.get(3L), 3), frameWidth, frameHeight));
		if (!hasDistinctCorners(arenaCorners)) return Optional.empty();
		return Optional.of(arenaCorners);
	}

	private Point2D extrapolateScreenCorner(Point2D[] corners, int screenCornerIndex) {
		final Point2D corner = corners[screenCornerIndex];
		final Point2D previous = corners[(screenCornerIndex + 3) % 4];
		final Point2D next = corners[(screenCornerIndex + 1) % 4];

		final Point2D awayFromPrevious = corner.subtract(previous).multiply(QUIET_ZONE_TO_BODY_RATIO);
		final Point2D awayFromNext = corner.subtract(next).multiply(QUIET_ZONE_TO_BODY_RATIO);

		return corner.add(awayFromPrevious).add(awayFromNext);
	}

	private Point2D clampToFrame(Point2D point, int frameWidth, int frameHeight) {
		return new Point2D(clamp(point.getX(), 0.0, frameWidth), clamp(point.getY(), 0.0, frameHeight));
	}

	private double clamp(double value, double minimum, double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private Point2D[] canonicalizeCorners(Polygon2D_F64 polygon) {
		Point2D topLeft = null;
		Point2D topRight = null;
		Point2D bottomRight = null;
		Point2D bottomLeft = null;
		double minSum = Double.MAX_VALUE;
		double maxSum = -Double.MAX_VALUE;
		double minDiff = Double.MAX_VALUE;
		double maxDiff = -Double.MAX_VALUE;

		for (int i = 0; i < polygon.size(); i++) {
			final Point2D_F64 point = polygon.get(i);
			final Point2D fxPoint = new Point2D(point.x, point.y);
			final double sum = point.x + point.y;
			final double diff = point.x - point.y;
			if (sum < minSum) {
				minSum = sum;
				topLeft = fxPoint;
			}
			if (sum > maxSum) {
				maxSum = sum;
				bottomRight = fxPoint;
			}
			if (diff < minDiff) {
				minDiff = diff;
				bottomLeft = fxPoint;
			}
			if (diff > maxDiff) {
				maxDiff = diff;
				topRight = fxPoint;
			}
		}
		return new Point2D[] { topLeft, topRight, bottomRight, bottomLeft };
	}

	private boolean allCornersInBounds(Point2D[] corners, int frameWidth, int frameHeight) {
		for (Point2D corner : corners) {
			if (corner == null) return false;
			if (corner.getX() < 0 || corner.getY() < 0 || corner.getX() > frameWidth || corner.getY() > frameHeight)
				return false;
		}
		return true;
	}

	private boolean hasDistinctCorners(List<Point2D> corners) {
		if (corners.size() != 4) return false;
		for (int i = 0; i < corners.size(); i++) {
			final Point2D firstCorner = corners.get(i);
			if (firstCorner == null) return false;
			for (int j = i + 1; j < corners.size(); j++) {
				if (firstCorner.distance(corners.get(j)) < 1.0) return false;
			}
		}
		return true;
	}

	private boolean areCornersStable(List<Point2D> previousCorners, List<Point2D> currentCorners) {
		if (previousCorners.size() != currentCorners.size()) return false;
		for (int i = 0; i < previousCorners.size(); i++) {
			if (previousCorners.get(i).distance(currentCorners.get(i)) > MAX_CORNER_DELTA_PX) return false;
		}
		return true;
	}

	private List<Point2D> copyCorners(List<Point2D> source) {
		return new ArrayList<>(source);
	}
}
