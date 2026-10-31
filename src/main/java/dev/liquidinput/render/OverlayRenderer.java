package dev.liquidinput.render;

import java.util.Set;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import dev.liquidinput.config.ConfigManager;
import dev.liquidinput.config.OverlayConfig;
import dev.liquidinput.input.PhysicalInput;
import dev.liquidinput.layout.KeyboardLayouts;
import dev.liquidinput.layout.KeyboardLayouts.KeyDef;
import dev.liquidinput.layout.KeyboardLayouts.Layout;

/** Draws the keyboard, mouse and stats widgets (on the HUD and as live previews in the GUI). */
public final class OverlayRenderer {
	private OverlayRenderer() {}

	// ------------------------------------------------------------------ input sources

	/** Where pressed-state comes from: the real physical tracker, or a scripted demo for the intro. */
	public interface Source {
		boolean keyDown(int key);

		long keyPressAt(int key);

		long keyReleaseAt(int key);

		boolean mouseDown(int b);

		long mousePressAt(int b);

		long mouseReleaseAt(int b);

		int cps(int b);

		long scrollUpAt();

		long scrollDownAt();

		double moveX();

		double moveY();
	}

	public static final Source PHYSICAL = new Source() {
		public boolean keyDown(int key) { return PhysicalInput.isKeyDown(key); }
		public long keyPressAt(int key) { return PhysicalInput.keyPressedAt(key); }
		public long keyReleaseAt(int key) { return PhysicalInput.keyReleasedAt(key); }
		public boolean mouseDown(int b) { return PhysicalInput.isMouseDown(b); }
		public long mousePressAt(int b) { return PhysicalInput.mousePressedAt(b); }
		public long mouseReleaseAt(int b) { return PhysicalInput.mouseReleasedAt(b); }
		public int cps(int b) { return PhysicalInput.cps(b); }
		public long scrollUpAt() { return PhysicalInput.lastScrollUp(); }
		public long scrollDownAt() { return PhysicalInput.lastScrollDown(); }
		public double moveX() { return PhysicalInput.moveX(); }
		public double moveY() { return PhysicalInput.moveY(); }
	};

	/** Editor overlay info for the key editor in the GUI. */
	public record EditView(Set<String> hidden, String selected, String hovered, int accent) {}

	private static long appearStart = 0;
	private static boolean wasVisible;

	public static void restartAppear() {
		appearStart = System.currentTimeMillis();
	}

	private static OverlayConfig cfg() {
		return ConfigManager.get();
	}

	// ------------------------------------------------------------------ HUD entry

	public static void renderHud(GuiGraphics g) {
		OverlayConfig c = cfg();
		Minecraft mc = Minecraft.getInstance();
		boolean visible = c.enabled && !(c.hideInF1 && mc.options.hideGui);
		if (visible && !wasVisible) restartAppear();
		wasVisible = visible;
		if (!visible) return;

		int sw = g.guiWidth(), sh = g.guiHeight();
		Painter p = Painter.begin(g, c.hudHighQuality);
		try {
			if (c.keyboard.enabled) {
				float[] s = keyboardSize(c.keyboard);
				drawKeyboard(p, c.keyboard.x * (sw - s[0]), c.keyboard.y * (sh - s[1]), 1f, PHYSICAL, null, true);
			}
			if (c.mouse.enabled) {
				float[] s = mouseSize(c.mouse);
				drawMouse(p, c.mouse.x * (sw - s[0]), c.mouse.y * (sh - s[1]), 1f, PHYSICAL, true);
			}
			if (c.statsWidget.enabled) {
				float[] s = statsSize(c.statsWidget);
				drawStats(p, c.statsWidget.x * (sw - s[0]), c.statsWidget.y * (sh - s[1]), 1f, PHYSICAL);
			}
		} finally {
			p.end();
		}
	}

	// ------------------------------------------------------------------ sizes (GUI px)

	private static Layout cachedLayout;
	private static int cachedHash;

	public static Layout layout(OverlayConfig.KeyboardSettings k) {
		int h = java.util.Objects.hash(k.lang, k.size, k.showFRow, k.showNav, k.showNumpad, k.keystrokesShift,
				k.keystrokesSpace, k.hidden, k.remap);
		if (cachedLayout == null || h != cachedHash) {
			cachedLayout = KeyboardLayouts.build(k);
			cachedHash = h;
		}
		return cachedLayout;
	}

	public static float unit(OverlayConfig.KeyboardSettings k) {
		return k.keyUnit * k.scale;
	}

	public static float[] keyboardSize(OverlayConfig.KeyboardSettings k) {
		Layout l = layout(k);
		float u = unit(k), gap = k.gap * k.scale;
		float pad = cfg().theme.backplate ? gap * 2.5f : 0;
		return new float[]{Math.max(u, l.width() * u - gap + pad * 2), Math.max(u, l.height() * u - gap + pad * 2)};
	}

	public static float mouseUnit(OverlayConfig.MouseSettings m) {
		return cfg().keyboard.keyUnit * m.scale;
	}

	public static float[] mouseSize(OverlayConfig.MouseSettings m) {
		float u = mouseUnit(m);
		if (m.style == OverlayConfig.MouseStyle.CLASSIC) {
			float h = u * 1.0f + (m.showMiddle || m.showScroll ? u * 0.55f : 0) + (m.cps == OverlayConfig.CpsPos.BELOW ? u * 0.5f : 0);
			return new float[]{u * 3f, h};
		}
		float side = m.showSide ? u * 0.32f : 0;
		float below = m.cps == OverlayConfig.CpsPos.BELOW ? u * 0.62f : 0;
		return new float[]{u * 2.4f + side, u * 3.4f + below};
	}

	public static float[] statsSize(OverlayConfig.StatsWidget s) {
		float u = cfg().keyboard.keyUnit * s.scale;
		int rows = (s.showCps ? 1 : 0) + (s.showKps ? 1 : 0) + (s.showTotals ? 2 : 0);
		return new float[]{u * 3.6f, u * 0.25f + Math.max(1, rows) * u * 0.48f + u * 0.1f};
	}

	// ------------------------------------------------------------------ animation helpers

	private static float pressAmount(boolean down, long pressAt, long releaseAt, long now) {
		OverlayConfig c = cfg();
		if (!c.keyboard.keyIndicators) return 0;
		float spd = Math.max(0.2f, c.anim.speed);
		if (down) {
			if (c.anim.press == OverlayConfig.PressAnim.NONE) return 1;
			return Ease.outCubic((now - pressAt) / (35f / spd));
		}
		if (releaseAt <= 0 || releaseAt < pressAt) return 0;
		float age = now - releaseAt;
		return switch (c.anim.press) {
			case NONE -> 0;
			case FADE -> 1 - Ease.outCubic(age / Math.max(1f, c.anim.fadeMs / spd));
			default -> 1 - Ease.outCubic(age / Math.max(1f, c.anim.fadeMs * 0.6f / spd));
		};
	}

	/** Pop / sink scale factor and vertical offset (fraction of height). */
	private static float[] pressTransform(boolean down, long pressAt, long releaseAt, long now, float press) {
		OverlayConfig.AnimSettings a = cfg().anim;
		float b = a.bounce, spd = Math.max(0.2f, a.speed);
		float s = 1, dy = 0;
		switch (a.press) {
			case POP -> {
				if (down) {
					s = 1 - 0.10f * b * Ease.outCubic((now - pressAt) / (60f / spd));
				} else if (releaseAt > pressAt) {
					float age = (now - releaseAt) * spd;
					float dip = 0.10f * b * Math.max(0, 1 - age / 70f);
					float over = age < 600 ? 0.045f * b * (float) (Math.exp(-age / 130f) * Math.sin(age / 38f)) : 0;
					s = 1 - dip + over;
				}
			}
			case SINK -> {
				s = 1 - 0.03f * press * b;
				dy = 0.08f * press * b;
			}
			default -> {
			}
		}
		return new float[]{s, dy};
	}

	private static float appear(float delayMs, long now, boolean enabled) {
		if (!enabled || !cfg().anim.cascadeIn || appearStart == 0) return 1;
		float t = (now - appearStart - delayMs) / 420f;
		return t >= 1 ? 1 : Math.max(0, t);
	}

	// ------------------------------------------------------------------ colours

	private static int[] bgColors(float nx, float ny, float now) {
		OverlayConfig.ThemeSettings t = cfg().theme;
		OverlayConfig.AnimSettings a = cfg().anim;
		int top = t.bgTop, bot = t.bgBottom;
		switch (t.gradient) {
			case HORIZONTAL -> {
				int col = Colors.lerp(t.bgTop, t.bgBottom, nx);
				top = Colors.brighten(col, 0.06f);
				bot = Colors.darken(col, 0.06f);
			}
			case DIAGONAL -> {
				int col = Colors.lerp(t.bgTop, t.bgBottom, (nx + ny) / 2f);
				top = Colors.brighten(col, 0.06f);
				bot = Colors.darken(col, 0.06f);
			}
			default -> {
			}
		}
		if (a.idle == OverlayConfig.IdleAnim.RAINBOW) {
			float hue = now / 1000f * 0.08f * a.speed + nx * 0.55f + ny * 0.15f;
			top = Colors.hsv(hue, 0.55f, 1f, Colors.a(t.bgTop));
			bot = Colors.hsv(hue + 0.07f, 0.65f, 0.85f, Colors.a(t.bgBottom));
		} else if (a.idle == OverlayConfig.IdleAnim.BREATHE) {
			float k = 0.78f + 0.22f * (float) Math.sin(now / 1000f * Math.PI * 2 / 3.2f * a.speed);
			top = Colors.mulAlpha(top, k);
			bot = Colors.mulAlpha(bot, k);
		}
		float o = t.glassOpacity;
		return new int[]{Colors.mulAlpha(top, o), Colors.mulAlpha(bot, o)};
	}

	private static int[] pressColors(float nx, float ny, float now) {
		OverlayConfig.ThemeSettings t = cfg().theme;
		if (t.rainbowPressed) {
			float hue = now / 1000f * 0.25f * t.rainbowSpeed + nx * 0.5f + ny * 0.2f;
			return new int[]{Colors.hsv(hue, 0.45f, 1f, 255), Colors.hsv(hue + 0.06f, 0.7f, 1f, 255)};
		}
		switch (t.gradient) {
			case HORIZONTAL -> {
				int col = Colors.lerp(t.pressTop, t.pressBottom, nx);
				return new int[]{Colors.brighten(col, 0.1f), col};
			}
			case DIAGONAL -> {
				int col = Colors.lerp(t.pressTop, t.pressBottom, (nx + ny) / 2f);
				return new int[]{Colors.brighten(col, 0.1f), col};
			}
			default -> {
				return new int[]{t.pressTop, t.pressBottom};
			}
		}
	}

	private static float shimmer(float nx, float ny, long now) {
		OverlayConfig.AnimSettings a = cfg().anim;
		if (a.idle != OverlayConfig.IdleAnim.SHIMMER) return 0;
		float period = 4200f / Math.max(0.2f, a.speed);
		float s = (now % (long) period) / period * 1.8f - 0.4f;
		float pos = nx * 0.75f + ny * 0.25f;
		float d = pos - s;
		return (float) Math.exp(-(d * d) / 0.006f);
	}

	// ------------------------------------------------------------------ one glass key

	/**
	 * @param boardBand for BOARD gradient mode: vertical fraction range of this key across the board
	 */
	public static void drawKey(Painter p, float x, float y, float w, float h, float r, String label, float press,
							   float ripple, float nx, float ny, float bandTop, float bandBot, int counter,
							   float scale, long now, int ledState) {
		OverlayConfig c = cfg();
		OverlayConfig.ThemeSettings t = c.theme;

		int[] bg = bgColors(nx, ny, now);
		int[] pc = pressColors(nx, ny, now);
		if (t.gradient == OverlayConfig.GradientMode.BOARD) {
			int a = Colors.lerp(t.bgTop, t.bgBottom, bandTop), b = Colors.lerp(t.bgTop, t.bgBottom, bandBot);
			if (c.anim.idle != OverlayConfig.IdleAnim.RAINBOW) {
				bg = new int[]{Colors.mulAlpha(a, t.glassOpacity), Colors.mulAlpha(b, t.glassOpacity)};
			}
			if (!t.rainbowPressed) {
				pc = new int[]{Colors.lerp(t.pressTop, t.pressBottom, bandTop), Colors.lerp(t.pressTop, t.pressBottom, bandBot)};
			}
		}

		float onePx = p.px();
		// shadow under the key
		if (t.shadow) {
			p.shadow(x, y + h * 0.06f, w, h, r, Math.max(1.5f, h * 0.12f) * (1 - 0.6f * press), 0x50000000, 4);
		}
		// glow when pressed
		if (press > 0.01f && c.anim.glow) {
			p.shadow(x, y, w, h, r, Math.max(2f, h * 0.22f), Colors.mulAlpha(t.glow, 0.9f * press), 6);
		}
		// glass body
		p.round(x, y, w, h, r, bg[0], bg[1]);
		// pressed fill
		if (press > 0.003f) {
			p.round(x, y, w, h, r, Colors.mulAlpha(pc[0], press), Colors.mulAlpha(pc[1], press));
		}
		// ripple
		if (ripple > 0 && ripple < 1) {
			float e = Ease.outCubic(ripple);
			float rw = w * e, rh = h * e;
			p.round(x + (w - rw) / 2, y + (h - rh) / 2, rw, rh, r * e, Colors.mulAlpha(t.glow, 0.55f * (1 - ripple)));
		}
		// specular highlight (liquid glass): bright top sheen + 1px top edge
		if (t.highlight) {
			p.round4(x + onePx, y + onePx, w - 2 * onePx, h * 0.48f, r, r, 0, 0,
					Colors.withAlpha(0xFFFFFF, Math.round(70 * (1 - press * 0.5f))), 0x00FFFFFF);
		}
		float sh = shimmer(nx, ny, now);
		if (sh > 0.01f) {
			p.round(x, y, w, h, r, Colors.withAlpha(0xFFFFFF, Math.round(110 * sh)), Colors.withAlpha(0xFFFFFF, Math.round(25 * sh)));
		}
		// border
		p.ring(x, y, w, h, r, Math.max(onePx, 0.5f * scale), Colors.lerp(t.border, Colors.withAlpha(t.glow, 0xCC), press * 0.6f));

		// label
		if (c.keyboard.showLabels && label != null) {
			int col = Colors.lerp(t.text, t.textPressed, press);
			if (label.isEmpty()) {
				float bw = w * 0.28f, bh = Math.max(onePx, h * 0.07f);
				p.round(x + (w - bw) / 2, y + h * 0.5f - bh / 2, bw, bh, bh / 2, Colors.mulAlpha(col, 0.7f));
			} else {
				float base = Math.min(h, 24f * scale) * (label.length() <= 2 ? 0.40f : 0.33f);
				float size = p.fit(label, base, w * 0.82f);
				float cy = y + h * (counter >= 0 ? 0.44f : 0.5f);
				p.textCentered(label, x + w / 2, cy, col, size, c.keyboard.labelShadow);
			}
			if (counter >= 0) {
				String n = counter > 9999 ? (counter / 1000) + "k" : Integer.toString(counter);
				float cs = Math.min(h, 24f * scale) * 0.24f;
				p.textCentered(n, x + w / 2, y + h * 0.78f, Colors.mulAlpha(col, 0.6f), cs);
			}
		}
		// lock LED
		if (ledState >= 0) {
			float d = Math.max(2f * scale, h * 0.09f);
			int on = ledState == 1 ? Colors.withAlpha(t.glow, 255) : 0x40FFFFFF;
			if (ledState == 1) p.shadow(x + w - d * 2.2f, y + d * 1.2f, d, d, d / 2, d, Colors.withAlpha(t.glow, 0x90));
			p.round(x + w - d * 2.2f, y + d * 1.2f, d, d, d / 2, on);
		}
	}

	// ------------------------------------------------------------------ keyboard

	public static void drawKeyboard(Painter p, float x, float y, float extraScale, Source src, EditView edit, boolean hud) {
		OverlayConfig c = cfg();
		OverlayConfig.KeyboardSettings k = c.keyboard;
		Layout l = edit != null ? KeyboardLayouts.buildAll(k) : layout(k);
		long now = System.currentTimeMillis();
		float sc = k.scale * extraScale;
		float u = k.keyUnit * sc, gap = k.gap * sc, r = k.radius * sc;

		p.push();
		p.alpha(k.opacity);
		float bw = l.width() * u - gap, bh = l.height() * u - gap;
		if (c.theme.backplate) {
			float pad = gap * 2.5f;
			drawPlate(p, x, y, bw + pad * 2, bh + pad * 2, r + pad * 0.6f, sc);
			x += pad;
			y += pad;
		}

		for (KeyDef d : l.keys()) {
			float kx = x + d.x() * u, ky = y + d.y() * u, kw = d.w() * u - gap, kh = d.h() * u - gap;
			float nx = (d.x() + d.w() / 2) / Math.max(1, l.width());
			float ny = (d.y() + d.h() / 2) / Math.max(1, l.height());
			float bandTop = d.y() / Math.max(1, l.height()), bandBot = (d.y() + d.h()) / Math.max(1, l.height());

			boolean down = src.keyDown(d.key());
			long pa = src.keyPressAt(d.key()), ra = src.keyReleaseAt(d.key());
			float press = pressAmount(down, pa, ra, now);
			float[] tf = c.keyboard.keyIndicators ? pressTransform(down, pa, ra, now, press) : new float[]{1, 0};
			float ripple = c.anim.press == OverlayConfig.PressAnim.RIPPLE && c.keyboard.keyIndicators && pa > 0
					? (now - pa) / (480f / Math.max(0.2f, c.anim.speed)) : -1;
			float ap = appear((d.x() + d.y() * 0.6f) * 26f, now, hud);
			if (ap <= 0) continue;

			int led = -1;
			if (k.lockLeds) {
				if (d.key() == GLFW.GLFW_KEY_CAPS_LOCK) led = PhysicalInput.capsLock() ? 1 : 0;
				else if (d.key() == GLFW.GLFW_KEY_NUM_LOCK) led = PhysicalInput.numLock() ? 1 : 0;
			}

			p.push();
			float s = tf[0] * (ap < 1 ? 0.55f + 0.45f * Ease.outBack(ap) : 1);
			p.scaleAround(kx + kw / 2, ky + kh / 2 + tf[1] * kh, s);
			p.translate(0, tf[1] * kh);
			if (ap < 1) p.alpha(Math.min(1, ap * 1.6f));
			boolean hidden = edit != null && edit.hidden().contains(d.id());
			if (hidden) p.alpha(0.22f);

			drawKey(p, kx, ky, kw, kh, Math.min(r, Math.min(kw, kh) / 2), d.label(), press, ripple, nx, ny,
					bandTop, bandBot, k.showCounters ? PhysicalInput.keyPressCount(d.key()) : -1, sc, now, led);

			if (edit != null) {
				if (d.id().equals(edit.selected())) {
					p.ring(kx - 1.5f * sc, ky - 1.5f * sc, kw + 3 * sc, kh + 3 * sc, r + 1.5f * sc, 1.2f * sc, edit.accent());
				} else if (d.id().equals(edit.hovered())) {
					p.ring(kx - 1 * sc, ky - 1 * sc, kw + 2 * sc, kh + 2 * sc, r + 1, 0.8f * sc, 0xAAFFFFFF);
				}
				if (hidden) {
					p.rect(kx + kw * 0.2f, ky + kh / 2, kw * 0.6f, Math.max(p.px(), 0.8f * sc), 0xFFFF6B6B);
				}
			}
			p.pop();
		}
		p.pop();
	}

	/** Glass backplate used behind widgets. */
	public static void drawPlate(Painter p, float x, float y, float w, float h, float r, float sc) {
		OverlayConfig.ThemeSettings t = cfg().theme;
		if (t.shadow) p.shadow(x, y + 1.5f * sc, w, h, r, 6 * sc, 0x60000000);
		p.round(x, y, w, h, r, Colors.mulAlpha(Colors.lerp(t.bgTop, 0x30FFFFFF, 0.4f), 0.7f * t.glassOpacity),
				Colors.mulAlpha(Colors.lerp(t.bgBottom, 0x10000000, 0.4f), 0.7f * t.glassOpacity));
		if (t.highlight) p.round4(x, y, w, h * 0.4f, r, r, 0, 0, 0x22FFFFFF, 0x00FFFFFF);
		p.ring(x, y, w, h, r, Math.max(p.px(), 0.5f * sc), Colors.mulAlpha(t.border, 0.8f));
	}

	/** Which key (if any) is at gx, gy for a keyboard drawn at x, y (used by the GUI key editor). */
	public static KeyDef keyAt(float x, float y, float extraScale, float gx, float gy) {
		OverlayConfig.KeyboardSettings k = cfg().keyboard;
		Layout l = KeyboardLayouts.buildAll(k);
		float sc = k.scale * extraScale;
		float u = k.keyUnit * sc, gap = k.gap * sc;
		if (cfg().theme.backplate) {
			x += gap * 2.5f;
			y += gap * 2.5f;
		}
		for (KeyDef d : l.keys()) {
			float kx = x + d.x() * u, ky = y + d.y() * u, kw = d.w() * u - gap, kh = d.h() * u - gap;
			if (gx >= kx && gx < kx + kw && gy >= ky && gy < ky + kh) return d;
		}
		return null;
	}

	public static float[] keyboardSizeAll(float extraScale) {
		OverlayConfig.KeyboardSettings k = cfg().keyboard;
		Layout l = KeyboardLayouts.buildAll(k);
		float sc = k.scale * extraScale;
		float u = k.keyUnit * sc, gap = k.gap * sc;
		float pad = cfg().theme.backplate ? gap * 5f : 0;
		return new float[]{l.width() * u - gap + pad, l.height() * u - gap + pad};
	}

	// ------------------------------------------------------------------ mouse

	private static float mousePress(Source src, int b, long now) {
		return pressAmount(src.mouseDown(b), src.mousePressAt(b), src.mouseReleaseAt(b), now);
	}

	public static void drawMouse(Painter p, float x, float y, float extraScale, Source src, boolean hud) {
		OverlayConfig c = cfg();
		OverlayConfig.MouseSettings m = c.mouse;
		OverlayConfig.ThemeSettings t = c.theme;
		long now = System.currentTimeMillis();
		float sc = m.scale * extraScale;
		float u = c.keyboard.keyUnit * sc;
		float gap = c.keyboard.gap * sc;
		float ap = appear(120, now, hud);
		if (ap <= 0) return;

		p.push();
		p.alpha(m.opacity * Math.min(1, ap * 1.6f));
		if (ap < 1) {
			float[] sz = mouseSize(m);
			p.scaleAround(x + sz[0] * extraScale / 2, y + sz[1] * extraScale / 2, 0.6f + 0.4f * Ease.outBack(ap));
		}

		if (m.style == OverlayConfig.MouseStyle.CLASSIC) {
			drawMouseClassic(p, x, y, u, gap, sc, src, now);
			p.pop();
			return;
		}

		float side = m.showSide ? u * 0.32f : 0;
		float mx = x + side, W = u * 2.4f, H = u * 3.4f, bh = u * 1.45f;
		float bigR = W * 0.46f, small = Math.max(2 * sc, c.keyboard.radius * sc * 0.5f);
		float half = (W - gap) / 2;

		// whole-mouse soft shadow
		if (t.shadow) p.shadow(mx, y + 2 * sc, W, H, bigR, 7 * sc, 0x55000000);

		float pl = mousePress(src, PhysicalInput.MB_LEFT, now);
		float pr = mousePress(src, PhysicalInput.MB_RIGHT, now);
		float pm = mousePress(src, PhysicalInput.MB_MIDDLE, now);
		float[] tl = pressTransform(src.mouseDown(0), src.mousePressAt(0), src.mouseReleaseAt(0), now, pl);
		float[] tr = pressTransform(src.mouseDown(1), src.mousePressAt(1), src.mouseReleaseAt(1), now, pr);

		// buttons
		mouseButton(p, mx, y, half, bh, bigR, small, small, small, "LMB", pl, tl, 0.25f, src.cps(0),
				PhysicalInput.mouseClickCount(0), sc, now, src, 0);
		mouseButton(p, mx + half + gap, y, half, bh, small, bigR, small, small, "RMB", pr, tr, 0.75f, src.cps(1),
				PhysicalInput.mouseClickCount(1), sc, now, src, 1);

		// body
		float by = y + bh + gap, bodyH = H - bh - gap;
		int[] bg = bgColors(0.5f, 0.7f, now);
		p.round4(mx, by, W, bodyH, small, small, bigR, bigR, bg[0], bg[1]);
		if (t.highlight) p.round4(mx, by, W, bodyH * 0.35f, small, small, 0, 0, 0x26FFFFFF, 0x00FFFFFF);
		float sh = shimmer(0.5f, 0.8f, now);
		if (sh > 0.01f) p.round4(mx, by, W, bodyH, small, small, bigR, bigR, Colors.withAlpha(0xFFFFFF, Math.round(90 * sh)), 0x00FFFFFF);
		p.ring4(mx, by, W, bodyH, small, small, bigR, bigR, Math.max(p.px(), 0.5f * sc), t.border);

		// wheel
		if (m.showMiddle || m.showScroll) {
			float ww = u * 0.34f, wh = u * 0.8f;
			float wx = mx + W / 2 - ww / 2, wy = y + u * 0.32f;
			int[] pc = pressColors(0.5f, 0.2f, now);
			p.round(wx, wy, ww, wh, ww / 2, 0xC0101018, 0xC0181820);
			if (pm > 0.01f) p.round(wx, wy, ww, wh, ww / 2, Colors.mulAlpha(pc[0], pm), Colors.mulAlpha(pc[1], pm));
			p.ring(wx, wy, ww, wh, ww / 2, Math.max(p.px(), 0.5f * sc), t.border);
			// wheel ridges
			for (int i = 0; i < 3; i++) {
				p.rect(wx + ww * 0.28f, wy + wh * (0.3f + i * 0.2f), ww * 0.44f, Math.max(p.px(), 0.6f * sc), 0x40FFFFFF);
			}
			if (m.showScroll) {
				float up = 1 - Ease.outCubic((now - src.scrollUpAt()) / 260f);
				float dn = 1 - Ease.outCubic((now - src.scrollDownAt()) / 260f);
				if (up > 0) arrow(p, mx + W / 2, wy - u * 0.12f, u * 0.13f, true, Colors.mulAlpha(t.glow, up));
				if (dn > 0) arrow(p, mx + W / 2, wy + wh + u * 0.12f, u * 0.13f, false, Colors.mulAlpha(t.glow, dn));
			}
		}

		// side buttons
		if (m.showSide) {
			float sw = u * 0.3f, shh = u * 0.42f, sx = x + side * 0.15f;
			for (int i = 0; i < 2; i++) {
				int b = i == 0 ? PhysicalInput.MB_FORWARD : PhysicalInput.MB_BACK;
				float sp = mousePress(src, b, now);
				float sy = by + u * 0.25f + i * (shh + gap);
				int[] sbg = bgColors(0.0f, 0.6f, now);
				int[] pc = pressColors(0, 0.6f, now);
				p.round(sx, sy, sw, shh, sw * 0.45f, sbg[0], sbg[1]);
				if (sp > 0.01f) {
					if (c.anim.glow) p.shadow(sx, sy, sw, shh, sw * 0.45f, 2 * sc, Colors.mulAlpha(t.glow, sp));
					p.round(sx, sy, sw, shh, sw * 0.45f, Colors.mulAlpha(pc[0], sp), Colors.mulAlpha(pc[1], sp));
				}
				p.ring(sx, sy, sw, shh, sw * 0.45f, Math.max(p.px(), 0.5f * sc), t.border);
			}
		}

		// movement pad
		if (m.showMovement) {
			float cx = mx + W / 2, cy = by + bodyH * 0.52f, rad = Math.min(W, bodyH) * 0.26f;
			p.circle(cx, cy, rad, 0x30000000);
			p.ring(cx - rad, cy - rad, rad * 2, rad * 2, rad, Math.max(p.px(), 0.5f * sc), 0x50FFFFFF);
			p.rect(cx - rad, cy, rad * 2, p.px(), 0x25FFFFFF);
			p.rect(cx, cy - rad, p.px(), rad * 2, 0x25FFFFFF);
			double dx = src.moveX() * 0.12 * m.movementSensitivity, dy = src.moveY() * 0.12 * m.movementSensitivity;
			double len = Math.sqrt(dx * dx + dy * dy);
			double max = rad * 0.8;
			if (len > max) {
				dx = dx / len * max;
				dy = dy / len * max;
			}
			float dotR = Math.max(1.5f * sc, rad * 0.2f);
			p.shadow((float) (cx + dx) - dotR, (float) (cy + dy) - dotR, dotR * 2, dotR * 2, dotR, dotR, Colors.withAlpha(t.glow, 0xA0));
			p.circle((float) (cx + dx), (float) (cy + dy), dotR, Colors.withAlpha(t.glow, 0xFF));
		} else if (m.cps == OverlayConfig.CpsPos.INSIDE) {
			// tiny logo-ish highlight in the body
			p.textCentered("CPS", mx + W / 2, by + bodyH * 0.55f, Colors.mulAlpha(t.text, 0.45f), u * 0.24f);
		}

		if (m.cps == OverlayConfig.CpsPos.BELOW) {
			String s = src.cps(0) + "  |  " + src.cps(1) + " CPS";
			float ty = y + H + u * 0.18f;
			float tw = p.textWidth(s, u * 0.3f) + u * 0.4f;
			drawPill(p, mx + W / 2 - tw / 2, ty, tw, u * 0.44f, sc);
			p.textCentered(s, mx + W / 2, ty + u * 0.22f, t.text, u * 0.3f);
		}
		p.pop();
	}

	private static void drawPill(Painter p, float x, float y, float w, float h, float sc) {
		OverlayConfig.ThemeSettings t = cfg().theme;
		int[] bg = bgColors(0.5f, 0.5f, System.currentTimeMillis());
		p.round(x, y, w, h, h / 2, bg[0], bg[1]);
		p.ring(x, y, w, h, h / 2, Math.max(p.px(), 0.5f * sc), t.border);
	}

	private static void arrow(Painter p, float cx, float cy, float s, boolean up, int color) {
		int rows = 4;
		for (int i = 0; i < rows; i++) {
			float t = (i + 1f) / rows;
			float w = s * 2 * t;
			float yy = up ? cy - s + i * (s / rows) * 1.6f : cy + s - (i + 1) * (s / rows) * 1.6f;
			p.rect(cx - w / 2, yy, w, s / rows * 1.6f, color);
		}
	}

	private static void mouseButton(Painter p, float x, float y, float w, float h, float tl, float tr, float br, float bl,
									String label, float press, float[] tf, float nx, int cps, int count, float sc, long now,
									Source src, int button) {
		OverlayConfig c = cfg();
		OverlayConfig.ThemeSettings t = c.theme;
		p.push();
		p.scaleAround(x + w / 2, y + h / 2 + tf[1] * h, tf[0]);
		p.translate(0, tf[1] * h);
		int[] bg = bgColors(nx, 0.2f, now);
		int[] pc = pressColors(nx, 0.2f, now);
		if (press > 0.01f && c.anim.glow) p.shadow(x, y, w, h, Math.max(tl, tr) * 0.6f, 3 * sc, Colors.mulAlpha(t.glow, 0.9f * press));
		p.round4(x, y, w, h, tl, tr, br, bl, bg[0], bg[1]);
		if (press > 0.003f) p.round4(x, y, w, h, tl, tr, br, bl, Colors.mulAlpha(pc[0], press), Colors.mulAlpha(pc[1], press));
		if (c.anim.press == OverlayConfig.PressAnim.RIPPLE && c.keyboard.keyIndicators) {
			float rp = (now - src.mousePressAt(button)) / (480f / Math.max(0.2f, c.anim.speed));
			if (rp > 0 && rp < 1) {
				float e = Ease.outCubic(rp);
				p.round(x + w * (1 - e) / 2, y + h * (1 - e) / 2, w * e, h * e, Math.min(w, h) * 0.3f * e, Colors.mulAlpha(t.glow, 0.5f * (1 - rp)));
			}
		}
		if (t.highlight) p.round4(x + p.px(), y + p.px(), w - 2 * p.px(), h * 0.45f, tl, tr, 0, 0, Colors.withAlpha(0xFFFFFF, Math.round(70 * (1 - press * 0.5f))), 0x00FFFFFF);
		float sh = shimmer(nx, 0.2f, now);
		if (sh > 0.01f) p.round4(x, y, w, h, tl, tr, br, bl, Colors.withAlpha(0xFFFFFF, Math.round(100 * sh)), Colors.withAlpha(0xFFFFFF, Math.round(20 * sh)));
		p.ring4(x, y, w, h, tl, tr, br, bl, Math.max(p.px(), 0.5f * sc), Colors.lerp(t.border, Colors.withAlpha(t.glow, 0xCC), press * 0.6f));

		int col = Colors.lerp(t.text, t.textPressed, press);
		float u = c.keyboard.keyUnit * sc;
		boolean inside = c.mouse.cps == OverlayConfig.CpsPos.INSIDE;
		if (c.keyboard.showLabels) {
			p.textCentered(label, x + w / 2, y + h * (inside ? 0.62f : 0.66f), col, u * 0.3f, c.keyboard.labelShadow);
		}
		if (inside) {
			p.textCentered(Integer.toString(cps), x + w / 2, y + h * 0.84f, Colors.mulAlpha(col, 0.75f), u * 0.24f);
		}
		if (c.mouse.showCounters) {
			p.textCentered(Integer.toString(count), x + w / 2, y + h * 0.42f, Colors.mulAlpha(col, 0.5f), u * 0.2f);
		}
		p.pop();
	}

	private static void drawMouseClassic(Painter p, float x, float y, float u, float gap, float sc, Source src, long now) {
		OverlayConfig c = cfg();
		OverlayConfig.MouseSettings m = c.mouse;
		float r = c.keyboard.radius * sc;
		float w = u * 1.5f - gap / 2, h = u - gap;
		boolean inside = m.cps == OverlayConfig.CpsPos.INSIDE;
		for (int b = 0; b < 2; b++) {
			float kx = x + b * (w + gap);
			float press = mousePress(src, b, now);
			float[] tf = pressTransform(src.mouseDown(b), src.mousePressAt(b), src.mouseReleaseAt(b), now, press);
			float ripple = c.anim.press == OverlayConfig.PressAnim.RIPPLE ? (now - src.mousePressAt(b)) / 480f : -1;
			p.push();
			p.scaleAround(kx + w / 2, y + h / 2 + tf[1] * h, tf[0]);
			p.translate(0, tf[1] * h);
			String lab = b == 0 ? "LMB" : "RMB";
			drawKey(p, kx, y, w, h, Math.min(r, h / 2), inside ? null : lab, press, ripple, b == 0 ? 0.25f : 0.75f, 0.3f,
					0, 1, -1, sc, now, -1);
			if (inside) {
				int col = Colors.lerp(c.theme.text, c.theme.textPressed, press);
				if (c.keyboard.showLabels) p.textCentered(lab, kx + w / 2, y + h * 0.36f, col, u * 0.3f, c.keyboard.labelShadow);
				p.textCentered(src.cps(b) + " CPS", kx + w / 2, y + h * 0.7f, Colors.mulAlpha(col, 0.75f), u * 0.22f);
			}
			p.pop();
		}
		float yy = y + u;
		if (m.showMiddle || m.showScroll) {
			float mh = u * 0.55f - gap;
			float press = mousePress(src, PhysicalInput.MB_MIDDLE, now);
			drawKey(p, x, yy, u * 3 - gap, mh, Math.min(r, mh / 2), null, press, -1, 0.5f, 0.8f, 0, 1, -1, sc, now, -1);
			int col = Colors.lerp(c.theme.text, c.theme.textPressed, press);
			p.textCentered("MMB", x + (u * 3 - gap) / 2, yy + mh / 2, col, u * 0.24f);
			if (m.showScroll) {
				float up = 1 - Ease.outCubic((now - src.scrollUpAt()) / 260f);
				float dn = 1 - Ease.outCubic((now - src.scrollDownAt()) / 260f);
				float ax = x + (u * 3 - gap) * 0.25f, bx = x + (u * 3 - gap) * 0.75f;
				arrow(p, ax, yy + mh / 2, u * 0.11f, true, Colors.mulAlpha(up > 0 ? c.theme.glow : c.theme.text, Math.max(0.35f, up)));
				arrow(p, bx, yy + mh / 2, u * 0.11f, false, Colors.mulAlpha(dn > 0 ? c.theme.glow : c.theme.text, Math.max(0.35f, dn)));
			}
			yy += u * 0.55f;
		}
		if (m.cps == OverlayConfig.CpsPos.BELOW) {
			String s = src.cps(0) + "  |  " + src.cps(1) + " CPS";
			float tw = u * 3 - gap;
			drawPill(p, x, yy, tw, u * 0.44f, sc);
			p.textCentered(s, x + tw / 2, yy + u * 0.22f, c.theme.text, u * 0.28f);
		}
	}

	// ------------------------------------------------------------------ stats

	public static void drawStats(Painter p, float x, float y, float extraScale, Source src) {
		OverlayConfig c = cfg();
		OverlayConfig.StatsWidget s = c.statsWidget;
		float sc = s.scale * extraScale;
		float u = c.keyboard.keyUnit * sc;
		float[] size = statsSize(s);
		float w = size[0] * extraScale, h = size[1] * extraScale;
		p.push();
		p.alpha(s.opacity);
		drawPlate(p, x, y, w, h, Math.min(h / 2, c.keyboard.radius * sc * 1.5f), sc);
		float ty = y + u * 0.18f, line = u * 0.48f, size8 = u * 0.3f;
		int txt = c.theme.text, dim = Colors.mulAlpha(c.theme.text, 0.6f), acc = Colors.withAlpha(c.theme.glow, 0xFF);
		if (s.showCps) {
			p.text("CPS", x + u * 0.25f, ty + line * 0.25f, dim, size8);
			p.textRight(src.cps(0) + " • " + src.cps(1), x + w - u * 0.25f, ty + line * 0.25f, acc, size8);
			ty += line;
		}
		if (s.showKps) {
			p.text("KPS", x + u * 0.25f, ty + line * 0.25f, dim, size8);
			p.textRight(Integer.toString(PhysicalInput.kps()), x + w - u * 0.25f, ty + line * 0.25f, txt, size8);
			ty += line;
		}
		if (s.showTotals) {
			p.text("Keys", x + u * 0.25f, ty + line * 0.25f, dim, size8);
			p.textRight(Long.toString(c.stats.totalKeys), x + w - u * 0.25f, ty + line * 0.25f, txt, size8);
			ty += line;
			p.text("Clicks", x + u * 0.25f, ty + line * 0.25f, dim, size8);
			p.textRight(Long.toString(c.stats.totalLeft + c.stats.totalRight + c.stats.totalOther), x + w - u * 0.25f, ty + line * 0.25f, txt, size8);
		}
		p.pop();
	}
}
