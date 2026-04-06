package com.shootoff.gui;

public class AutoTagsCalibrationStrategy implements CalibrationStrategy {
	@Override
	public CalibrationMode getMode() {
		return CalibrationMode.AUTO_TAGS;
	}

	@Override
	public void start(CalibrationManager calibrationManager) {
		calibrationManager.beginAutoTagsCalibration();
	}
}
