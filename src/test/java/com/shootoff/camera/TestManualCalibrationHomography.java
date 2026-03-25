package com.shootoff.camera;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;

import javafx.geometry.BoundingBox;

public class TestManualCalibrationHomography {

	@Before
	public void setUp() {
		nu.pattern.OpenCV.loadShared();
	}

	@Test
	public void testUndistortCoordsWithManualHomography() {
		MockCameraManager cameraManager = new MockCameraManager();
		
		// 1. Simulate a skewed quadrilateral in the camera feed (as might be seen if projector is at an angle)
		// Top-Left, Top-Right, Bottom-Right, Bottom-Left
		Point[] cvPts = new Point[] {
				new Point(100, 150),
				new Point(500, 100),
				new Point(550, 400),
				new Point(80, 450)
		};
		
		MatOfPoint2f sourceCorners = new MatOfPoint2f();
		sourceCorners.fromArray(cvPts);
		
		RotatedRect boundsRect = Imgproc.minAreaRect(sourceCorners);
		Rect rect = boundsRect.boundingRect();
		
		int width = rect.width;
		int height = rect.height;
		if ((width & 1) == 1) width++;
		if ((height & 1) == 1) height++;

		MatOfPoint2f destCorners = new MatOfPoint2f();
		destCorners.fromArray(
			new Point(rect.x, rect.y), 
			new Point(rect.x + width, rect.y),
			new Point(rect.x + width, rect.y + height), 
			new Point(rect.x, rect.y + height)
		);

		Mat manualPerspectiveMatrix = Imgproc.getPerspectiveTransform(sourceCorners, destCorners);
		cameraManager.setManualPerspectiveMatrix(manualPerspectiveMatrix);
		cameraManager.setProjectionBounds(new BoundingBox(rect.x, rect.y, width, height));

		// Test undistorting the extreme edges (Corners)
		java.awt.Point tl = cameraManager.undistortCoords(100, 150);
		assertEquals("Top left X", rect.x, tl.x, 2.0);
		assertEquals("Top left Y", rect.y, tl.y, 2.0);

		java.awt.Point tr = cameraManager.undistortCoords(500, 100);
		assertEquals("Top right X", rect.x + width, tr.x, 2.0);
		assertEquals("Top right Y", rect.y, tr.y, 2.0);

		java.awt.Point br = cameraManager.undistortCoords(550, 400);
		assertEquals("Bottom right X", rect.x + width, br.x, 2.0);
		assertEquals("Bottom right Y", rect.y + height, br.y, 2.0);

		java.awt.Point bl = cameraManager.undistortCoords(80, 450);
		assertEquals("Bottom left X", rect.x, bl.x, 2.0);
		assertEquals("Bottom left Y", rect.y + height, bl.y, 2.0);
        
        // Test an arbitrary random point in the center
        // Center of the source quadrilateral is roughly around (307, 275)
        java.awt.Point center = cameraManager.undistortCoords(307, 275);
        
        // It should map roughly to the center of the bounding rectangle
        assertEquals("Center X", rect.x + (width/2.0), center.x, 15.0);
        assertEquals("Center Y", rect.y + (height/2.0), center.y, 15.0);
        
        // Test points randomly near an edge to ensure the homography handles interpolation correctly
        // E.g., a point halfway between Top-Left(100, 150) and Top-Right(500, 100) -> (300, 125)
        java.awt.Point topEdgePoint = cameraManager.undistortCoords(300, 125);
        assertEquals("Top Edge intermediate X", rect.x + (width/2.0), topEdgePoint.x, 5.0);
        assertEquals("Top Edge intermediate Y", rect.y, topEdgePoint.y, 5.0);
	}
}
