package dev.liquidinput.render;

public final class Colors {
	private Colors() {}

	public static int a(int c) {
		return c >>> 24;
	}

	public static int r(int c) {
		return (c >> 16) & 0xFF;
	}

	public static int g(int c) {
		return (c >> 8) & 0xFF;
	}

	public static int b(int c) {
		return c & 0xFF;
	}

	public static int argb(int a, int r, int g, int b) {
		return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static int clamp(int v) {
		return v < 0 ? 0 : Math.min(255, v);
	}

	public static int lerp(int c1, int c2, float t) {
		if (t <= 0) return c1;
		if (t >= 1) return c2;
		return argb(
				Math.round(a(c1) + (a(c2) - a(c1)) * t),
				Math.round(r(c1) + (r(c2) - r(c1)) * t),
				Math.round(g(c1) + (g(c2) - g(c1)) * t),
				Math.round(b(c1) + (b(c2) - b(c1)) * t));
	}

	public static int mulAlpha(int c, float f) {
		if (f >= 1) return c;
		if (f <= 0) return c & 0x00FFFFFF;
		return (Math.round(a(c) * f) << 24) | (c & 0x00FFFFFF);
	}

	public static int withAlpha(int c, int alpha) {
		return (clamp(alpha) << 24) | (c & 0x00FFFFFF);
	}

	public static int brighten(int c, float amt) {
		return lerp(c, (c & 0xFF000000) | 0xFFFFFF, amt);
	}

	public static int darken(int c, float amt) {
		return lerp(c, c & 0xFF000000, amt);
	}

	/** h, s, v in 0..1 */
	public static int hsv(float h, float s, float v, int alpha) {
		h = h - (float) Math.floor(h);
		float r, g, b;
		int i = (int) (h * 6);
		float f = h * 6 - i;
		float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return argb(alpha, Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
	}

	/** returns {h, s, v} in 0..1 */
	public static float[] toHsv(int c) {
		float r = r(c) / 255f, g = g(c) / 255f, b = b(c) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h = 0;
		if (d > 1e-5f) {
			if (max == r) h = ((g - b) / d) % 6;
			else if (max == g) h = (b - r) / d + 2;
			else h = (r - g) / d + 4;
			h /= 6;
			if (h < 0) h += 1;
		}
		float s = max <= 0 ? 0 : d / max;
		return new float[]{h, s, max};
	}

	public static float luminance(int c) {
		return (0.2126f * r(c) + 0.7152f * g(c) + 0.0722f * b(c)) / 255f;
	}

	public static String hex(int c) {
		return String.format("#%08X", c);
	}
}
