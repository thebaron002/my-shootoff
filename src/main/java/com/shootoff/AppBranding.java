package com.shootoff;

public final class AppBranding {
	private static final String DEFAULT_APP_NAME = "ShootOFF 5.0";

	private AppBranding() {}

	public static String getAppName() {
		return System.getProperty("shootoff.brand.name", DEFAULT_APP_NAME);
	}

	public static String getArenaName() {
		return System.getProperty("shootoff.brand.arenaName", getAppName() + " Arena");
	}

	public static String getVersionedAppName(String version) {
		if (getAppName().matches(".*\\d+(?:\\.\\d+)*.*")) {
			return getAppName();
		}

		return getAppName() + " " + version;
	}
}
