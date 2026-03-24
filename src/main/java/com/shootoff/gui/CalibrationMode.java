package com.shootoff.gui;

public enum CalibrationMode {
	MANUAL("Manual"),
	AUTO_GREEN("Auto Green");

	private final String displayName;

	CalibrationMode(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
