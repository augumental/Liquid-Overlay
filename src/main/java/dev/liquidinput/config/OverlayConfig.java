package dev.liquidinput.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything the player can customise. Serialized to config/liquidinput.json with Gson. */
public class OverlayConfig {
	public int version = 1;

	// ---------------------------------------------------------------- general
	public boolean enabled = true;
	public boolean introSeen = false;
	public boolean introEveryOpen = false;
	/** GLFW key that opens the GUI. Default: Left Ctrl. */
	public int openKey = 341;
	/** Only a quick tap (with no other key held) opens the GUI, so holding Ctrl to sprint still works. */
	public boolean openKeyTap = true;
	/** Optional hotkey to toggle the overlay on/off. -1 = unbound. */
	public int toggleKey = -1;
	public boolean showInScreens = false;
	public boolean showWithChat = true;
	public boolean hideInF1 = true;
	public boolean injectedGuard = true;
	/** Rasterise the HUD at full framebuffer resolution (smoothest). Off = lighter on FPS. */
	public boolean hudHighQuality = true;

	// GUI feel
	public boolean guiSounds = true;
	public boolean hoverSounds = true;
	public float guiVolume = 0.8f;
	public float haptics = 0.6f;
	public boolean guiBlur = true;

	public KeyboardSettings keyboard = new KeyboardSettings();
	public MouseSettings mouse = new MouseSettings();
	public StatsWidget statsWidget = new StatsWidget();
	public ThemeSettings theme = new ThemeSettings();
	public AnimSettings anim = new AnimSettings();
	public SoundSettings sound = new SoundSettings();
	public StatsData stats = new StatsData();

	/** Saved setups (JSON of this whole config minus stats/slots). */
	public List<String> presetSlots = new ArrayList<>(List.of("", "", ""));

	// ---------------------------------------------------------------- nested

	public static class Widget {
		public boolean enabled = true;
		/** 0..1 across the free space of the screen (so it stays on-screen at any resolution). */
		public float x, y;
		public float scale = 1.0f;
		public float opacity = 1.0f;
	}

	public enum Lang { QWERTY, AZERTY, QWERTZ, AUTO }

	public enum Size { KEYSTROKES, HALF, FULL }

	public static class KeyboardSettings extends Widget {
		public Lang lang = Lang.QWERTY;
		public Size size = Size.KEYSTROKES;
		public boolean showFRow = true;
		public boolean showNav = true;
		public boolean showNumpad = false;
		public boolean keystrokesShift = true;
		public boolean keystrokesSpace = true;
		public boolean keyIndicators = true;
		public boolean showLabels = true;
		public boolean showCounters = false;
		public boolean labelShadow = false;
		public boolean lockLeds = true;
		public int keyUnit = 24;
		public int gap = 3;
		public int radius = 6;
		/** Keys the player hid in the editor (by layout slot id). */
		public Set<String> hidden = new HashSet<>();
		/** Slot id -> GLFW key it should light up for (rebinding). */
		public Map<String, Integer> remap = new HashMap<>();

		public KeyboardSettings() {
			x = 0.02f;
			y = 0.70f;
		}
	}

	public enum MouseStyle { SHAPE, CLASSIC }

	public enum CpsPos { INSIDE, BELOW, OFF }

	public static class MouseSettings extends Widget {
		public MouseStyle style = MouseStyle.SHAPE;
		public boolean showMiddle = true;
		public boolean showSide = true;
		public boolean showScroll = true;
		public CpsPos cps = CpsPos.INSIDE;
		public boolean showMovement = false;
		public float movementSensitivity = 1.0f;
		public boolean showCounters = false;

		public MouseSettings() {
			x = 0.22f;
			y = 0.70f;
		}
	}

	public static class StatsWidget extends Widget {
		public boolean showCps = true;
		public boolean showKps = true;
		public boolean showTotals = false;

		public StatsWidget() {
			enabled = false;
			x = 0.02f;
			y = 0.55f;
		}
	}

	public enum GradientMode { VERTICAL, HORIZONTAL, DIAGONAL, BOARD }

	public static class ThemeSettings {
		public String preset = "Liquid Glass";
		public int bgTop = 0x66FFFFFF;
		public int bgBottom = 0x33B4C8FF;
		public int pressTop = 0xF2FFFFFF;
		public int pressBottom = 0xE6A8C8FF;
		public int text = 0xFFFFFFFF;
		public int textPressed = 0xFF1A2233;
		public int border = 0x80FFFFFF;
		public int glow = 0xFF9EC9FF;
		public GradientMode gradient = GradientMode.VERTICAL;
		public float glassOpacity = 0.85f;
		public boolean highlight = true;
		public boolean shadow = true;
		public boolean backplate = false;
		public boolean rainbowPressed = false;
		public float rainbowSpeed = 1.0f;
		/** Tint the settings GUI with the overlay's colours. */
		public boolean tintGui = true;
	}

	public enum PressAnim { NONE, FADE, POP, RIPPLE, SINK }

	public enum IdleAnim { NONE, BREATHE, RAINBOW, SHIMMER }

	public static class AnimSettings {
		public PressAnim press = PressAnim.POP;
		public IdleAnim idle = IdleAnim.SHIMMER;
		public int fadeMs = 180;
		public float speed = 1.0f;
		public float bounce = 0.5f;
		public boolean glow = true;
		public boolean cascadeIn = true;
	}

	public enum KeyPack { THOCK, CLICKY, CREAMY, TYPEWRITER, BUBBLE, VANILLA, CUSTOM }

	public enum MousePack { CRISP, SOFT, VANILLA, CUSTOM }

	public static class SoundSettings {
		public boolean keySounds = true;
		public KeyPack keyPack = KeyPack.THOCK;
		public float keyVolume = 0.45f;
		public boolean releaseSounds = true;
		public boolean mouseSounds = true;
		public MousePack mousePack = MousePack.CRISP;
		public float mouseVolume = 0.5f;
		public float pitchVariation = 0.06f;
		public boolean muteInChat = true;
	}

	public static class StatsData {
		public long totalKeys;
		public long totalLeft;
		public long totalRight;
		public long totalOther;
		public int peakLeftCps;
		public int peakRightCps;
		public long playMillis;
		public int sessions;
	}
}
