package com.shootoff.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.shootoff.camera.autocalibration.GreenScreenCalibrationManager;
import com.shootoff.camera.cameratypes.Camera;

import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;

public class TestGreenScreenCalibration {
	@Before
	public void setUp() {
		nu.pattern.OpenCV.loadShared();
	}

	@Test
	public void testGreenScreenCalibrationCreatesPerspectiveTransform() {
		final BufferedImage frame = new BufferedImage(640, 480, BufferedImage.TYPE_3BYTE_BGR);
		final Graphics2D graphics = frame.createGraphics();
		graphics.setColor(java.awt.Color.BLACK);
		graphics.fillRect(0, 0, frame.getWidth(), frame.getHeight());
		graphics.setColor(new java.awt.Color(0, 255, 65));
		graphics.fill(new Polygon(new int[] { 120, 520, 500, 100 }, new int[] { 80, 110, 380, 350 }, 4));
		graphics.dispose();

		final CapturingCalibrationListener calibrationListener = new CapturingCalibrationListener();
		final GreenScreenCalibrationManager calibrationManager = new GreenScreenCalibrationManager(calibrationListener);

		calibrationManager.processFrame(new Frame(Camera.bufferedImageToMat(frame), 1_000L));

		assertTrue(calibrationListener.bounds.isPresent());
		assertTrue(calibrationManager.getBoundingBox().isPresent());

		final Bounds bounds = calibrationManager.getBoundingBox().get();
		assertTrue(bounds.getWidth() > 350);
		assertTrue(bounds.getHeight() > 220);

		final Point topLeft = calibrationManager.undistortCoords(120, 80);
		final Point topRight = calibrationManager.undistortCoords(520, 110);
		final Point bottomRight = calibrationManager.undistortCoords(500, 380);
		final Point bottomLeft = calibrationManager.undistortCoords(100, 350);

		assertEquals(bounds.getMinX(), topLeft.getX(), 35.0);
		assertEquals(bounds.getMinY(), topLeft.getY(), 35.0);
		assertEquals(bounds.getMaxX(), topRight.getX(), 35.0);
		assertEquals(bounds.getMinY(), topRight.getY(), 35.0);
		assertEquals(bounds.getMaxX(), bottomRight.getX(), 35.0);
		assertEquals(bounds.getMaxY(), bottomRight.getY(), 35.0);
		assertEquals(bounds.getMinX(), bottomLeft.getX(), 35.0);
		assertEquals(bounds.getMaxY(), bottomLeft.getY(), 35.0);
	}

	private static class CapturingCalibrationListener implements CameraCalibrationListener {
		private Optional<Bounds> bounds = Optional.empty();

		@Override
		public void calibrate(Bounds arenaBounds, Optional<Dimension2D> perspectivePaperDims,
				boolean calibratedFromCanvas, long frameDelay) {
			bounds = Optional.of(arenaBounds);
		}

		@Override
		public void setArenaBackground(String resourceFilename) {}
	}
}
