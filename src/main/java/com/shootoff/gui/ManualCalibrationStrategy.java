package com.shootoff.gui;

public class ManualCalibrationStrategy implements CalibrationStrategy {
	@Override
	public CalibrationMode getMode() {
		return CalibrationMode.MANUAL;
	}

	@Override
	public void start(CalibrationManager calibrationManager) {
		calibrationManager.beginManualCalibration();
	}
}
