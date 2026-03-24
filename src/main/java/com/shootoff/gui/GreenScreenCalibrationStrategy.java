package com.shootoff.gui;

public class GreenScreenCalibrationStrategy implements CalibrationStrategy {
	@Override
	public CalibrationMode getMode() {
		return CalibrationMode.AUTO_GREEN;
	}

	@Override
	public void start(CalibrationManager calibrationManager) {
		calibrationManager.beginAutoGreenCalibration();
	}
}
