package dev.liquidinput.input;

import dev.liquidinput.config.ConfigManager;
import dev.liquidinput.config.OverlayConfig;

/** Lifetime statistics shown on the Profile tab (persisted with the config). */
public final class Stats {
	private static final long SESSION_START = System.currentTimeMillis();
	private static int sessionKeys, sessionClicks, sessionPeakCps;

	private Stats() {}

	private static OverlayConfig.StatsData data() {
		return ConfigManager.get().stats;
	}

	static void onKey() {
		data().totalKeys++;
		sessionKeys++;
	}

	static void onClick(int button) {
		OverlayConfig.StatsData d = data();
		if (button == PhysicalInput.MB_LEFT) d.totalLeft++;
		else if (button == PhysicalInput.MB_RIGHT) d.totalRight++;
		else d.totalOther++;
		sessionClicks++;
	}

	static void peakCps(int left, int right) {
		OverlayConfig.StatsData d = data();
		if (left > d.peakLeftCps) d.peakLeftCps = left;
		if (right > d.peakRightCps) d.peakRightCps = right;
		sessionPeakCps = Math.max(sessionPeakCps, Math.max(left, right));
	}

	public static long sessionMillis() {
		return System.currentTimeMillis() - SESSION_START;
	}

	public static int sessionKeys() {
		return sessionKeys;
	}

	public static int sessionClicks() {
		return sessionClicks;
	}

	public static int sessionPeakCps() {
		return sessionPeakCps;
	}

	public static void reset() {
		ConfigManager.get().stats = new OverlayConfig.StatsData();
		sessionKeys = sessionClicks = sessionPeakCps = 0;
		PhysicalInput.resetCounters();
		ConfigManager.save();
	}
}
