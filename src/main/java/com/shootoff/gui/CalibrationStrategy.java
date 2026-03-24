package com.shootoff.gui;

public interface CalibrationStrategy {
	CalibrationMode getMode();

	void start(CalibrationManager calibrationManager);
}
