package dev.liquidinput.gui;

import java.util.function.IntConsumer;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;

import dev.liquidinput.config.ConfigManager;
import dev.liquidinput.config.OverlayConfig;
import dev.liquidinput.config.ThemePresets;
import dev.liquidinput.input.PhysicalInput;
import dev.liquidinput.input.Stats;
import dev.liquidinput.input.WinInputGuard;
import dev.liquidinput.layout.KeyboardLayouts;
import dev.liquidinput.render.Colors;
import dev.liquidinput.render.Ease;
import dev.liquidinput.render.OverlayRenderer;
import dev.liquidinput.render.Painter;
import dev.liquidinput.sound.CustomSounds;
import dev.liquidinput.sound.SoundFx;

/** The main liquid-glass settings window. */
public class MainScreen extends GlassScreen {
	enum Tab {
		GENERAL("General", "Behaviour, hotkeys & physical-input guard"),
		KEYBOARD("Keyboard", "Layout, size and which keys are shown"),
		MOUSE("Mouse", "Buttons, wheel, CPS and the stats panel"),
		THEME("Theme", "Colours, gradients and glass"),
		ANIMATION("Animation", "How keys react and breathe"),
		SOUNDS("Sounds", "Key and click sounds, custom audio"),
		PROFILE("Profile", "You, your stats and saved setups");

		final String title, desc;

		Tab(String title, String desc) {
			this.title = title;
			this.desc = desc;
		}
	}

	private static Tab lastTab = Tab.GENERAL;
	private Tab tab = lastTab;
	private long tabChangedAt;

	// key editor
	private boolean editKeys;
	private String selectedKey;

	// capture state: "open", "toggle", "rebind"
	private String capturing;

	// colour picker
	private int pickIndex = -1;
	private float pickH, pickS, pickV;
	private int pickA;

	private String toast;
	private long toastAt;
	private long confirmResetAt, confirmStatsAt;

	// layout cursor for setting rows
	private Painter p;
	private float rx, rw, ry;

	public MainScreen(Screen parent) {
		super(Component.literal("Liquid Input Overlay"), parent);
	}

	@Override
	protected void onOpened() {
		SoundFx.ui(SoundFx.Ui.OPEN);
		tabChangedAt = System.currentTimeMillis();
	}

	private OverlayConfig cfg() {
		return ConfigManager.get();
	}

	private void showToast(String s) {
		toast = s;
		toastAt = System.currentTimeMillis();
	}

	// ================================================================== frame

	@Override
	protected void draw(Painter p, float dt) {
		this.p = p;
		OverlayConfig c = cfg();
		float rawMx = ui.mx, rawMy = ui.my;

		float W = Math.min(width - 20, 480), H = Math.min(height - 16, 300);
		float x0 = (width - W) / 2f, y0 = (height - H) / 2f;
		float cxw = x0 + W / 2, cyw = y0 + H / 2;

		float open = openT(480), close = closeT();
		float scale = (0.9f + 0.1f * Ease.outBack(open, 1.4f)) * (1 - 0.06f * Ease.outCubic(close)) * (1 - 0.01f * thump);
		float alpha = Ease.outCubic(Math.min(1, open * 1.8f)) * (1 - close);
		float lift = (1 - Ease.outCubic(open)) * 12 + close * 8;

		p.push();
		p.alpha(alpha);
		p.scaleAround(cxw, cyw, scale);
		p.translate(nudgeX, nudgeY + lift);
		// map the mouse into window space so hit-testing follows the animation
		ui.mx = (rawMx - cxw) / scale + cxw - nudgeX;
		ui.my = (rawMy - cyw) / scale + cyw - nudgeY - lift;

		drawWindow(x0, y0, W, H);
		drawSidebar(x0 + 6, y0 + 6, 112, H - 12);
		drawContent(x0 + 124, y0 + 6, W - 130, H - 12);
		drawToast(x0, y0, W, H);
		if (pickIndex >= 0) drawColorPicker(x0, y0, W, H);

		p.pop();
	}

	// ================================================================== window chrome

	private void drawWindow(float x, float y, float w, float h) {
		float r = 14;
		long now = System.currentTimeMillis();
		p.shadow(x, y + 5, w, h, r, 22, 0x99000000);
		p.round(x, y, w, h, r, 0xD2181C28, 0xE00C0E15);
		// liquid blobs drifting inside the glass
		ui.pushClip(x, y, w, h);
		float t = now / 1000f;
		p.blob(x + w * (0.25f + 0.12f * (float) Math.sin(t * 0.35f)), y + h * (0.3f + 0.15f * (float) Math.cos(t * 0.27f)), 120, Colors.withAlpha(ui.accent, 0x70));
		p.blob(x + w * (0.78f + 0.1f * (float) Math.cos(t * 0.3f)), y + h * (0.75f + 0.12f * (float) Math.sin(t * 0.22f)), 140, Colors.withAlpha(ui.accent2, 0x60));
		ui.popClip();
		// frosted sheen + specular rim
		p.round4(x, y, w, h * 0.42f, r, r, 0, 0, 0x22FFFFFF, 0x00FFFFFF);
		p.ring(x, y, w, h, r, p.px(), 0x55FFFFFF);
		p.ring(x + 1, y + 1, w - 2, h - 2, r - 1, p.px(), 0x14FFFFFF);
	}

	private void icon(Tab t, float x, float y, int col, boolean active) {
		long now = System.currentTimeMillis();
		switch (t) {
			case GENERAL -> {
				p.ring(x + 1, y + 1, 8, 8, 4, 1.4f, col);
				p.circle(x + 5, y + 5, 1.4f, col);
			}
			case KEYBOARD -> {
				p.ring(x, y + 1.5f, 10, 7, 2, 1, col);
				for (int i = 0; i < 3; i++) p.rect(x + 2 + i * 2.2f, y + 3.5f, 1.2f, 1.2f, col);
				p.rect(x + 2.8f, y + 5.8f, 4.4f, 1.1f, col);
			}
			case MOUSE -> {
				p.ring(x + 2, y, 6, 10, 3, 1, col);
				p.rect(x + 4.6f, y + 1.5f, 0.9f, 2.5f, col);
			}
			case THEME -> {
				p.circle(x + 3, y + 3.5f, 2.3f, 0xFFFF7A9C);
				p.circle(x + 7.2f, y + 3.5f, 2.3f, 0xFF7AD8FF);
				p.circle(x + 5.1f, y + 7.2f, 2.3f, 0xFFB8FF7A);
			}
			case ANIMATION -> {
				float s = active ? 0.5f + 0.5f * (float) Math.sin(now / 200.0) : 0.5f;
				p.circle(x + 3, y + 6, 2.2f + 0.6f * s, col);
				p.circle(x + 7.5f, y + 2.5f, 1.6f + 0.6f * (1 - s), col);
				p.circle(x + 8, y + 8, 1f, col);
			}
			case SOUNDS -> {
				for (int i = 0; i < 4; i++) {
					float hh = active ? 3 + 5 * Math.abs((float) Math.sin(now / 180.0 + i * 1.3)) : 3 + i % 2 * 3 + (i == 2 ? 2 : 0);
					p.round(x + i * 2.6f, y + 10 - hh, 1.6f, hh, 0.8f, col);
				}
			}
			case PROFILE -> {
				p.circle(x + 5, y + 3, 2.5f, col);
				p.round4(x + 1, y + 6.5f, 8, 3.5f, 3, 3, 0.5f, 0.5f, col, col);
			}
		}
	}

	private void drawSidebar(float x, float y, float w, float h) {
		ui.glassPanel(x, y, w, h, 10, 0.7f);
		// logo orb + title
		long now = System.currentTimeMillis();
		float ox = x + 15, oy = y + 16;
		p.shadow(ox - 6, oy - 6, 12, 12, 6, 4, Colors.withAlpha(ui.accent, 0x90));
		p.circle(ox, oy, 6, ui.accent);
		p.circle(ox + 1.2f, oy + 1.2f, 4.6f, ui.accent2);
		p.circle(ox - 1.8f, oy - 2f, 1.8f, 0xC0FFFFFF);
		p.text("Liquid Input", x + 26, y + 10, 0xFFFFFFFF, 8, false);
		p.text("overlay • v1.0", x + 26, y + 19.5f, 0x88E0E6F0, 6);

		// tab list
		Tab[] tabs = {Tab.GENERAL, Tab.KEYBOARD, Tab.MOUSE, Tab.THEME, Tab.ANIMATION, Tab.SOUNDS};
		float ty0 = y + 36, th = 20;
		int activeIdx = -1;
		for (int i = 0; i < tabs.length; i++) if (tabs[i] == tab) activeIdx = i;
		if (activeIdx >= 0) {
			float target = ty0 + activeIdx * (th + 2);
			float cur = ui.anim("tabpill", target, 15);
			float stretch = Math.min(10, Math.abs(target - cur) * 0.6f);
			float py = cur - (target < cur ? stretch : 0);
			p.shadow(x + 5, py, w - 10, th + stretch, 7, 4, Colors.withAlpha(ui.accent, 0x60));
			p.round(x + 5, py, w - 10, th + stretch, 7, Colors.withAlpha(ui.accent, 0xE0), Colors.withAlpha(ui.accent2, 0xE0));
			p.round4(x + 5.5f, py + 0.5f, w - 11, (th + stretch) * 0.5f, 7, 7, 0, 0, 0x50FFFFFF, 0x00FFFFFF);
		}
		for (int i = 0; i < tabs.length; i++) {
			Tab t = tabs[i];
			float ty = ty0 + i * (th + 2);
			String id = "tab" + i;
			boolean hov = ui.hover(id, x + 5, ty, w - 10, th);
			float ht = ui.anim(id + "h", hov && t != tab ? 1 : 0, 14);
			if (ht > 0.01f) p.round(x + 5, ty, w - 10, th, 7, Colors.withAlpha(0xFFFFFF, Math.round(26 * ht)));
			if (ui.click(id, x + 5, ty, w - 10, th) && t != tab) selectTab(t);
			boolean on = t == tab;
			int col = on ? 0xFF0E1220 : Colors.lerp(0xCCE6EAF5, 0xFFFFFFFF, ht);
			float nudge = 2 * ht;
			icon(t, x + 12 + nudge, ty + 5, col, on || hov);
			p.text(t.title, x + 28 + nudge, ty + 6.5f, col, 8);
		}

		// profile card
		float py = y + h - 38;
		boolean on = tab == Tab.PROFILE;
		boolean hov = ui.hover("profilecard", x + 5, py, w - 10, 33);
		float ht = ui.anim("profilecardh", hov ? 1 : 0, 14);
		if (ui.click("profilecard", x + 5, py, w - 10, 33) && !on) selectTab(Tab.PROFILE);
		p.push();
		p.scaleAround(x + w / 2, py + 16, ui.squish("profilecard"));
		if (on) {
			p.shadow(x + 5, py, w - 10, 33, 9, 4, Colors.withAlpha(ui.accent, 0x60));
			p.round(x + 5, py, w - 10, 33, 9, Colors.withAlpha(ui.accent, 0xD0), Colors.withAlpha(ui.accent2, 0xD0));
		} else {
			p.round(x + 5, py, w - 10, 33, 9, Colors.withAlpha(0xFFFFFF, 28 + Math.round(22 * ht)), 0x10FFFFFF);
		}
		p.ring(x + 5, py, w - 10, 33, 9, p.px(), 0x45FFFFFF);
		float hx = x + 11, hy = py + 6.5f;
		p.shadow(hx, hy, 20, 20, 5, 3, 0x50000000);
		p.face(skin(), hx, hy, 20);
		p.ring(hx - 1, hy - 1, 22, 22, 5, p.px(), 0x80FFFFFF);
		String name = Minecraft.getInstance().getUser().getName();
		int tc = on ? 0xFF0E1220 : 0xFFFFFFFF;
		p.text(name, hx + 25, py + 9, tc, p.fit(name, 8, w - 50));
		p.text("Profile & stats ›", hx + 25, py + 19.5f, on ? 0xAA0E1220 : 0x99E0E6F0, 6);
		p.pop();
		// tiny online dot
		p.circle(hx + 19, hy + 19, 2.6f, 0xFF1E2230);
		p.circle(hx + 19, hy + 19, 1.8f, cfg().enabled ? 0xFF5CF29A : 0xFFFF6B6B);
	}

	/** Your real skin once you're in a world (remembered afterwards); the default skin before that. */
	private static PlayerSkin lastSkin;

	private PlayerSkin skin() {
		Minecraft mc = Minecraft.getInstance();
		try {
			if (mc.player != null) lastSkin = mc.player.getSkin();
			if (lastSkin != null) return lastSkin;
			return DefaultPlayerSkin.get(mc.getUser().getProfileId());
		} catch (Throwable t) {
			return DefaultPlayerSkin.getDefaultSkin();
		}
	}

	private void selectTab(Tab t) {
		tab = t;
		lastTab = t;
		tabChangedAt = System.currentTimeMillis();
		editKeys = false;
		selectedKey = null;
		pickIndex = -1;
		ui.resetScroll("list");
		SoundFx.ui(SoundFx.Ui.TAB, 1f + t.ordinal() * 0.035f);
		haptic(0.45f);
	}

	// ================================================================== content

	private void drawContent(float x, float y, float w, float h) {
		OverlayConfig c = cfg();
		float tt = Ease.outCubic((System.currentTimeMillis() - tabChangedAt) / 260f);

		// header
		p.push();
		p.alpha(tt);
		p.translate((1 - tt) * 8, 0);
		p.text(tab.title, x + 4, y + 4, 0xFFFFFFFF, 12, false);
		p.text(tab.desc, x + 4, y + 18, 0x99E0E6F0, 6);
		p.pop();

		// master switch (always visible)
		float sx = x + w - 30, sy = y + 6;
		p.text(c.enabled ? "Overlay on" : "Overlay off", sx - 4 - p.textWidth(c.enabled ? "Overlay on" : "Overlay off", 7), sy + 3, c.enabled ? 0xFFFFFFFF : 0x99FFFFFF, 7);
		boolean nv = ui.toggle("master", sx, sy, c.enabled);
		if (nv != c.enabled) {
			c.enabled = nv;
			if (nv) OverlayRenderer.restartAppear();
		}

		float top = y + 30;
		boolean preview = tab == Tab.KEYBOARD || tab == Tab.MOUSE || tab == Tab.THEME || tab == Tab.ANIMATION;
		if (preview) {
			float ph = Math.min(92, h * 0.36f);
			drawPreview(x, top, w, ph);
			top += ph + 6;
		}

		// settings list
		float lh = y + h - top;
		float scrollY = ui.beginScroll("list", x, top, w, lh);
		p.push();
		p.alpha(tt);
		p.translate(0, (1 - tt) * 10);
		rx = x;
		rw = w - 6;
		ry = top - scrollY + 2;
		float start = ry;
		switch (tab) {
			case GENERAL -> tabGeneral();
			case KEYBOARD -> tabKeyboard();
			case MOUSE -> tabMouse();
			case THEME -> tabTheme();
			case ANIMATION -> tabAnimation();
			case SOUNDS -> tabSounds();
			case PROFILE -> tabProfile();
		}
		float contentH = ry - start + 8;
		p.pop();
		ui.endScroll("list", x, top, w, lh, contentH);
	}

	// ------------------------------------------------------------------ live preview

	private void drawPreview(float x, float y, float w, float h) {
		OverlayConfig c = cfg();
		ui.glassPanel(x, y, w, h, 10, 0.55f);
		ui.pushClip(x, y, w, h);
		float pad = 8;
		boolean kb = c.keyboard.enabled || editKeys, ms = c.mouse.enabled && !editKeys;
		float[] ks = editKeys ? OverlayRenderer.keyboardSizeAll(1f) : OverlayRenderer.keyboardSize(c.keyboard);
		float[] mz = OverlayRenderer.mouseSize(c.mouse);
		float totalW = (kb ? ks[0] : 0) + (ms ? mz[0] : 0) + (kb && ms ? 12 : 0);
		float totalH = Math.max(kb ? ks[1] : 0, ms ? mz[1] : 0);
		if (totalW > 0 && totalH > 0) {
			float e = Math.min(1.3f, Math.min((w - pad * 2) / totalW, (h - pad * 2 - 6) / totalH));
			float bx = x + (w - totalW * e) / 2, by = y + (h - totalH * e) / 2 + 2;
			if (kb) {
				OverlayRenderer.EditView ev = null;
				if (editKeys) {
					KeyboardLayouts.KeyDef hov = OverlayRenderer.keyAt(bx, by, e, ui.mx, ui.my);
					String hid = hov == null ? null : hov.id();
					if (hov != null) ui.hover("key:" + hid, bx, by, ks[0] * e, ks[1] * e);
					if (hov != null && ui.click("key:" + hid, bx, by, ks[0] * e, ks[1] * e)) {
						if (hid.equals(selectedKey)) {
							if (!c.keyboard.hidden.remove(hid)) c.keyboard.hidden.add(hid);
							SoundFx.ui(c.keyboard.hidden.contains(hid) ? SoundFx.Ui.TOGGLE_OFF : SoundFx.Ui.TOGGLE_ON);
						} else {
							selectedKey = hid;
							SoundFx.ui(SoundFx.Ui.TAP, 1.2f);
						}
						haptic(0.35f);
						ConfigManager.markDirty();
					}
					ev = new OverlayRenderer.EditView(c.keyboard.hidden, selectedKey, hid, ui.accent);
				}
				OverlayRenderer.drawKeyboard(p, bx, by, e, OverlayRenderer.PHYSICAL, ev, false);
				bx += ks[0] * e + 12;
			}
			if (ms) {
				OverlayRenderer.drawMouse(p, bx, y + (h - mz[1] * e) / 2 + 2, e, OverlayRenderer.PHYSICAL, false);
			}
		} else {
			p.textCentered("Keyboard and mouse are hidden", x + w / 2, y + h / 2, 0x88FFFFFF, 8);
		}
		ui.popClip();
		String hint = editKeys ? "Click a key to select • click again to hide/show" : "Live preview • press keys to test";
		p.text(hint, x + 7, y + 4, 0x77FFFFFF, 6);
		if (PhysicalInput.blockedEvents() > 0) {
			String b = "⚠ " + PhysicalInput.blockedEvents() + " injected ignored";
			p.textRight(b, x + w - 7, y + 4, 0x99FFB86B, 6);
		}
	}

	// ------------------------------------------------------------------ row helpers

	private static final int TXT = 0xFFF2F4FA, DIM = 0x99D8DEEA;

	private void section(String title) {
		ry += 6;
		p.text(title.toUpperCase(), rx + 6, ry + 2, Colors.withAlpha(ui.accent, 0xE0), 6);
		float tw = p.textWidth(title.toUpperCase(), 6);
		p.hgrad(rx + 10 + tw, ry + 4.5f, rw - tw - 16, p.px(), 0x40FFFFFF, 0x00FFFFFF);
		ry += 12;
	}

	private float row(String id, String label, String hint) {
		float h = hint == null ? 22 : 27;
		float y = ry;
		boolean hov = ui.over(rx, y, rw, h - 2);
		float ht = ui.anim(id + "#row", hov ? 1 : 0, 12);
		if (ht > 0.01f) p.round(rx + 2, y, rw - 4, h - 2, 7, Colors.withAlpha(0xFFFFFF, Math.round(16 * ht)));
		if (hint == null) {
			p.text(label, rx + 8, y + (h - 2) / 2 - 3.5f, TXT, 8);
		} else {
			p.text(label, rx + 8, y + 5, TXT, 8);
			p.text(hint, rx + 8, y + 15.5f, DIM, 6);
		}
		ry += h;
		return y;
	}

	private float rowH(String hint) {
		return (hint == null ? 22 : 27) - 2;
	}

	private boolean tog(String id, String label, String hint, boolean v) {
		float y = row(id, label, hint);
		return ui.toggle(id, rx + rw - 32, y + rowH(hint) / 2 - 6.5f, v);
	}

	private float sld(String id, String label, String hint, float v, float min, float max, float step, String fmt) {
		float y = row(id, label, hint);
		float sw = Math.min(120, rw * 0.38f);
		float sx = rx + rw - sw - 10;
		String val = String.format(fmt, v);
		p.textRight(val, sx - 8, y + rowH(hint) / 2 - 3.5f, Colors.withAlpha(ui.accent, 0xFF), 8);
		return ui.slider(id, sx, y + rowH(hint) / 2 - 6, sw, v, min, max, step);
	}

	private int seg(String id, String label, String hint, String[] opts, int sel) {
		float y = row(id, label, hint);
		float sw = Math.min(opts.length * 46f, rw * 0.6f);
		return ui.segmented(id, rx + rw - sw - 6, y + rowH(hint) / 2 - 7, sw, 14, opts, sel);
	}

	private boolean btn(String id, String label, String hint, String text, boolean primary) {
		float y = row(id, label, hint);
		float bw = Math.max(60, p.textWidth(text, 8) + 20);
		return ui.button(id, rx + rw - bw - 6, y + rowH(hint) / 2 - 7, bw, 14, text, primary);
	}

	private void colorRow(int index, String label, int color) {
		String id = "color" + index;
		float y = row(id, label, null);
		String hex = Colors.hex(color);
		p.textRight(hex, rx + rw - 40, y + 6.5f, DIM, 7);
		if (ui.swatch(id, rx + rw - 34, y + 3, 26, 14, color)) openPicker(index);
	}

	private void keyRow(String id, String label, String hint, int key, IntConsumer set, boolean clearable) {
		float y = row(id, label, hint);
		boolean listening = id.equals(capturing) && dev.liquidinput.LiquidInputClient.isCapturing();
		if (!listening && id.equals(capturing)) capturing = null;
		int r = ui.keybind(id, rx + rw - 86, y + rowH(hint) / 2 - 7, 80, 14, KeyboardLayouts.keyName(key), listening);
		if (r == 1) {
			capturing = id;
			captureKey(k -> {
				if (k == GLFW.GLFW_KEY_ESCAPE) {
					SoundFx.ui(SoundFx.Ui.ERROR);
					capturing = null;
					return;
				}
				set.accept(k);
				SoundFx.ui(SoundFx.Ui.SUCCESS);
				capturing = null;
			});
		} else if (r == 2 && clearable) {
			set.accept(-1);
			ConfigManager.markDirty();
		}
	}

	private static <E extends Enum<E>> String[] names(E[] values, String... labels) {
		if (labels.length == values.length) return labels;
		String[] out = new String[values.length];
		for (int i = 0; i < values.length; i++) {
			String n = values[i].name().toLowerCase();
			out[i] = Character.toUpperCase(n.charAt(0)) + n.substring(1);
		}
		return out;
	}

	// ================================================================== tabs

	private void tabGeneral() {
		OverlayConfig c = cfg();
		section("Opening the GUI");
		keyRow("open", "Open key", "Default: Left Ctrl", c.openKey, k -> c.openKey = k, false);
		c.openKeyTap = tog("openTap", "Tap to open", "Only a quick tap opens it, so holding Ctrl to sprint still works", c.openKeyTap);
		keyRow("toggle", "Toggle overlay hotkey", "Right-click to clear", c.toggleKey, k -> c.toggleKey = k, true);
		c.introEveryOpen = tog("introEvery", "Play intro every time", null, c.introEveryOpen);
		if (btn("replayIntro", "Opening animation", null, "Replay intro", false)) {
			switchTo(new IntroScreen(parent));
		}

		section("Visibility");
		if (btn("editLayout", "HUD position & scale", "Drag widgets around your real screen", "Edit layout", true)) {
			SoundFx.ui(SoundFx.Ui.WHOOSH);
			switchTo(new LayoutEditorScreen(this));
		}
		c.showWithChat = tog("withChat", "Show while chat is open", null, c.showWithChat);
		c.showInScreens = tog("inScreens", "Show over menus", "Inventory, pause menu and other screens", c.showInScreens);
		c.hideInF1 = tog("f1", "Hide with F1", null, c.hideInF1);
		c.hudHighQuality = tog("hudHq", "High-res HUD rendering", "Smoothest edges. Turn off for a few extra FPS", c.hudHighQuality);

		section("Physical input only");
		boolean win = WinInputGuard.isWindows();
		String status = !win ? "Hardware-only tracking via GLFW (guard is Windows-only)"
				: WinInputGuard.isRunning() ? "Active • " + PhysicalInput.blockedEvents() + " injected events ignored"
				: "Inactive";
		boolean g = tog("guard", "Block injected input", status, c.injectedGuard);
		if (g != c.injectedGuard) {
			c.injectedGuard = g;
			WinInputGuard.setEnabled(g);
		}

		section("Interface feel");
		c.guiSounds = tog("guiSounds", "Interface sounds", null, c.guiSounds);
		c.hoverSounds = tog("hoverSounds", "Hover ticks", null, c.hoverSounds);
		c.guiVolume = sld("guiVolume", "Interface volume", null, c.guiVolume, 0, 1, 0.05f, "%.2f");
		c.haptics = sld("haptics", "Haptic feedback", "Springy nudge & squish on every press", c.haptics, 0, 1, 0.05f, "%.2f");
		c.guiBlur = tog("blur", "Frosted background blur", null, c.guiBlur);
	}

	private void tabKeyboard() {
		OverlayConfig.KeyboardSettings k = cfg().keyboard;
		section("Layout");
		k.enabled = tog("kbEnabled", "Show keyboard", null, k.enabled);
		k.lang = OverlayConfig.Lang.values()[seg("lang", "Language", null, new String[]{"QWERTY", "AZERTY", "QWERTZ", "Auto"}, k.lang.ordinal())];
		k.size = OverlayConfig.Size.values()[seg("size", "Size", null, new String[]{"WASD", "Half", "Full"}, k.size.ordinal())];
		switch (k.size) {
			case KEYSTROKES -> {
				k.keystrokesShift = tog("ksShift", "Shift & Ctrl row", null, k.keystrokesShift);
				k.keystrokesSpace = tog("ksSpace", "Space bar", null, k.keystrokesSpace);
			}
			case HALF -> k.showFRow = tog("frow", "Esc & F1-F4 row", null, k.showFRow);
			case FULL -> {
				k.showFRow = tog("frow", "Function row", null, k.showFRow);
				k.showNav = tog("nav", "Navigation cluster", "Insert / Home / Page keys", k.showNav);
				k.showNumpad = tog("numpad", "Numpad", null, k.showNumpad);
			}
		}

		section("Indicators");
		k.keyIndicators = tog("indicators", "Key indicators", "Light up keys while they are physically held", k.keyIndicators);
		k.showLabels = tog("labels", "Key labels", null, k.showLabels);
		k.labelShadow = tog("labelShadow", "Label shadow", null, k.labelShadow);
		k.showCounters = tog("counters", "Press counters", "How often each key was pressed", k.showCounters);
		k.lockLeds = tog("leds", "Caps / Num lock LEDs", null, k.lockLeds);

		section("Geometry");
		k.keyUnit = Math.round(sld("unit", "Key size", null, k.keyUnit, 14, 40, 1, "%.0fpx"));
		k.gap = Math.round(sld("gap", "Key gap", null, k.gap, 0, 8, 1, "%.0fpx"));
		k.radius = Math.round(sld("radius", "Corner radius", null, k.radius, 0, 14, 1, "%.0fpx"));
		k.scale = sld("kbScale", "Scale", null, k.scale, 0.4f, 2.5f, 0.05f, "%.2fx");
		k.opacity = sld("kbOpacity", "Opacity", null, k.opacity, 0.1f, 1f, 0.05f, "%.2f");

		section("Custom keys");
		boolean ek = tog("editKeys", "Edit keys", "Click keys in the preview to select / hide them", editKeys);
		if (ek != editKeys) {
			editKeys = ek;
			selectedKey = null;
		}
		if (editKeys) {
			String sel = selectedKey == null ? "No key selected" : "Selected: " + selectedLabel();
			float y = row("selKey", sel, selectedKey == null ? "Click a key in the preview above" : "Rebind makes this slot light up for another physical key");
			if (selectedKey != null) {
				boolean listening = "rebind".equals(capturing) && dev.liquidinput.LiquidInputClient.isCapturing();
				if (!listening && "rebind".equals(capturing)) capturing = null;
				int r = ui.keybind("rebindBtn", rx + rw - 172, y + 6, 80, 14, "Rebind", listening);
				if (r == 1) {
					capturing = "rebind";
					String slot = selectedKey;
					captureKey(key -> {
						capturing = null;
						if (key == GLFW.GLFW_KEY_ESCAPE) return;
						k.remap.put(slot, key);
						SoundFx.ui(SoundFx.Ui.SUCCESS);
					});
				}
				boolean hidden = k.hidden.contains(selectedKey);
				if (ui.button("hideBtn", rx + rw - 86, y + 6, 80, 14, hidden ? "Show key" : "Hide key", false)) {
					if (hidden) k.hidden.remove(selectedKey);
					else k.hidden.add(selectedKey);
					ConfigManager.markDirty();
				}
			}
			if (btn("resetKeys", "Reset custom keys", k.hidden.size() + " hidden • " + k.remap.size() + " rebound", "Reset", false)) {
				k.hidden.clear();
				k.remap.clear();
				selectedKey = null;
				ConfigManager.markDirty();
				showToast("Keys reset");
			}
		}
	}

	private String selectedLabel() {
		for (KeyboardLayouts.KeyDef d : KeyboardLayouts.buildAll(cfg().keyboard).keys()) {
			if (d.id().equals(selectedKey)) return d.label().isEmpty() ? "Space" : d.label();
		}
		return selectedKey;
	}

	private void tabMouse() {
		OverlayConfig.MouseSettings m = cfg().mouse;
		section("Mouse");
		m.enabled = tog("msEnabled", "Show mouse", null, m.enabled);
		m.style = OverlayConfig.MouseStyle.values()[seg("msStyle", "Style", null, new String[]{"Mouse", "Classic"}, m.style.ordinal())];
		m.showMiddle = tog("msMiddle", "Middle button", null, m.showMiddle);
		m.showScroll = tog("msScroll", "Scroll indicator", "Flashes on wheel up / down", m.showScroll);
		if (m.style == OverlayConfig.MouseStyle.SHAPE) {
			m.showSide = tog("msSide", "Side buttons", "Back / forward (mouse 4 & 5)", m.showSide);
			m.showMovement = tog("msMove", "Movement pad", "Shows where your mouse is moving", m.showMovement);
			if (m.showMovement) m.movementSensitivity = sld("msSens", "Pad sensitivity", null, m.movementSensitivity, 0.2f, 4f, 0.1f, "%.1fx");
		}
		m.cps = OverlayConfig.CpsPos.values()[seg("msCps", "CPS counter", null, new String[]{"Inside", "Below", "Off"}, m.cps.ordinal())];
		m.showCounters = tog("msCounters", "Click counters", null, m.showCounters);
		m.scale = sld("msScale", "Scale", null, m.scale, 0.4f, 2.5f, 0.05f, "%.2fx");
		m.opacity = sld("msOpacity", "Opacity", null, m.opacity, 0.1f, 1f, 0.05f, "%.2f");

		OverlayConfig.StatsWidget s = cfg().statsWidget;
		section("Stats panel");
		s.enabled = tog("stEnabled", "Show stats panel", "A small CPS / KPS readout", s.enabled);
		if (s.enabled) {
			s.showCps = tog("stCps", "Clicks per second", null, s.showCps);
			s.showKps = tog("stKps", "Keys per second", null, s.showKps);
			s.showTotals = tog("stTotals", "Lifetime totals", null, s.showTotals);
			s.scale = sld("stScale", "Scale", null, s.scale, 0.4f, 2.5f, 0.05f, "%.2fx");
		}
	}

	private void tabTheme() {
		OverlayConfig.ThemeSettings t = cfg().theme;
		section("Presets");
		int cols = rw > 300 ? 4 : 3;
		float gap = 6, cw = (rw - 12 - gap * (cols - 1)) / cols, ch = 34;
		long now = System.currentTimeMillis();
		for (int i = 0; i < ThemePresets.ALL.size(); i++) {
			ThemePresets.Preset pr = ThemePresets.ALL.get(i);
			float x = rx + 6 + (i % cols) * (cw + gap), y = ry + (i / cols) * (ch + gap);
			String id = "preset" + i;
			boolean hov = ui.hover(id, x, y, cw, ch);
			float ht = ui.anim(id + "h", hov ? 1 : 0, 14);
			boolean on = pr.name().equals(t.preset);
			if (ui.click(id, x, y, cw, ch)) {
				pr.applyTo(t);
				ConfigManager.markDirty();
				SoundFx.ui(SoundFx.Ui.SUCCESS, 0.95f + i * 0.02f);
				haptic(0.6f);
			}
			p.push();
			p.scaleAround(x + cw / 2, y + ch / 2, ui.squish(id) * (1 + 0.025f * ht));
			if (on) p.shadow(x, y, cw, ch, 8, 4, Colors.withAlpha(pr.glow(), 0xA0));
			p.round(x, y, cw, ch, 8, Colors.lerp(0xFF1A1E2A, pr.bgTop() | 0xFF000000, 0.35f), Colors.lerp(0xFF10131B, pr.bgBottom() | 0xFF000000, 0.35f));
			// mini keys: one idle, one pressed (animated)
			float kp = 0.5f + 0.5f * (float) Math.sin(now / 420.0 + i);
			float kx = x + 6, ky = y + 6, ks = 11;
			p.round(kx, ky, ks, ks, 3, pr.bgTop(), pr.bgBottom());
			p.ring(kx, ky, ks, ks, 3, p.px(), pr.border());
			p.round(kx + ks + 3, ky, ks, ks, 3, Colors.mulAlpha(pr.pressTop(), kp), Colors.mulAlpha(pr.pressBottom(), kp));
			p.ring(kx + ks + 3, ky, ks, ks, 3, p.px(), pr.border());
			p.text(pr.name(), x + 6, y + ch - 10, 0xFFFFFFFF, p.fit(pr.name(), 7, cw - 12));
			p.ring(x, y, cw, ch, 8, p.px(), on ? Colors.withAlpha(pr.glow(), 0xFF) : Colors.withAlpha(0xFFFFFF, 50 + Math.round(70 * ht)));
			p.pop();
		}
		ry += ((ThemePresets.ALL.size() + cols - 1) / cols) * (ch + gap) + 2;

		section("Colours");
		colorRow(0, "Key background (top)", t.bgTop);
		colorRow(1, "Key background (bottom)", t.bgBottom);
		colorRow(2, "Pressed (top)", t.pressTop);
		colorRow(3, "Pressed (bottom)", t.pressBottom);
		colorRow(4, "Label", t.text);
		colorRow(5, "Label when pressed", t.textPressed);
		colorRow(6, "Border", t.border);
		colorRow(7, "Glow / accent", t.glow);

		section("Glass");
		t.gradient = OverlayConfig.GradientMode.values()[seg("gradient", "Gradient", null, new String[]{"Vertical", "Horiz.", "Diagonal", "Board"}, t.gradient.ordinal())];
		t.glassOpacity = sld("glassOpacity", "Glass opacity", null, t.glassOpacity, 0.1f, 1f, 0.05f, "%.2f");
		t.highlight = tog("highlight", "Specular highlight", null, t.highlight);
		t.shadow = tog("shadow", "Soft shadows", null, t.shadow);
		t.backplate = tog("backplate", "Backplate", "Glass plate behind the keyboard", t.backplate);
		t.rainbowPressed = tog("rainbow", "Rainbow presses", null, t.rainbowPressed);
		if (t.rainbowPressed) t.rainbowSpeed = sld("rainbowSpeed", "Rainbow speed", null, t.rainbowSpeed, 0.1f, 4f, 0.1f, "%.1fx");
		t.tintGui = tog("tintGui", "Tint this GUI with the theme", null, t.tintGui);
	}

	private void tabAnimation() {
		OverlayConfig.AnimSettings a = cfg().anim;
		section("Press");
		a.press = OverlayConfig.PressAnim.values()[seg("press", "Press animation", null, new String[]{"None", "Fade", "Pop", "Ripple", "Sink"}, a.press.ordinal())];
		a.fadeMs = Math.round(sld("fade", "Release fade", null, a.fadeMs, 0, 800, 10, "%.0fms"));
		a.bounce = sld("bounce", "Bounce", null, a.bounce, 0, 1.5f, 0.05f, "%.2f");
		a.glow = tog("glow", "Glow on press", null, a.glow);
		section("Idle");
		a.idle = OverlayConfig.IdleAnim.values()[seg("idle", "Idle animation", null, new String[]{"None", "Breathe", "Rainbow", "Shimmer"}, a.idle.ordinal())];
		a.speed = sld("speed", "Speed", null, a.speed, 0.2f, 3f, 0.05f, "%.2fx");
		section("Entrance");
		a.cascadeIn = tog("cascade", "Cascade in", "Keys ripple in when you join a world", a.cascadeIn);
		if (btn("replayCascade", "Preview entrance", null, "Play", false)) OverlayRenderer.restartAppear();
	}

	private void tabSounds() {
		OverlayConfig.SoundSettings s = cfg().sound;
		section("Keys");
		s.keySounds = tog("keySounds", "Key sounds", null, s.keySounds);
		{
			p.text("Sound pack", rx + 8, ry + 5, TXT, 8);
			ry += 18;
			String[] packs = {"Thock", "Clicky", "Creamy", "Typewriter", "Bubble", "Vanilla", "Custom"};
			float cw = rw - 12;
			float hh = ui.chipsHeight(packs.length, 4, 14);
			int sel = ui.chips("keyPack", rx + 6, ry, cw, packs, s.keyPack.ordinal(), 4, 14);
			if (sel != s.keyPack.ordinal()) {
				s.keyPack = OverlayConfig.KeyPack.values()[sel];
				SoundFx.previewKey();
			}
			ry += hh + 6;
		}
		s.keyVolume = sld("keyVol", "Key volume", null, s.keyVolume, 0, 1, 0.05f, "%.2f");
		s.pitchVariation = sld("pitch", "Pitch variation", "Makes typing sound natural", s.pitchVariation, 0, 0.2f, 0.01f, "%.2f");
		s.releaseSounds = tog("release", "Release sounds", "Softer sound when a key / button comes up", s.releaseSounds);

		section("Mouse");
		s.mouseSounds = tog("mouseSounds", "Click sounds", null, s.mouseSounds);
		int mp = seg("mousePack", "Click pack", null, new String[]{"Crisp", "Soft", "Vanilla", "Custom"}, s.mousePack.ordinal());
		if (mp != s.mousePack.ordinal()) {
			s.mousePack = OverlayConfig.MousePack.values()[mp];
			SoundFx.mouse(0, true);
		}
		s.mouseVolume = sld("mouseVol", "Click volume", null, s.mouseVolume, 0, 1, 0.05f, "%.2f");
		s.muteInChat = tog("muteChat", "Mute while typing in chat", null, s.muteInChat);

		section("Custom sounds");
		int found = 0;
		for (String n : CustomSounds.NAMES) if (CustomSounds.has(n)) found++;
		if (btn("openFolder", "Sounds folder", found + " of 4 files loaded (key, key_up, mouse, mouse_up .wav)", "Open folder", false)) {
			Util.getPlatform().openPath(CustomSounds.folder());
		}
		if (btn("reloadSounds", "Reload custom sounds", null, "Reload", false)) {
			int n = CustomSounds.reload();
			showToast("Loaded " + n + " custom sound" + (n == 1 ? "" : "s"));
			SoundFx.ui(n > 0 ? SoundFx.Ui.SUCCESS : SoundFx.Ui.ERROR);
		}
		if (btn("testSounds", "Test", null, "Play key + click", true)) {
			SoundFx.key(GLFW.GLFW_KEY_A, true);
			SoundFx.mouse(0, true);
		}
	}

	private void tabProfile() {
		OverlayConfig c = cfg();
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();

		// hero card
		float hx = rx + 6, hy = ry + 2, hw = rw - 12, hh = 64;
		p.round(hx, hy, hw, hh, 12, Colors.withAlpha(ui.accent, 0x50), Colors.withAlpha(ui.accent2, 0x30));
		p.round4(hx, hy, hw, hh * 0.45f, 12, 12, 0, 0, 0x26FFFFFF, 0x00FFFFFF);
		p.ring(hx, hy, hw, hh, 12, p.px(), 0x55FFFFFF);
		float fs = 44, fx = hx + 12, fy = hy + 10;
		float pulse = 0.5f + 0.5f * (float) Math.sin(now / 600.0);
		p.shadow(fx, fy, fs, fs, 9, 6 + 3 * pulse, Colors.withAlpha(ui.accent, 0x90));
		p.face(skin(), fx, fy, fs);
		p.ring(fx - 1.5f, fy - 1.5f, fs + 3, fs + 3, 9, 1.2f, 0xCCFFFFFF);
		String name = mc.getUser().getName();
		p.text(name, fx + fs + 12, fy + 6, 0xFFFFFFFF, 16, false);
		String uuid = String.valueOf(mc.getUser().getProfileId());
		p.text(uuid.length() > 13 ? uuid.substring(0, 13) + "…" : uuid, fx + fs + 12, fy + 25, 0x99FFFFFF, 6);
		long minutes = (c.stats.playMillis + Stats.sessionMillis()) / 60000;
		p.text(c.stats.sessions + " sessions • " + (minutes / 60) + "h " + (minutes % 60) + "m with the overlay", fx + fs + 12, fy + 34, 0xCCFFFFFF, 6);
		ry += hh + 8;

		// stat cards
		section("Lifetime");
		String[][] cards = {
				{"Key presses", fmt(c.stats.totalKeys)},
				{"Left clicks", fmt(c.stats.totalLeft)},
				{"Right clicks", fmt(c.stats.totalRight)},
				{"Peak CPS (L / R)", c.stats.peakLeftCps + " / " + c.stats.peakRightCps},
				{"This session", fmt(Stats.sessionKeys()) + " keys"},
				{"Session clicks", fmt(Stats.sessionClicks())},
		};
		int cols = rw > 300 ? 3 : 2;
		float gap = 6, cw = (rw - 12 - gap * (cols - 1)) / cols, ch = 32;
		for (int i = 0; i < cards.length; i++) {
			float x = rx + 6 + (i % cols) * (cw + gap), y = ry + (i / cols) * (ch + gap);
			ui.glassPanel(x, y, cw, ch, 8, 0.8f);
			p.text(cards[i][0], x + 7, y + 6, DIM, 6);
			p.text(cards[i][1], x + 7, y + 15, 0xFFFFFFFF, 10, false);
		}
		ry += ((cards.length + cols - 1) / cols) * (ch + gap);

		section("Saved setups");
		for (int i = 0; i < 3; i++) {
			boolean has = !c.presetSlots.get(i).isEmpty();
			float y = row("slot" + i, "Slot " + (i + 1), has ? "Saved setup" : "Empty");
			if (ui.button("save" + i, rx + rw - 132, y + 4, 60, 14, "Save", false)) {
				c.presetSlots.set(i, ConfigManager.exportSetup());
				ConfigManager.save();
				showToast("Saved to slot " + (i + 1));
				SoundFx.ui(SoundFx.Ui.SUCCESS);
			}
			if (has && ui.button("load" + i, rx + rw - 66, y + 4, 60, 14, "Load", true)) {
				if (ConfigManager.importSetup(c.presetSlots.get(i))) {
					showToast("Loaded slot " + (i + 1));
					OverlayRenderer.restartAppear();
				}
			}
		}

		section("Danger zone");
		boolean confirmStats = now - confirmStatsAt < 3000;
		if (btn("resetStats", "Reset statistics", null, confirmStats ? "Click to confirm" : "Reset stats", false)) {
			if (confirmStats) {
				Stats.reset();
				showToast("Statistics reset");
				confirmStatsAt = 0;
			} else {
				confirmStatsAt = now;
				SoundFx.ui(SoundFx.Ui.ERROR);
			}
		}
		boolean confirmAll = now - confirmResetAt < 3000;
		if (btn("resetAll", "Reset all settings", "Stats and saved slots are kept", confirmAll ? "Click to confirm" : "Reset", false)) {
			if (confirmAll) {
				ConfigManager.resetAll();
				showToast("Settings reset");
				confirmResetAt = 0;
			} else {
				confirmResetAt = now;
				SoundFx.ui(SoundFx.Ui.ERROR);
			}
		}
	}

	private static String fmt(long n) {
		return String.format("%,d", n);
	}

	// ================================================================== colour picker

	private int pickGet(int i) {
		OverlayConfig.ThemeSettings t = cfg().theme;
		return switch (i) {
			case 0 -> t.bgTop;
			case 1 -> t.bgBottom;
			case 2 -> t.pressTop;
			case 3 -> t.pressBottom;
			case 4 -> t.text;
			case 5 -> t.textPressed;
			case 6 -> t.border;
			default -> t.glow;
		};
	}

	private void pickSet(int i, int v) {
		OverlayConfig.ThemeSettings t = cfg().theme;
		switch (i) {
			case 0 -> t.bgTop = v;
			case 1 -> t.bgBottom = v;
			case 2 -> t.pressTop = v;
			case 3 -> t.pressBottom = v;
			case 4 -> t.text = v;
			case 5 -> t.textPressed = v;
			case 6 -> t.border = v;
			default -> t.glow = v;
		}
		t.preset = "Custom";
		ConfigManager.markDirty();
	}

	private void openPicker(int index) {
		pickIndex = index;
		int col = pickGet(index);
		float[] hsv = Colors.toHsv(col);
		pickH = hsv[0];
		pickS = hsv[1];
		pickV = hsv[2];
		pickA = Colors.a(col);
		ui.setAnim("picker#open", 0);
		SoundFx.ui(SoundFx.Ui.WHOOSH, 1.2f);
	}

	private void drawColorPicker(float wx, float wy, float ww, float wh) {
		float ot = ui.anim("picker#open", 1, 12);
		p.layer();
		p.round(wx, wy, ww, wh, 14, Colors.withAlpha(0x05070C, Math.round(140 * ot)));

		float w = 190, h = 160;
		float x = wx + (ww - w) / 2, y = wy + (wh - h) / 2;
		p.push();
		p.alpha(ot);
		p.scaleAround(x + w / 2, y + h / 2, 0.9f + 0.1f * Ease.outBack(ot));
		ui.beginModal(x, y, w, h);

		p.shadow(x, y + 3, w, h, 12, 14, 0xAA000000);
		p.round(x, y, w, h, 12, 0xF0202534, 0xF4141820);
		p.round4(x, y, w, h * 0.35f, 12, 12, 0, 0, 0x22FFFFFF, 0x00FFFFFF);
		p.ring(x, y, w, h, 12, p.px(), 0x66FFFFFF);
		String[] names = {"Key background (top)", "Key background (bottom)", "Pressed (top)", "Pressed (bottom)", "Label", "Label when pressed", "Border", "Glow / accent"};
		p.text(names[pickIndex], x + 10, y + 8, 0xFFFFFFFF, 8);

		// saturation / value square
		float sx = x + 10, sy = y + 22, sw = 120, sh = 80;
		int cols = 60;
		for (int i = 0; i < cols; i++) {
			float s = i / (float) (cols - 1);
			p.grad(sx + sw * i / cols, sy, sw / cols + 0.5f, sh, Colors.hsv(pickH, s, 1, 255), 0xFF000000);
		}
		p.ring(sx, sy, sw, sh, 2, p.px(), 0x60FFFFFF);
		if (ui.click("sv", sx, sy, sw, sh)) ui.active = "sv";
		if ("sv".equals(ui.active) && ui.down) {
			pickS = Ease.clamp01((ui.mx - sx) / sw);
			pickV = 1 - Ease.clamp01((ui.my - sy) / sh);
			applyPick();
		}
		float kx = sx + pickS * sw, ky = sy + (1 - pickV) * sh;
		p.circle(kx, ky, 4, 0xFFFFFFFF);
		p.circle(kx, ky, 2.8f, Colors.hsv(pickH, pickS, pickV, 255));

		// hue bar
		float hx = sx, hy = sy + sh + 8, hw = sw, hh = 8;
		for (int i = 0; i < 48; i++) {
			p.rect(hx + hw * i / 48f, hy, hw / 48f + 0.5f, hh, Colors.hsv(i / 48f, 1, 1, 255));
		}
		p.ring(hx, hy, hw, hh, 2, p.px(), 0x60FFFFFF);
		if (ui.click("hue", hx, hy - 2, hw, hh + 4)) ui.active = "hue";
		if ("hue".equals(ui.active) && ui.down) {
			pickH = Ease.clamp01((ui.mx - hx) / hw) * 0.999f;
			applyPick();
			SoundFx.ui(SoundFx.Ui.SLIDE, 0.8f + pickH * 0.6f);
		}
		p.round(hx + pickH * hw - 2, hy - 1.5f, 4, hh + 3, 2, 0xFFFFFFFF);

		// alpha bar
		float ay = hy + hh + 6;
		ui.pushClip(hx, ay, hw, hh);
		ui.checker(hx, ay, hw, hh, 4);
		ui.popClip();
		p.hgrad(hx, ay, hw, hh, Colors.hsv(pickH, pickS, pickV, 0), Colors.hsv(pickH, pickS, pickV, 255));
		p.ring(hx, ay, hw, hh, 2, p.px(), 0x60FFFFFF);
		if (ui.click("alpha", hx, ay - 2, hw, hh + 4)) ui.active = "alpha";
		if ("alpha".equals(ui.active) && ui.down) {
			pickA = Math.round(Ease.clamp01((ui.mx - hx) / hw) * 255);
			applyPick();
			SoundFx.ui(SoundFx.Ui.SLIDE, 0.8f + pickA / 400f);
		}
		p.round(hx + pickA / 255f * hw - 2, ay - 1.5f, 4, hh + 3, 2, 0xFFFFFFFF);

		// preview + hex
		float px = sx + sw + 10, pw = w - (px - x) - 10;
		ui.pushClip(px, sy, pw, 40);
		ui.checker(px, sy, pw, 40, 4);
		ui.popClip();
		int cur = pickGet(pickIndex);
		p.round(px, sy, pw, 40, 6, cur);
		p.ring(px, sy, pw, 40, 6, p.px(), 0x80FFFFFF);
		p.textCentered(Colors.hex(cur), px + pw / 2, sy + 48, 0xFFE6EAF5, 7);

		// quick swatches
		int[] quick = {0xFFFFFFFF, 0xFF000000, 0xFF9EC9FF, 0xFFFF6FAE, 0xFF7CF5C8, 0xFFFFC27A, 0xFF8B6CFF, 0xFFFF4E2B};
		for (int i = 0; i < quick.length; i++) {
			float qx = px + (i % 4) * (pw / 4), qy = sy + 56 + (i / 4) * 12;
			if (ui.swatch("q" + i, qx + 1, qy, pw / 4 - 2, 10, quick[i])) {
				float[] hsv = Colors.toHsv(quick[i]);
				pickH = hsv[0];
				pickS = hsv[1];
				pickV = hsv[2];
				applyPick();
			}
		}
		boolean done = ui.button("pickDone", x + w - 70, y + h - 22, 60, 14, "Done", true);
		ui.endModal();
		p.pop();

		if (done || (ui.clickOutsideModal())) {
			if (!done) ui.clicked = false;
			pickIndex = -1;
			SoundFx.ui(SoundFx.Ui.TAP, 0.9f);
		}
	}

	private void applyPick() {
		pickSet(pickIndex, Colors.hsv(pickH, pickS, pickV, pickA));
	}

	// ================================================================== misc

	private void drawToast(float x, float y, float w, float h) {
		if (toast == null) return;
		float age = System.currentTimeMillis() - toastAt;
		if (age > 2200) {
			toast = null;
			return;
		}
		float t = Ease.outBack(Math.min(1, age / 260f)) * (age > 1900 ? 1 - (age - 1900) / 300f : 1);
		float tw = p.textWidth(toast, 8) + 24, th = 18;
		float tx = x + 124 + (w - 130 - tw) / 2, ty = y + h - 28 + (1 - t) * 10;
		p.layer();
		p.push();
		p.alpha(Math.max(0, Math.min(1, t)));
		p.shadow(tx, ty, tw, th, 9, 6, Colors.withAlpha(ui.accent, 0x70));
		p.round(tx, ty, tw, th, 9, 0xF0262B3A, 0xF01A1E28);
		p.ring(tx, ty, tw, th, 9, p.px(), Colors.withAlpha(ui.accent, 0xC0));
		p.textCentered(toast, tx + tw / 2, ty + th / 2, 0xFFFFFFFF, 8);
		p.pop();
	}

	@Override
	protected void onEscape() {
		if (pickIndex >= 0) {
			pickIndex = -1;
			SoundFx.ui(SoundFx.Ui.TAP, 0.9f);
			return;
		}
		if (editKeys) {
			editKeys = false;
			return;
		}
		onClose();
	}
}
