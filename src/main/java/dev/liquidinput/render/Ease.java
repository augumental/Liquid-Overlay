package dev.liquidinput.render;

public final class Ease {
	private Ease() {}

	public static float clamp01(float t) {
		return t < 0 ? 0 : (t > 1 ? 1 : t);
	}

	public static float outCubic(float t) {
		t = clamp01(t);
		float u = 1 - t;
		return 1 - u * u * u;
	}

	public static float inOutCubic(float t) {
		t = clamp01(t);
		return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
	}

	public static float outQuint(float t) {
		t = clamp01(t);
		float u = 1 - t;
		return 1 - u * u * u * u * u;
	}

	public static float outBack(float t) {
		return outBack(t, 1.70158f);
	}

	public static float outBack(float t, float s) {
		t = clamp01(t) - 1;
		return t * t * ((s + 1) * t + s) + 1;
	}

	public static float outElastic(float t) {
		t = clamp01(t);
		if (t == 0 || t == 1) return t;
		return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
	}

	/** Damped spring wobble starting at 1 and settling to 0. */
	public static float wobble(float ms, float periodMs, float decayMs) {
		if (ms < 0) return 0;
		return (float) (Math.exp(-ms / decayMs) * Math.cos(ms / periodMs * Math.PI * 2));
	}

	/** Frame-rate independent approach of current toward target. */
	public static float approach(float current, float target, float speedPerSecond, float dtSeconds) {
		float k = 1 - (float) Math.exp(-speedPerSecond * dtSeconds);
		return current + (target - current) * k;
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
