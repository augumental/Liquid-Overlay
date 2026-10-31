package dev.liquidinput.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import dev.liquidinput.config.OverlayConfig;

/**
 * Keyboard geometry in "units" (1u = one standard key). GLFW key codes are positional (named after
 * the US layout), so a layout only changes the printed label: on AZERTY the key at the US "Q"
 * position is GLFW_KEY_Q but prints "A".
 */
public final class KeyboardLayouts {
	public record KeyDef(String id, int key, float x, float y, float w, float h, String label) {
		public KeyDef withLabel(String l) {
			return new KeyDef(id, key, x, y, w, h, l);
		}

		public KeyDef withKey(int k) {
			return new KeyDef(id, k, x, y, w, h, label);
		}
	}

	public record Layout(List<KeyDef> keys, float width, float height) {}

	private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

	private static final Map<Integer, String> BASE = new HashMap<>();
	private static final Map<Integer, String> AZERTY = new HashMap<>();
	private static final Map<Integer, String> QWERTZ = new HashMap<>();

	static {
		String digits = "1234567890";
		for (int i = 0; i < 10; i++) BASE.put(GLFW.GLFW_KEY_0 + ((i + 1) % 10), String.valueOf(digits.charAt(i)));
		for (char c = 'A'; c <= 'Z'; c++) BASE.put((int) c, String.valueOf(c));
		for (int i = 1; i <= 12; i++) BASE.put(GLFW.GLFW_KEY_F1 + i - 1, "F" + i);
		for (int i = 0; i <= 9; i++) BASE.put(GLFW.GLFW_KEY_KP_0 + i, String.valueOf(i));
		put(BASE, GLFW.GLFW_KEY_GRAVE_ACCENT, "`");
		put(BASE, GLFW.GLFW_KEY_MINUS, "-");
		put(BASE, GLFW.GLFW_KEY_EQUAL, "=");
		put(BASE, GLFW.GLFW_KEY_BACKSPACE, "Back");
		put(BASE, GLFW.GLFW_KEY_TAB, "Tab");
		put(BASE, GLFW.GLFW_KEY_LEFT_BRACKET, "[");
		put(BASE, GLFW.GLFW_KEY_RIGHT_BRACKET, "]");
		put(BASE, GLFW.GLFW_KEY_BACKSLASH, "\\");
		put(BASE, GLFW.GLFW_KEY_CAPS_LOCK, "Caps");
		put(BASE, GLFW.GLFW_KEY_SEMICOLON, ";");
		put(BASE, GLFW.GLFW_KEY_APOSTROPHE, "'");
		put(BASE, GLFW.GLFW_KEY_ENTER, "Enter");
		put(BASE, GLFW.GLFW_KEY_LEFT_SHIFT, "Shift");
		put(BASE, GLFW.GLFW_KEY_RIGHT_SHIFT, "Shift");
		put(BASE, GLFW.GLFW_KEY_COMMA, ",");
		put(BASE, GLFW.GLFW_KEY_PERIOD, ".");
		put(BASE, GLFW.GLFW_KEY_SLASH, "/");
		put(BASE, GLFW.GLFW_KEY_WORLD_2, "<");
		put(BASE, GLFW.GLFW_KEY_LEFT_CONTROL, MAC ? "Ctrl" : "Ctrl");
		put(BASE, GLFW.GLFW_KEY_RIGHT_CONTROL, "Ctrl");
		put(BASE, GLFW.GLFW_KEY_LEFT_SUPER, MAC ? "Cmd" : "Win");
		put(BASE, GLFW.GLFW_KEY_RIGHT_SUPER, MAC ? "Cmd" : "Win");
		put(BASE, GLFW.GLFW_KEY_LEFT_ALT, MAC ? "Opt" : "Alt");
		put(BASE, GLFW.GLFW_KEY_RIGHT_ALT, MAC ? "Opt" : "Alt");
		put(BASE, GLFW.GLFW_KEY_MENU, "Menu");
		put(BASE, GLFW.GLFW_KEY_SPACE, "");
		put(BASE, GLFW.GLFW_KEY_ESCAPE, "Esc");
		put(BASE, GLFW.GLFW_KEY_PRINT_SCREEN, "PrtSc");
		put(BASE, GLFW.GLFW_KEY_SCROLL_LOCK, "ScrLk");
		put(BASE, GLFW.GLFW_KEY_PAUSE, "Pause");
		put(BASE, GLFW.GLFW_KEY_INSERT, "Ins");
		put(BASE, GLFW.GLFW_KEY_DELETE, "Del");
		put(BASE, GLFW.GLFW_KEY_HOME, "Home");
		put(BASE, GLFW.GLFW_KEY_END, "End");
		put(BASE, GLFW.GLFW_KEY_PAGE_UP, "PgUp");
		put(BASE, GLFW.GLFW_KEY_PAGE_DOWN, "PgDn");
		put(BASE, GLFW.GLFW_KEY_UP, "↑");
		put(BASE, GLFW.GLFW_KEY_DOWN, "↓");
		put(BASE, GLFW.GLFW_KEY_LEFT, "←");
		put(BASE, GLFW.GLFW_KEY_RIGHT, "→");
		put(BASE, GLFW.GLFW_KEY_NUM_LOCK, "Num");
		put(BASE, GLFW.GLFW_KEY_KP_DIVIDE, "/");
		put(BASE, GLFW.GLFW_KEY_KP_MULTIPLY, "*");
		put(BASE, GLFW.GLFW_KEY_KP_SUBTRACT, "-");
		put(BASE, GLFW.GLFW_KEY_KP_ADD, "+");
		put(BASE, GLFW.GLFW_KEY_KP_ENTER, "Ent");
		put(BASE, GLFW.GLFW_KEY_KP_DECIMAL, ".");

		// AZERTY (French) labels by US position
		put(AZERTY, GLFW.GLFW_KEY_GRAVE_ACCENT, "²");
		String az = "&é\"'(-è_çà";
		for (int i = 0; i < 10; i++) put(AZERTY, GLFW.GLFW_KEY_0 + ((i + 1) % 10), String.valueOf(az.charAt(i)));
		put(AZERTY, GLFW.GLFW_KEY_MINUS, ")");
		put(AZERTY, GLFW.GLFW_KEY_EQUAL, "=");
		put(AZERTY, GLFW.GLFW_KEY_Q, "A");
		put(AZERTY, GLFW.GLFW_KEY_W, "Z");
		put(AZERTY, GLFW.GLFW_KEY_A, "Q");
		put(AZERTY, GLFW.GLFW_KEY_Z, "W");
		put(AZERTY, GLFW.GLFW_KEY_LEFT_BRACKET, "^");
		put(AZERTY, GLFW.GLFW_KEY_RIGHT_BRACKET, "$");
		put(AZERTY, GLFW.GLFW_KEY_SEMICOLON, "M");
		put(AZERTY, GLFW.GLFW_KEY_APOSTROPHE, "ù");
		put(AZERTY, GLFW.GLFW_KEY_BACKSLASH, "*");
		put(AZERTY, GLFW.GLFW_KEY_M, ",");
		put(AZERTY, GLFW.GLFW_KEY_COMMA, ";");
		put(AZERTY, GLFW.GLFW_KEY_PERIOD, ":");
		put(AZERTY, GLFW.GLFW_KEY_SLASH, "!");
		put(AZERTY, GLFW.GLFW_KEY_CAPS_LOCK, "Verr");
		put(AZERTY, GLFW.GLFW_KEY_ENTER, "Entr");
		put(AZERTY, GLFW.GLFW_KEY_LEFT_SHIFT, "Maj");
		put(AZERTY, GLFW.GLFW_KEY_RIGHT_SHIFT, "Maj");
		put(AZERTY, GLFW.GLFW_KEY_ESCAPE, "Échap");
		put(AZERTY, GLFW.GLFW_KEY_RIGHT_ALT, "AltGr");

		// QWERTZ (German) labels by US position
		put(QWERTZ, GLFW.GLFW_KEY_GRAVE_ACCENT, "^");
		put(QWERTZ, GLFW.GLFW_KEY_MINUS, "ß");
		put(QWERTZ, GLFW.GLFW_KEY_EQUAL, "´");
		put(QWERTZ, GLFW.GLFW_KEY_Y, "Z");
		put(QWERTZ, GLFW.GLFW_KEY_Z, "Y");
		put(QWERTZ, GLFW.GLFW_KEY_LEFT_BRACKET, "Ü");
		put(QWERTZ, GLFW.GLFW_KEY_RIGHT_BRACKET, "+");
		put(QWERTZ, GLFW.GLFW_KEY_SEMICOLON, "Ö");
		put(QWERTZ, GLFW.GLFW_KEY_APOSTROPHE, "Ä");
		put(QWERTZ, GLFW.GLFW_KEY_BACKSLASH, "#");
		put(QWERTZ, GLFW.GLFW_KEY_SLASH, "-");
		put(QWERTZ, GLFW.GLFW_KEY_RIGHT_ALT, "AltGr");
		put(QWERTZ, GLFW.GLFW_KEY_CAPS_LOCK, "Caps");
		put(QWERTZ, GLFW.GLFW_KEY_LEFT_CONTROL, "Strg");
		put(QWERTZ, GLFW.GLFW_KEY_RIGHT_CONTROL, "Strg");
	}

	private static void put(Map<Integer, String> m, int k, String v) {
		m.put(k, v);
	}

	private KeyboardLayouts() {}

	/** Printed label for a GLFW key in the chosen language. */
	public static String label(int key, OverlayConfig.Lang lang) {
		if (lang == OverlayConfig.Lang.AUTO) {
			try {
				String n = GLFW.glfwGetKeyName(key, 0);
				if (n != null && !n.isEmpty()) return n.toUpperCase(Locale.ROOT);
			} catch (Throwable ignored) {
			}
		} else if (lang == OverlayConfig.Lang.AZERTY && AZERTY.containsKey(key)) {
			return AZERTY.get(key);
		} else if (lang == OverlayConfig.Lang.QWERTZ && QWERTZ.containsKey(key)) {
			return QWERTZ.get(key);
		}
		String b = BASE.get(key);
		if (b != null) return b;
		try {
			String n = GLFW.glfwGetKeyName(key, 0);
			if (n != null && !n.isEmpty()) return n.toUpperCase(Locale.ROOT);
		} catch (Throwable ignored) {
		}
		return key < 0 ? "-" : "#" + key;
	}

	/** Full readable key name for settings ("Left Ctrl", "F5"...). */
	public static String keyName(int key) {
		if (key < 0) return "None";
		return switch (key) {
			case GLFW.GLFW_KEY_LEFT_CONTROL -> "Left Ctrl";
			case GLFW.GLFW_KEY_RIGHT_CONTROL -> "Right Ctrl";
			case GLFW.GLFW_KEY_LEFT_SHIFT -> "Left Shift";
			case GLFW.GLFW_KEY_RIGHT_SHIFT -> "Right Shift";
			case GLFW.GLFW_KEY_LEFT_ALT -> "Left Alt";
			case GLFW.GLFW_KEY_RIGHT_ALT -> "Right Alt";
			case GLFW.GLFW_KEY_SPACE -> "Space";
			default -> label(key, OverlayConfig.Lang.AUTO);
		};
	}

	// ------------------------------------------------------------------ builders

	private static final class B {
		final List<KeyDef> keys = new ArrayList<>();
		final OverlayConfig.Lang lang;

		B(OverlayConfig.Lang lang) {
			this.lang = lang;
		}

		B k(int key, float x, float y, float w, float h) {
			keys.add(new KeyDef(Integer.toString(key), key, x, y, w, h, label(key, lang)));
			return this;
		}

		B k(int key, float x, float y) {
			return k(key, x, y, 1, 1);
		}

		B row(float x, float y, int... ks) {
			for (int i = 0; i < ks.length; i++) k(ks[i], x + i, y);
			return this;
		}
	}

	private static boolean iso(OverlayConfig.Lang lang) {
		return lang == OverlayConfig.Lang.AZERTY || lang == OverlayConfig.Lang.QWERTZ;
	}

	public static Layout build(OverlayConfig.KeyboardSettings s) {
		B b = new B(s.lang);
		switch (s.size) {
			case KEYSTROKES -> buildKeystrokes(b, s);
			case HALF -> buildHalf(b, s);
			case FULL -> buildFull(b, s);
		}
		// apply per-key remaps and hidden keys
		List<KeyDef> out = new ArrayList<>();
		float w = 0, h = 0;
		for (KeyDef d : b.keys) {
			Integer rk = s.remap.get(d.id());
			if (rk != null) d = d.withKey(rk).withLabel(label(rk, s.lang));
			if (s.hidden.contains(d.id())) continue;
			out.add(d);
			w = Math.max(w, d.x() + d.w());
			h = Math.max(h, d.y() + d.h());
		}
		return new Layout(out, w, h);
	}

	/** All slots including hidden ones (for the key editor). */
	public static Layout buildAll(OverlayConfig.KeyboardSettings s) {
		B b = new B(s.lang);
		switch (s.size) {
			case KEYSTROKES -> buildKeystrokes(b, s);
			case HALF -> buildHalf(b, s);
			case FULL -> buildFull(b, s);
		}
		List<KeyDef> out = new ArrayList<>();
		float w = 0, h = 0;
		for (KeyDef d : b.keys) {
			Integer rk = s.remap.get(d.id());
			if (rk != null) d = d.withKey(rk).withLabel(label(rk, s.lang));
			out.add(d);
			w = Math.max(w, d.x() + d.w());
			h = Math.max(h, d.y() + d.h());
		}
		return new Layout(out, w, h);
	}

	private static void buildKeystrokes(B b, OverlayConfig.KeyboardSettings s) {
		b.k(GLFW.GLFW_KEY_W, 1, 0);
		b.row(0, 1, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D);
		float y = 2;
		if (s.keystrokesShift) {
			b.k(GLFW.GLFW_KEY_LEFT_SHIFT, 0, y, 1.5f, 0.75f);
			b.k(GLFW.GLFW_KEY_LEFT_CONTROL, 1.5f, y, 1.5f, 0.75f);
			y += 0.75f;
		}
		if (s.keystrokesSpace) b.k(GLFW.GLFW_KEY_SPACE, 0, y, 3, 0.6f);
	}

	private static void buildHalf(B b, OverlayConfig.KeyboardSettings s) {
		float y = 0;
		if (s.showFRow) {
			b.k(GLFW.GLFW_KEY_ESCAPE, 0, 0);
			b.row(1.5f, 0, GLFW.GLFW_KEY_F1, GLFW.GLFW_KEY_F2, GLFW.GLFW_KEY_F3, GLFW.GLFW_KEY_F4);
			y = 1.25f;
		}
		b.row(0, y, GLFW.GLFW_KEY_GRAVE_ACCENT, GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3,
				GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6);
		b.k(GLFW.GLFW_KEY_TAB, 0, y + 1, 1.5f, 1);
		b.row(1.5f, y + 1, GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_T, GLFW.GLFW_KEY_Y);
		b.k(GLFW.GLFW_KEY_CAPS_LOCK, 0, y + 2, 1.75f, 1);
		b.row(1.75f, y + 2, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_H);
		if (iso(b.lang)) {
			b.k(GLFW.GLFW_KEY_LEFT_SHIFT, 0, y + 3, 1.25f, 1);
			b.k(GLFW.GLFW_KEY_WORLD_2, 1.25f, y + 3);
		} else {
			b.k(GLFW.GLFW_KEY_LEFT_SHIFT, 0, y + 3, 2.25f, 1);
		}
		b.row(2.25f, y + 3, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B);
		b.k(GLFW.GLFW_KEY_LEFT_CONTROL, 0, y + 4, 1.25f, 1);
		b.k(GLFW.GLFW_KEY_LEFT_SUPER, 1.25f, y + 4, 1.25f, 1);
		b.k(GLFW.GLFW_KEY_LEFT_ALT, 2.5f, y + 4, 1.25f, 1);
		b.k(GLFW.GLFW_KEY_SPACE, 3.75f, y + 4, 3.5f, 1);
	}

	private static void buildFull(B b, OverlayConfig.KeyboardSettings s) {
		float y = 0;
		boolean nav = s.showNav;
		if (s.showFRow) {
			b.k(GLFW.GLFW_KEY_ESCAPE, 0, 0);
			b.row(2, 0, GLFW.GLFW_KEY_F1, GLFW.GLFW_KEY_F2, GLFW.GLFW_KEY_F3, GLFW.GLFW_KEY_F4);
			b.row(6.5f, 0, GLFW.GLFW_KEY_F5, GLFW.GLFW_KEY_F6, GLFW.GLFW_KEY_F7, GLFW.GLFW_KEY_F8);
			b.row(11, 0, GLFW.GLFW_KEY_F9, GLFW.GLFW_KEY_F10, GLFW.GLFW_KEY_F11, GLFW.GLFW_KEY_F12);
			if (nav) b.row(15.25f, 0, GLFW.GLFW_KEY_PRINT_SCREEN, GLFW.GLFW_KEY_SCROLL_LOCK, GLFW.GLFW_KEY_PAUSE);
			y = 1.25f;
		}
		// number row
		b.row(0, y, GLFW.GLFW_KEY_GRAVE_ACCENT, GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4,
				GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_0,
				GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_EQUAL);
		b.k(GLFW.GLFW_KEY_BACKSPACE, 13, y, 2, 1);
		// top letter row
		b.k(GLFW.GLFW_KEY_TAB, 0, y + 1, 1.5f, 1);
		b.row(1.5f, y + 1, GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_T,
				GLFW.GLFW_KEY_Y, GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_P,
				GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET);
		boolean iso = iso(b.lang);
		if (iso) b.k(GLFW.GLFW_KEY_ENTER, 13.75f, y + 1, 1.25f, 2);
		else b.k(GLFW.GLFW_KEY_BACKSLASH, 13.5f, y + 1, 1.5f, 1);
		// home row
		b.k(GLFW.GLFW_KEY_CAPS_LOCK, 0, y + 2, 1.75f, 1);
		b.row(1.75f, y + 2, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_G,
				GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_L, GLFW.GLFW_KEY_SEMICOLON,
				GLFW.GLFW_KEY_APOSTROPHE);
		if (iso) b.k(GLFW.GLFW_KEY_BACKSLASH, 12.75f, y + 2);
		else b.k(GLFW.GLFW_KEY_ENTER, 12.75f, y + 2, 2.25f, 1);
		// bottom letter row
		if (iso) {
			b.k(GLFW.GLFW_KEY_LEFT_SHIFT, 0, y + 3, 1.25f, 1);
			b.k(GLFW.GLFW_KEY_WORLD_2, 1.25f, y + 3);
		} else {
			b.k(GLFW.GLFW_KEY_LEFT_SHIFT, 0, y + 3, 2.25f, 1);
		}
		b.row(2.25f, y + 3, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B,
				GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_SLASH);
		b.k(GLFW.GLFW_KEY_RIGHT_SHIFT, 12.25f, y + 3, 2.75f, 1);
		// space row
		b.k(GLFW.GLFW_KEY_LEFT_CONTROL, 0, y + 4, 1.25f, 1);
		b.k(GLFW.GLFW_KEY_LEFT_SUPER, 1.25f, y + 4, 1.25f, 1);
		b.k(GLFW.GLFW_KEY_LEFT_ALT, 2.5f, y + 4, 1.25f, 1);
		b.k(GLFW.GLFW_KEY_SPACE, 3.75f, y + 4, 6.25f, 1);
		b.k(GLFW.GLFW_KEY_RIGHT_ALT, 10, y + 4, 1.25f, 1);
		b.k(GLFW.GLFW_KEY_RIGHT_SUPER, 11.25f, y + 4, 1.25f, 1);
		b.k(GLFW.GLFW_KEY_MENU, 12.5f, y + 4, 1.25f, 1);
		b.k(GLFW.GLFW_KEY_RIGHT_CONTROL, 13.75f, y + 4, 1.25f, 1);

		float nx = 15.25f;
		if (nav) {
			b.row(nx, y, GLFW.GLFW_KEY_INSERT, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_PAGE_UP);
			b.row(nx, y + 1, GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_END, GLFW.GLFW_KEY_PAGE_DOWN);
		}
		// arrows are always part of the full board
		b.k(GLFW.GLFW_KEY_UP, nx + 1, y + 3);
		b.row(nx, y + 4, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT);

		if (s.showNumpad) {
			float px = nx + 3.25f;
			b.row(px, y, GLFW.GLFW_KEY_NUM_LOCK, GLFW.GLFW_KEY_KP_DIVIDE, GLFW.GLFW_KEY_KP_MULTIPLY, GLFW.GLFW_KEY_KP_SUBTRACT);
			b.row(px, y + 1, GLFW.GLFW_KEY_KP_7, GLFW.GLFW_KEY_KP_8, GLFW.GLFW_KEY_KP_9);
			b.k(GLFW.GLFW_KEY_KP_ADD, px + 3, y + 1, 1, 2);
			b.row(px, y + 2, GLFW.GLFW_KEY_KP_4, GLFW.GLFW_KEY_KP_5, GLFW.GLFW_KEY_KP_6);
			b.row(px, y + 3, GLFW.GLFW_KEY_KP_1, GLFW.GLFW_KEY_KP_2, GLFW.GLFW_KEY_KP_3);
			b.k(GLFW.GLFW_KEY_KP_ENTER, px + 3, y + 3, 1, 2);
			b.k(GLFW.GLFW_KEY_KP_0, px, y + 4, 2, 1);
			b.k(GLFW.GLFW_KEY_KP_DECIMAL, px + 2, y + 4);
		}
	}
}
