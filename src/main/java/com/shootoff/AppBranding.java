package com.shootoff;

public final class AppBranding {
	private static final String DEFAULT_APP_NAME = "My ShootOFF";

	private AppBranding() {}

	public static String getAppName() {
		return System.getProperty("shootoff.brand.name", DEFAULT_APP_NAME);
	}

	public static String getArenaName() {
		return System.getProperty("shootoff.brand.arenaName", getAppName() + " Arena");
	}

	public static String getVersionedAppName(String version) {
		return getAppName() + " " + version;
	}
}
