package dev.liquidinput.gui;

import java.util.HashMap;
import java.util.Map;

import dev.liquidinput.config.ConfigManager;
import dev.liquidinput.render.Colors;
import dev.liquidinput.render.Ease;
import dev.liquidinput.render.Painter;
import dev.liquidinput.sound.SoundFx;

/**
 * Small immediate-mode widget toolkit with a liquid-glass look: springy toggles, sliding segment
 * pills, glowing sliders, squishy buttons, hover / click sounds and haptic nudges.
 */
public final class Ui {
	public Painter p;
	public float mx, my;
	public boolean down;
	public boolean clicked, rightClicked;
	public float scroll;
	public String active;
	public float dt = 1 / 60f;
	public int accent = 0xFF8FB8FF, accent2 = 0xFFB89CFF;

	private boolean pendingClick, pendingRight;
	private float pendingScroll;
	private String hovered, lastHovered;

	private final GlassScreen screen;
	private final Map<String, Float> anims = new HashMap<>();
	private final Map<String, Long> pressedAt = new HashMap<>();
	private final Map<String, Float> scrollPos = new HashMap<>();
	private final Map<String, Float> scrollTarget = new HashMap<>();
	private final Map<String, Float> scrollContent = new HashMap<>();

	private final float[][] clipStack = new float[8][];
	private int clipDepth;

	/** Rect of a modal popup (from the previous frame). Clicks outside it are ignored by other widgets. */
	public float[] modal;
	private float[] nextModal;
	private boolean inModal;

	public Ui(GlassScreen screen) {
		this.screen = screen;
	}

	// ------------------------------------------------------------------ frame

	public void beginFrame(Painter p, float mx, float my, float dt) {
		this.p = p;
		this.mx = mx;
		this.my = my;
		this.dt = dt;
		clicked = pendingClick;
		rightClicked = pendingRight;
		scroll = pendingScroll;
		pendingClick = pendingRight = false;
		pendingScroll = 0;
		modal = nextModal;
		nextModal = null;
		clipDepth = 0;
	}

	public void endFrame() {
		if (hovered != null && !hovered.equals(lastHovered)) SoundFx.ui(SoundFx.Ui.HOVER, 1.1f);
		lastHovered = hovered;
		hovered = null;
		if (!down && active != null) {
			active = null;
			ConfigManager.markDirty();
		}
	}

	public void press(int button) {
		if (button == 0) {
			pendingClick = true;
			down = true;
		} else if (button == 1) {
			pendingRight = true;
		}
	}

	public void release(int button) {
		if (button == 0) down = false;
	}

	public void addScroll(double v) {
		pendingScroll += (float) v;
	}

	// ------------------------------------------------------------------ helpers

	public void beginModal(float x, float y, float w, float h) {
		nextModal = new float[]{x, y, w, h};
		inModal = true;
	}

	public void endModal() {
		inModal = false;
	}

	public boolean clickOutsideModal() {
		return modal != null && clicked && !(mx >= modal[0] && mx < modal[0] + modal[2] && my >= modal[1] && my < modal[1] + modal[3]);
	}

	public boolean over(float x, float y, float w, float h) {
		if (!(mx >= x && mx < x + w && my >= y && my < y + h)) return false;
		for (int i = 0; i < clipDepth; i++) {
			float[] c = clipStack[i];
			if (!(mx >= c[0] && mx < c[0] + c[2] && my >= c[1] && my < c[1] + c[3])) return false;
		}
		// while a popup is open, nothing behind it reacts
		return modal == null || inModal;
	}

	public boolean hover(String id, float x, float y, float w, float h) {
		boolean o = over(x, y, w, h) && (active == null || active.equals(id));
		if (o) hovered = id;
		return o;
	}

	public boolean click(String id, float x, float y, float w, float h) {
		if (clicked && over(x, y, w, h)) {
			clicked = false;
			pressedAt.put(id, System.currentTimeMillis());
			return true;
		}
		return false;
	}

	public boolean rightClick(float x, float y, float w, float h) {
		if (rightClicked && over(x, y, w, h)) {
			rightClicked = false;
			return true;
		}
		return false;
	}

	public float anim(String id, float target, float speed) {
		Float v = anims.get(id);
		float cur = v == null ? target : v;
		cur = Ease.approach(cur, target, speed, dt);
		anims.put(id, cur);
		return cur;
	}

	public void setAnim(String id, float v) {
		anims.put(id, v);
	}

	/** Squish scale after a click: dips then springs back with a tiny overshoot. */
	public float squish(String id) {
		Long t = pressedAt.get(id);
		if (t == null) return 1f;
		float age = System.currentTimeMillis() - t;
		if (age > 600) return 1f;
		return 1f - 0.08f * (float) Math.exp(-age / 60f) + 0.03f * (float) (Math.exp(-age / 150f) * Math.sin(age / 40f));
	}

	public void haptic(float s) {
		screen.haptic(s);
	}

	public void pushClip(float x, float y, float w, float h) {
		clipStack[clipDepth++] = new float[]{x, y, w, h};
		p.scissor(x, y, w, h);
	}

	public void popClip() {
		clipDepth--;
		p.endScissor();
		if (clipDepth > 0) {
			float[] c = clipStack[clipDepth - 1];
			p.scissor(c[0], c[1], c[2], c[3]);
		}
	}

	// ------------------------------------------------------------------ widgets

	public void glassPanel(float x, float y, float w, float h, float r, float strength) {
		p.round(x, y, w, h, r, Colors.withAlpha(0xFFFFFF, Math.round(30 * strength)), Colors.withAlpha(0xFFFFFF, Math.round(12 * strength)));
		p.round4(x, y, w, Math.min(h, 14), r, r, 0, 0, Colors.withAlpha(0xFFFFFF, Math.round(22 * strength)), 0x00FFFFFF);
		p.ring(x, y, w, h, r, p.px(), Colors.withAlpha(0xFFFFFF, Math.round(48 * strength)));
	}

	public boolean button(String id, float x, float y, float w, float h, String label, boolean primary) {
		boolean hov = hover(id, x, y, w, h);
		float ht = anim(id + "#h", hov ? 1 : 0, 14);
		boolean hit = click(id, x, y, w, h);
		if (hit) {
			SoundFx.ui(SoundFx.Ui.TAP, primary ? 1.08f : 1f);
			haptic(primary ? 0.7f : 0.45f);
		}
		float s = squish(id) * (1 + 0.02f * ht);
		p.push();
		p.scaleAround(x + w / 2, y + h / 2, s);
		float r = h / 2;
		if (primary) {
			p.shadow(x, y, w, h, r, 4 + 3 * ht, Colors.withAlpha(accent, 70 + Math.round(60 * ht)));
			p.round(x, y, w, h, r, Colors.brighten(accent, 0.12f * ht), accent2);
			p.round4(x + p.px(), y + p.px(), w - 2 * p.px(), h * 0.5f, r, r, 0, 0, 0x55FFFFFF, 0x10FFFFFF);
			p.ring(x, y, w, h, r, p.px(), 0x70FFFFFF);
			p.textCentered(label, x + w / 2, y + h / 2, 0xFF0E1220, 8);
		} else {
			p.round(x, y, w, h, r, Colors.withAlpha(0xFFFFFF, 34 + Math.round(30 * ht)), Colors.withAlpha(0xFFFFFF, 14 + Math.round(20 * ht)));
			p.round4(x + p.px(), y + p.px(), w - 2 * p.px(), h * 0.5f, r, r, 0, 0, 0x22FFFFFF, 0x00FFFFFF);
			p.ring(x, y, w, h, r, p.px(), Colors.withAlpha(0xFFFFFF, 60 + Math.round(60 * ht)));
			p.textCentered(label, x + w / 2, y + h / 2, 0xFFF4F6FF, 8);
		}
		p.pop();
		return hit;
	}

	public boolean toggle(String id, float x, float y, boolean value) {
		float w = 24, h = 13;
		boolean hov = hover(id, x - 2, y - 2, w + 4, h + 4);
		if (click(id, x - 2, y - 2, w + 4, h + 4)) {
			value = !value;
			SoundFx.ui(value ? SoundFx.Ui.TOGGLE_ON : SoundFx.Ui.TOGGLE_OFF);
			haptic(0.55f);
			ConfigManager.markDirty();
		}
		float t = anim(id + "#t", value ? 1 : 0, 13);
		float ht = anim(id + "#h", hov ? 1 : 0, 14);
		float r = h / 2;
		if (t > 0.02f) p.shadow(x, y, w, h, r, 3, Colors.withAlpha(accent, Math.round(90 * t)));
		p.round(x, y, w, h, r, Colors.withAlpha(0xFFFFFF, 40 + Math.round(20 * ht)), Colors.withAlpha(0xFFFFFF, 20));
		if (t > 0.01f) p.round(x, y, w, h, r, Colors.mulAlpha(accent, t), Colors.mulAlpha(accent2, t));
		p.ring(x, y, w, h, r, p.px(), Colors.withAlpha(0xFFFFFF, 70));
		// liquid knob: stretches while travelling
		float d = h - 3;
		float stretch = (float) Math.sin(Math.PI * t) * 5f;
		float kx = x + 1.5f + (w - 3 - d) * t - stretch * (value ? 1 : 0) * 0.5f;
		float kw = d + stretch;
		p.shadow(kx, y + 1.5f, kw, d, d / 2, 1.5f, 0x40000000);
		p.round(kx, y + 1.5f, kw, d, d / 2, 0xFFFFFFFF, 0xFFE6EAF5);
		return value;
	}

	public float slider(String id, float x, float y, float w, float value, float min, float max, float step) {
		float h = 12;
		boolean hov = hover(id, x - 3, y, w + 6, h);
		if (click(id, x - 3, y, w + 6, h)) {
			active = id;
			haptic(0.25f);
		}
		if (id.equals(active) && down) {
			float t = Ease.clamp01((mx - x) / w);
			float nv = min + t * (max - min);
			if (step > 0) nv = Math.round(nv / step) * step;
			nv = Math.max(min, Math.min(max, nv));
			if (Math.abs(nv - value) > 1e-6f) {
				SoundFx.ui(SoundFx.Ui.SLIDE, 0.8f + 0.6f * (nv - min) / (max - min));
				value = nv;
				ConfigManager.markDirty();
			}
		}
		if (hov && scroll != 0 && active == null) {
			float st = step > 0 ? step : (max - min) / 50f;
			value = Math.max(min, Math.min(max, value + Math.signum(scroll) * st));
			scroll = 0;
			SoundFx.ui(SoundFx.Ui.SLIDE, 0.8f + 0.6f * (value - min) / (max - min));
			ConfigManager.markDirty();
		}
		float t = (value - min) / (max - min);
		float vt = anim(id + "#v", t, 22);
		boolean drag = id.equals(active);
		float ht = anim(id + "#h", hov || drag ? 1 : 0, 14);
		float th = 4, ty = y + h / 2 - th / 2;
		p.round(x, ty, w, th, th / 2, 0x38FFFFFF, 0x24FFFFFF);
		p.hgradRound(x, ty, Math.max(th, w * vt), th, accent, accent2);
		float kd = 8 + 2 * ht;
		float kx = x + w * vt - kd / 2, ky = y + h / 2 - kd / 2;
		if (ht > 0.05f) p.shadow(kx, ky, kd, kd, kd / 2, 3 * ht + 1, Colors.withAlpha(accent, Math.round(120 * ht)));
		p.circle(kx + kd / 2, ky + kd / 2, kd / 2, 0xFFFFFFFF);
		p.circle(kx + kd / 2, ky + kd / 2, kd / 2 - 2.2f, Colors.lerp(0xFFFFFFFF, accent, 0.35f + 0.4f * ht));
		return value;
	}

	public int segmented(String id, float x, float y, float w, float h, String[] opts, int sel) {
		float r = h / 2;
		p.round(x, y, w, h, r, 0x26FFFFFF, 0x14FFFFFF);
		p.ring(x, y, w, h, r, p.px(), 0x40FFFFFF);
		float ow = w / opts.length;
		float target = x + ow * sel;
		float px = anim(id + "#x", target, 16);
		// liquid stretch while moving
		float dist = Math.abs(target - px);
		float stretch = Math.min(ow * 0.35f, dist * 0.5f);
		float lx = px - (target < px ? stretch : 0);
		p.shadow(lx + 1, y + 1, ow - 2 + stretch, h - 2, r - 1, 3, Colors.withAlpha(accent, 80));
		p.round(lx + 1, y + 1, ow - 2 + stretch, h - 2, r - 1, Colors.brighten(accent, 0.1f), accent2);
		p.round4(lx + 1.5f, y + 1.5f, ow - 3 + stretch, (h - 3) * 0.5f, r - 1, r - 1, 0, 0, 0x50FFFFFF, 0x00FFFFFF);
		for (int i = 0; i < opts.length; i++) {
			float ox = x + ow * i;
			String oid = id + "#" + i;
			boolean hov = hover(oid, ox, y, ow, h);
			if (click(oid, ox, y, ow, h) && i != sel) {
				sel = i;
				SoundFx.ui(SoundFx.Ui.TAB, 1f + i * 0.04f);
				haptic(0.4f);
				ConfigManager.markDirty();
			}
			boolean on = i == sel;
			float size = p.fit(opts[i], 8, ow - 6);
			int col = on ? 0xFF0E1220 : (hov ? 0xFFFFFFFF : 0xCCE6EAF5);
			p.textCentered(opts[i], ox + ow / 2, y + h / 2, col, size);
		}
		return sel;
	}

	/** Grid of selectable chips. */
	public int chips(String id, float x, float y, float w, String[] opts, int sel, int cols, float h) {
		float gap = 4;
		float cw = (w - gap * (cols - 1)) / cols;
		for (int i = 0; i < opts.length; i++) {
			float cx = x + (i % cols) * (cw + gap), cy = y + (i / cols) * (h + gap);
			String oid = id + "#" + i;
			boolean hov = hover(oid, cx, cy, cw, h);
			if (click(oid, cx, cy, cw, h) && i != sel) {
				sel = i;
				SoundFx.ui(SoundFx.Ui.TAB, 1f + i * 0.03f);
				haptic(0.4f);
				ConfigManager.markDirty();
			}
			boolean on = i == sel;
			float ot = anim(oid + "#on", on ? 1 : 0, 14);
			float ht = anim(oid + "#h", hov ? 1 : 0, 14);
			float s = squish(oid);
			p.push();
			p.scaleAround(cx + cw / 2, cy + h / 2, s);
			p.round(cx, cy, cw, h, h / 2, Colors.withAlpha(0xFFFFFF, 26 + Math.round(24 * ht)), 0x12FFFFFF);
			if (ot > 0.01f) {
				p.shadow(cx, cy, cw, h, h / 2, 3, Colors.withAlpha(accent, Math.round(80 * ot)));
				p.round(cx, cy, cw, h, h / 2, Colors.mulAlpha(Colors.brighten(accent, 0.1f), ot), Colors.mulAlpha(accent2, ot));
			}
			p.ring(cx, cy, cw, h, h / 2, p.px(), 0x45FFFFFF);
			p.textCentered(opts[i], cx + cw / 2, cy + h / 2, Colors.lerp(0xFFE6EAF5, 0xFF0E1220, ot), p.fit(opts[i], 8, cw - 6));
			p.pop();
		}
		return sel;
	}

	public float chipsHeight(int count, int cols, float h) {
		int rows = (count + cols - 1) / cols;
		return rows * h + (rows - 1) * 4;
	}

	/** @return 1 = start capture, 2 = clear (right click), 0 = nothing */
	public int keybind(String id, float x, float y, float w, float h, String text, boolean listening) {
		boolean hov = hover(id, x, y, w, h);
		float ht = anim(id + "#h", hov ? 1 : 0, 14);
		int res = 0;
		if (click(id, x, y, w, h)) {
			res = 1;
			SoundFx.ui(SoundFx.Ui.TAP);
			haptic(0.4f);
		} else if (rightClick(x, y, w, h)) {
			res = 2;
			SoundFx.ui(SoundFx.Ui.TOGGLE_OFF);
			haptic(0.3f);
		}
		float pulse = listening ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 160.0) : 0;
		p.push();
		p.scaleAround(x + w / 2, y + h / 2, squish(id));
		if (listening) p.shadow(x, y, w, h, 4, 4, Colors.withAlpha(accent, 60 + Math.round(80 * pulse)));
		p.round(x, y, w, h, 4, Colors.withAlpha(0xFFFFFF, 30 + Math.round(25 * ht)), 0x14FFFFFF);
		p.ring(x, y, w, h, 4, p.px(), listening ? Colors.withAlpha(accent, 255) : 0x55FFFFFF);
		p.textCentered(listening ? "Press a key…" : text, x + w / 2, y + h / 2, listening ? Colors.withAlpha(accent, 255) : 0xFFFFFFFF, p.fit(text, 8, w - 6));
		p.pop();
		return res;
	}

	public void checker(float x, float y, float w, float h, float cell) {
		p.rect(x, y, w, h, 0xFFE8E8E8);
		for (float yy = 0; yy < h; yy += cell) {
			for (float xx = ((int) (yy / cell) % 2) * cell; xx < w; xx += cell * 2) {
				p.rect(x + xx, y + yy, Math.min(cell, w - xx), Math.min(cell, h - yy), 0xFFB8B8B8);
			}
		}
	}

	public boolean swatch(String id, float x, float y, float w, float h, int color) {
		boolean hov = hover(id, x, y, w, h);
		boolean hit = click(id, x, y, w, h);
		if (hit) {
			SoundFx.ui(SoundFx.Ui.TAP, 1.15f);
			haptic(0.35f);
		}
		float ht = anim(id + "#h", hov ? 1 : 0, 14);
		p.push();
		p.scaleAround(x + w / 2, y + h / 2, squish(id) * (1 + 0.05f * ht));
		pushClip(x, y, w, h);
		checker(x, y, w, h, 3);
		popClip();
		p.round(x, y, w, h, 3, color);
		p.ring(x, y, w, h, 3, p.px(), Colors.withAlpha(0xFFFFFF, 90 + Math.round(100 * ht)));
		p.pop();
		return hit;
	}

	// ------------------------------------------------------------------ scrolling

	public float beginScroll(String id, float x, float y, float w, float h) {
		float content = scrollContent.getOrDefault(id, h);
		float maxScroll = Math.max(0, content - h);
		float target = scrollTarget.getOrDefault(id, 0f);
		if (scroll != 0 && over(x, y, w, h) && active == null) {
			target -= scroll * 28;
			scroll = 0;
		}
		target = Math.max(0, Math.min(maxScroll, target));
		scrollTarget.put(id, target);
		float pos = scrollPos.getOrDefault(id, 0f);
		pos = Ease.approach(pos, target, 18, dt);
		if (Math.abs(pos - target) < 0.05f) pos = target;
		scrollPos.put(id, pos);
		pushClip(x, y, w, h);
		return pos;
	}

	public void endScroll(String id, float x, float y, float w, float h, float contentHeight) {
		popClip();
		scrollContent.put(id, contentHeight);
		if (contentHeight > h + 1) {
			float pos = scrollPos.getOrDefault(id, 0f);
			float bh = Math.max(16, h * h / contentHeight);
			float by = y + (h - bh) * (pos / (contentHeight - h));
			p.round(x + w - 3, by, 2, bh, 1, 0x55FFFFFF);
		}
	}

	public void resetScroll(String id) {
		scrollTarget.put(id, 0f);
		scrollPos.put(id, 0f);
	}
}
