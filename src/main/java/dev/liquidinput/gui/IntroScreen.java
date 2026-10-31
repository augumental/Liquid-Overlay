package dev.liquidinput.gui;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.liquidinput.config.ConfigManager;
import dev.liquidinput.config.OverlayConfig;
import dev.liquidinput.input.PhysicalInput;
import dev.liquidinput.render.Colors;
import dev.liquidinput.render.Ease;
import dev.liquidinput.render.OverlayRenderer;
import dev.liquidinput.render.Painter;
import dev.liquidinput.sound.SoundFx;

/** The opening animation, ending on a card where the overlay can be switched on or off. */
public class IntroScreen extends GlassScreen {
	private static final String TITLE = "LIQUID INPUT";
	private static final float CARD_AT = 1500;

	private int lettersPlayed = -1;
	private boolean cardSoundPlayed;
	private long demoStart;

	public IntroScreen(Screen parent) {
		super(Component.literal("Liquid Input"), parent);
	}

	@Override
	protected void onOpened() {
		SoundFx.ui(SoundFx.Ui.OPEN, 0.9f);
		demoStart = System.currentTimeMillis();
	}

	private float elapsed() {
		return System.currentTimeMillis() - openedAt;
	}

	/** A scripted "someone is playing" input source for the demo keys. */
	private final OverlayRenderer.Source demo = new OverlayRenderer.Source() {
		private final int[] seq = {GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_SPACE, -1, -2};
		private static final long STEP = 300, HOLD = 170;

		private int slot(long t) {
			return (int) ((t / STEP) % seq.length);
		}

		private long t() {
			return Math.max(0, System.currentTimeMillis() - demoStart);
		}

		private boolean isDown(int code) {
			long t = t();
			return seq[slot(t)] == code && t % STEP < HOLD;
		}

		private long pressAt(int code) {
			long t = t();
			long cycle = STEP * seq.length;
			int idx = -1;
			for (int i = 0; i < seq.length; i++) if (seq[i] == code) idx = i;
			if (idx < 0) return 0;
			long base = (t / cycle) * cycle + idx * STEP;
			if (base > t) base -= cycle;
			return demoStart + Math.max(0, base);
		}

		public boolean keyDown(int key) { return isDown(key); }
		public long keyPressAt(int key) { return pressAt(key); }
		public long keyReleaseAt(int key) { long p = pressAt(key); return p == 0 ? 0 : p + HOLD; }
		public boolean mouseDown(int b) { return isDown(-1 - b); }
		public long mousePressAt(int b) { return pressAt(-1 - b); }
		public long mouseReleaseAt(int b) { long p = pressAt(-1 - b); return p == 0 ? 0 : p + HOLD; }
		public int cps(int b) { return b == 0 ? 7 : 3; }
		public long scrollUpAt() { return 0; }
		public long scrollDownAt() { return 0; }
		public double moveX() { return 0; }
		public double moveY() { return 0; }
	};

	@Override
	protected void draw(Painter p, float dt) {
		OverlayConfig c = ConfigManager.get();
		float e = elapsed();
		float close = closeT();
		float cx = width / 2f;

		p.push();
		p.alpha(1 - close);

		// ---- orb
		float orbIn = Ease.outBack(Math.min(1, e / 520f), 2.2f);
		float rise = Ease.inOutCubic((e - 900) / 700f);
		float orbY = Ease.lerp(height * 0.42f, height * 0.18f, rise);
		float orbR = 22 * orbIn * (1 - 0.3f * rise);
		float pulse = 0.5f + 0.5f * (float) Math.sin(e / 380.0);
		if (orbR > 0.5f) {
			p.blob(cx, orbY, orbR * (3.2f + 0.4f * pulse), Colors.withAlpha(ui.accent, 0x90));
			p.circle(cx, orbY, orbR, ui.accent);
			p.circle(cx + orbR * 0.15f, orbY + orbR * 0.18f, orbR * 0.78f, ui.accent2);
			// wobbling liquid highlight
			float wob = (float) Math.sin(e / 260.0) * orbR * 0.08f;
			p.circle(cx - orbR * 0.32f + wob, orbY - orbR * 0.36f, orbR * 0.28f, 0xB0FFFFFF);
			p.circle(cx + orbR * 0.4f, orbY + orbR * 0.42f, orbR * 0.1f, 0x80FFFFFF);
		}

		// ---- title letters cascade
		float titleY = orbY + orbR + 14 + (1 - rise) * 6;
		float size = 20;
		float total = p.textWidth(TITLE, size) + (TITLE.length() - 1) * 2;
		float lx = cx - total / 2;
		for (int i = 0; i < TITLE.length(); i++) {
			String ch = String.valueOf(TITLE.charAt(i));
			float lt = (e - 260 - i * 48) / 380f;
			if (lt > 0 && i > lettersPlayed && ch.trim().length() > 0) {
				lettersPlayed = i;
				SoundFx.ui(SoundFx.Ui.HOVER, 1.0f + i * 0.045f);
			}
			float a = Ease.clamp01(lt * 1.6f);
			float yOff = (1 - Ease.outBack(Ease.clamp01(lt), 2.4f)) * -14;
			int col = Colors.lerp(0xFFFFFFFF, ui.accent, 0.25f * (0.5f + 0.5f * (float) Math.sin(e / 300.0 + i * 0.6)));
			if (a > 0) {
				p.push();
				p.alpha(a);
				p.text(ch, lx, titleY + yOff, col, size, false);
				p.pop();
			}
			lx += p.textWidth(ch, size) + 2;
		}

		// ---- subtitle
		float st = Ease.outCubic((e - 820) / 450f);
		if (st > 0) {
			p.push();
			p.alpha(st);
			p.textCentered("A keystroke overlay that only trusts real fingers.", cx, titleY + 30 + (1 - st) * 6, 0xCCE6EAF5, 8);
			p.pop();
		}

		// ---- demo keys
		float dtIn = Ease.clamp01((e - 1000) / 500f);
		if (dtIn > 0) drawDemo(p, cx, titleY + 48, dtIn);

		// ---- control card
		float ct = (e - CARD_AT) / 450f;
		if (ct > 0) {
			if (!cardSoundPlayed) {
				cardSoundPlayed = true;
				SoundFx.ui(SoundFx.Ui.WHOOSH);
			}
			drawCard(p, c, cx, Math.min(height - 70, titleY + 132), Ease.clamp01(ct));
		} else if (ui.clicked) {
			// click to skip ahead
			ui.clicked = false;
			openedAt -= (long) (CARD_AT - e);
			SoundFx.ui(SoundFx.Ui.TAP);
		}

		p.pop();
	}

	private void drawDemo(Painter p, float cx, float y, float t) {
		OverlayConfig c = ConfigManager.get();
		long now = System.currentTimeMillis();
		float u = 22, gap = 3, r = 6;
		float kbW = 3 * u - gap, total = kbW + 16 + u * 2.4f * 0.8f;
		float x = cx - total / 2;
		int[][] keys = {{GLFW.GLFW_KEY_W, 1, 0}, {GLFW.GLFW_KEY_A, 0, 1}, {GLFW.GLFW_KEY_S, 1, 1}, {GLFW.GLFW_KEY_D, 2, 1}};
		String[] labels = c.keyboard.lang == OverlayConfig.Lang.AZERTY ? new String[]{"Z", "Q", "S", "D"} : new String[]{"W", "A", "S", "D"};
		for (int i = 0; i < keys.length; i++) {
			float kt = Ease.clamp01((t * 500 - i * 60) / 300f);
			if (kt <= 0) continue;
			float kx = x + keys[i][1] * u, ky = y + keys[i][2] * u;
			float press = demoPress(keys[i][0], now);
			p.push();
			p.alpha(kt);
			p.scaleAround(kx + (u - gap) / 2, ky + (u - gap) / 2, (0.6f + 0.4f * Ease.outBack(kt)) * (1 - 0.08f * press));
			OverlayRenderer.drawKey(p, kx, ky, u - gap, u - gap, r, labels[i], press, -1, keys[i][1] / 3f, keys[i][2] / 3f,
					0, 1, -1, 1f, now, -1);
			p.pop();
		}
		// space bar
		float spt = Ease.clamp01((t * 500 - 260) / 300f);
		if (spt > 0) {
			float press = demoPress(GLFW.GLFW_KEY_SPACE, now);
			p.push();
			p.alpha(spt);
			OverlayRenderer.drawKey(p, x, y + 2 * u, kbW, u * 0.6f, r * 0.8f, "", press, -1, 0.5f, 1f, 0, 1, -1, 1f, now, -1);
			p.pop();
		}
		// mouse
		float mt = Ease.clamp01((t * 500 - 180) / 320f);
		if (mt > 0) {
			p.push();
			p.alpha(mt);
			float ms = 0.8f * (22f / Math.max(1, c.keyboard.keyUnit)) / Math.max(0.1f, c.mouse.scale);
			OverlayRenderer.drawMouse(p, x + kbW + 16, y - 4, ms, demo, false);
			p.pop();
		}
	}

	private float demoPress(int key, long now) {
		boolean down = demo.keyDown(key);
		if (down) return 1;
		long ra = demo.keyReleaseAt(key);
		if (ra <= 0) return 0;
		return 1 - Ease.outCubic((now - ra) / 160f);
	}

	private void drawCard(Painter p, OverlayConfig c, float cx, float y, float t) {
		float w = 230, h = 64;
		float x = cx - w / 2;
		p.push();
		p.alpha(t);
		p.scaleAround(cx, y + h / 2, 0.85f + 0.15f * Ease.outBack(t, 1.8f));
		p.translate(nudgeX, nudgeY + (1 - Ease.outCubic(t)) * 16);
		ui.mx -= nudgeX;
		ui.my -= nudgeY;

		p.shadow(x, y + 4, w, h, 16, 18, 0x90000000);
		p.round(x, y, w, h, 16, 0xC81C2130, 0xD8101420);
		p.blob(x + w * 0.2f, y + h * 0.3f, 50, Colors.withAlpha(ui.accent, 0x50));
		p.round4(x, y, w, h * 0.45f, 16, 16, 0, 0, 0x26FFFFFF, 0x00FFFFFF);
		p.ring(x, y, w, h, 16, p.px(), 0x66FFFFFF);

		// big liquid toggle
		float tw = 52, th = 26, tx = x + 14, ty = y + (h - th) / 2;
		boolean hov = ui.hover("bigToggle", tx, ty, tw, th);
		if (ui.click("bigToggle", tx - 4, ty - 4, tw + 8, th + 8)) {
			c.enabled = !c.enabled;
			SoundFx.ui(c.enabled ? SoundFx.Ui.TOGGLE_ON : SoundFx.Ui.TOGGLE_OFF);
			haptic(0.9f);
			ConfigManager.markDirty();
			if (c.enabled) OverlayRenderer.restartAppear();
		}
		float kt = ui.anim("bigToggle#t", c.enabled ? 1 : 0, 11);
		float ht = ui.anim("bigToggle#h", hov ? 1 : 0, 14);
		p.push();
		p.scaleAround(tx + tw / 2, ty + th / 2, ui.squish("bigToggle") * (1 + 0.03f * ht));
		if (kt > 0.02f) p.shadow(tx, ty, tw, th, th / 2, 7, Colors.withAlpha(ui.accent, Math.round(130 * kt)));
		p.round(tx, ty, tw, th, th / 2, 0x40FFFFFF, 0x20FFFFFF);
		if (kt > 0.01f) p.round(tx, ty, tw, th, th / 2, Colors.mulAlpha(ui.accent, kt), Colors.mulAlpha(ui.accent2, kt));
		p.round4(tx + 1, ty + 1, tw - 2, th * 0.5f, th / 2, th / 2, 0, 0, 0x40FFFFFF, 0x00FFFFFF);
		p.ring(tx, ty, tw, th, th / 2, p.px(), 0x80FFFFFF);
		float d = th - 6, stretch = (float) Math.sin(Math.PI * kt) * 10;
		float kx = tx + 3 + (tw - 6 - d) * kt - stretch * (c.enabled ? 1 : 0) * 0.5f;
		p.shadow(kx, ty + 3, d + stretch, d, d / 2, 3, 0x50000000);
		p.round(kx, ty + 3, d + stretch, d, d / 2, 0xFFFFFFFF, 0xFFDDE3F0);
		p.pop();

		p.text(c.enabled ? "Overlay is ON" : "Overlay is OFF", tx + tw + 10, ty + 3, 0xFFFFFFFF, 8);
		p.text(c.enabled ? "Showing your physical input" : "Nothing will be drawn", tx + tw + 10, ty + 14, 0x99E0E6F0, 6);

		if (ui.button("continue", x + w - 66, y + h - 22, 56, 14, "Continue ›", true)) finish();
		ui.mx += nudgeX;
		ui.my += nudgeY;
		p.pop();

		// hint below
		float blink = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 400.0);
		p.textCentered("Tap Left Ctrl in-game any time to come back", cx, y + h + 12, Colors.withAlpha(0xFFFFFF, Math.round(80 + 60 * blink)), 6);
	}

	private void finish() {
		ConfigManager.get().introSeen = true;
		ConfigManager.save();
		SoundFx.ui(SoundFx.Ui.WHOOSH, 1.1f);
		switchTo(new MainScreen(parent));
	}

	@Override
	protected void onEscape() {
		ConfigManager.get().introSeen = true;
		onClose();
	}

	@Override
	protected boolean onKey(int key) {
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			if (elapsed() >= CARD_AT) finish();
			else openedAt -= (long) (CARD_AT - elapsed());
			return true;
		}
		return false;
	}

	@Override
	public void tick() {
		PhysicalInput.frame();
	}
}
