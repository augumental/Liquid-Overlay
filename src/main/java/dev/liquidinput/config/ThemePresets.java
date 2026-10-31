package dev.liquidinput.config;

import java.util.List;

public final class ThemePresets {
	public record Preset(String name, int bgTop, int bgBottom, int pressTop, int pressBottom,
						 int text, int textPressed, int border, int glow) {
		public void applyTo(OverlayConfig.ThemeSettings t) {
			t.preset = name;
			t.bgTop = bgTop;
			t.bgBottom = bgBottom;
			t.pressTop = pressTop;
			t.pressBottom = pressBottom;
			t.text = text;
			t.textPressed = textPressed;
			t.border = border;
			t.glow = glow;
			t.rainbowPressed = false;
		}
	}

	public static final List<Preset> ALL = List.of(
			new Preset("Liquid Glass", 0x66FFFFFF, 0x33B4C8FF, 0xF2FFFFFF, 0xE6A8C8FF, 0xFFFFFFFF, 0xFF1A2233, 0x80FFFFFF, 0xFF9EC9FF),
			new Preset("Aurora", 0x5539D5A0, 0x556A4CFF, 0xFF7CF5C8, 0xFF8B6CFF, 0xFFFFFFFF, 0xFF0E1420, 0x88B8FFE6, 0xFF7CF5C8),
			new Preset("Sunset", 0x55FF9A6B, 0x55C2457A, 0xFFFFC27A, 0xFFFF5E8A, 0xFFFFFFFF, 0xFF2A0F18, 0x88FFD0B0, 0xFFFF8A6B),
			new Preset("Ocean", 0x553AA0FF, 0x55124A8C, 0xFF6FE3FF, 0xFF2F7BFF, 0xFFFFFFFF, 0xFF06182E, 0x88A8DCFF, 0xFF4FC3FF),
			new Preset("Midnight", 0xAA1A1C28, 0xAA0A0B10, 0xFF5B6CFF, 0xFF8A5BFF, 0xFFE8EAF6, 0xFFFFFFFF, 0x55606A99, 0xFF7A6CFF),
			new Preset("Mint", 0x55C8FFE4, 0x5578D6B0, 0xFFB8FFDD, 0xFF4FD69C, 0xFFFFFFFF, 0xFF0B2A1E, 0x88DFFFF0, 0xFF6FFFC0),
			new Preset("Rose", 0x55FFD1E8, 0x55FF8DC0, 0xFFFFE3F0, 0xFFFF6FAE, 0xFFFFFFFF, 0xFF3A0B22, 0x88FFE0F0, 0xFFFF8DC0),
			new Preset("Cyber", 0x880A0F1A, 0x88120A1F, 0xFF00FFC8, 0xFFFF00E6, 0xFF9FFFF0, 0xFF000000, 0x8800FFC8, 0xFF00FFC8),
			new Preset("Ember", 0x66301010, 0x66150808, 0xFFFFB347, 0xFFFF4E2B, 0xFFFFE2C6, 0xFF2B0A00, 0x66FF8A50, 0xFFFF6A2B),
			new Preset("Gold", 0x664A3A12, 0x66221A08, 0xFFFFF1B0, 0xFFE0A93A, 0xFFFFF4D6, 0xFF2A1E00, 0x88FFE08A, 0xFFFFD36B),
			new Preset("Mono", 0x66FFFFFF, 0x40FFFFFF, 0xFFFFFFFF, 0xFFDADADA, 0xFFFFFFFF, 0xFF000000, 0x99FFFFFF, 0xFFFFFFFF),
			new Preset("Lavender", 0x55E2D6FF, 0x559A86E8, 0xFFF2ECFF, 0xFFB49CFF, 0xFFFFFFFF, 0xFF1E1438, 0x88EEE6FF, 0xFFC4B0FF)
	);

	private ThemePresets() {}
}
