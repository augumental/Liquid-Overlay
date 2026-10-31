package dev.liquidinput.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.liquidinput.config.ConfigManager;
import dev.liquidinput.config.OverlayConfig;
import dev.liquidinput.render.Colors;
import dev.liquidinput.render.Ease;
import dev.liquidinput.render.OverlayRenderer;
import dev.liquidinput.render.Painter;
import dev.liquidinput.sound.SoundFx;

/** Drag the HUD widgets around the real screen, scroll to resize, with smart snapping guides. */
public class LayoutEditorScreen extends GlassScreen {
	private static final String[] NAMES = {"Keyboard", "Mouse", "Stats"};
	private int dragging = -1;
	private float offX, offY;
	private boolean snap = true;
	private final List<float[]> guides = new ArrayList<>();
	private int lastSnapX = Integer.MIN_VALUE, lastSnapY = Integer.MIN_VALUE;

	public LayoutEditorScreen(Screen parent) {
		super(Component.literal("Edit layout"), parent);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		float t = Ease.outCubic(openT(300)) * (1 - closeT());
		g.fillGradient(0, 0, width, height, Colors.mulAlpha(0x50000000, t), Colors.mulAlpha(0x70000000, t));
	}

	private OverlayConfig.Widget widget(int i) {
		OverlayConfig c = ConfigManager.get();
		return switch (i) {
			case 0 -> c.keyboard;
			case 1 -> c.mouse;
			default -> c.statsWidget;
		};
	}

	private float[] size(int i) {
		OverlayConfig c = ConfigManager.get();
		return switch (i) {
			case 0 -> OverlayRenderer.keyboardSize(c.keyboard);
			case 1 -> OverlayRenderer.mouseSize(c.mouse);
			default -> OverlayRenderer.statsSize(c.statsWidget);
		};
	}

	@Override
	protected void draw(Painter p, float dt) {
		float t = Ease.outCubic(openT(300)) * (1 - closeT());
		p.push();
		p.alpha(t);

		// faint grid
		for (int gx = 0; gx < width; gx += 20) p.rect(gx, 0, p.px(), height, 0x0CFFFFFF);
		for (int gy = 0; gy < height; gy += 20) p.rect(0, gy, width, p.px(), 0x0CFFFFFF);
		p.rect(width / 2f, 0, p.px(), height, 0x18FFFFFF);
		p.rect(0, height / 2f, width, p.px(), 0x18FFFFFF);

		guides.clear();
		int hoveredIdx = -1;
		for (int i = 2; i >= 0; i--) {
			float[] s = size(i);
			OverlayConfig.Widget w = widget(i);
			float x = w.x * (width - s[0]), y = w.y * (height - s[1]);
			if (ui.over(x, y, s[0], s[1]) && hoveredIdx < 0 && dragging < 0) hoveredIdx = i;
		}

		// start drag / right-click toggle / scroll scale
		if (hoveredIdx >= 0) {
			OverlayConfig.Widget w = widget(hoveredIdx);
			float[] s = size(hoveredIdx);
			float x = w.x * (width - s[0]), y = w.y * (height - s[1]);
			ui.hover("w" + hoveredIdx, x, y, s[0], s[1]);
			if (ui.click("w" + hoveredIdx, x, y, s[0], s[1])) {
				dragging = hoveredIdx;
				offX = ui.mx - x;
				offY = ui.my - y;
				SoundFx.ui(SoundFx.Ui.TAP, 1.15f);
				haptic(0.4f);
			} else if (ui.rightClick(x, y, s[0], s[1])) {
				w.enabled = !w.enabled;
				SoundFx.ui(w.enabled ? SoundFx.Ui.TOGGLE_ON : SoundFx.Ui.TOGGLE_OFF);
				haptic(0.5f);
				ConfigManager.markDirty();
			} else if (ui.scroll != 0) {
				w.scale = Math.max(0.3f, Math.min(3f, Math.round((w.scale + Math.signum(ui.scroll) * 0.05f) * 100) / 100f));
				ui.scroll = 0;
				SoundFx.ui(SoundFx.Ui.SLIDE, 0.7f + w.scale * 0.3f);
				ConfigManager.markDirty();
			}
		}
		if (dragging >= 0) {
			if (!ui.down) {
				dragging = -1;
				SoundFx.ui(SoundFx.Ui.TAP, 0.95f);
				ConfigManager.markDirty();
			} else {
				drag(dragging);
			}
		}

		// draw widgets
		for (int i = 0; i < 3; i++) {
			float[] s = size(i);
			OverlayConfig.Widget w = widget(i);
			float x = w.x * (width - s[0]), y = w.y * (height - s[1]);
			boolean hot = i == dragging || i == hoveredIdx;
			float ht = ui.anim("hot" + i, hot ? 1 : 0, 14);
			p.push();
			if (!w.enabled) p.alpha(0.3f);
			switch (i) {
				case 0 -> OverlayRenderer.drawKeyboard(p, x, y, 1f, OverlayRenderer.PHYSICAL, null, false);
				case 1 -> OverlayRenderer.drawMouse(p, x, y, 1f, OverlayRenderer.PHYSICAL, false);
				default -> OverlayRenderer.drawStats(p, x, y, 1f, OverlayRenderer.PHYSICAL);
			}
			p.pop();
			int col = Colors.lerp(0x50FFFFFF, Colors.withAlpha(ui.accent, 0xFF), ht);
			dashed(p, x - 3, y - 3, s[0] + 6, s[1] + 6, col);
			// name tag
			String tag = NAMES[i] + (w.enabled ? "" : " (hidden)") + "  " + Math.round(w.scale * 100) + "%";
			float tw = p.textWidth(tag, 6) + 10;
			float ty = y - 14 < 2 ? y + s[1] + 5 : y - 14;
			p.round(x - 3, ty, tw, 10, 5, Colors.lerp(0xA0202432, Colors.withAlpha(ui.accent, 0xE0), ht));
			p.text(tag, x + 2, ty + 2.5f, ht > 0.5f ? 0xFF0E1220 : 0xFFFFFFFF, 6);
		}

		for (float[] gl : guides) {
			if (gl[0] == 0) p.rect(gl[1], 0, p.px(), height, Colors.withAlpha(ui.accent, 0xC0));
			else p.rect(0, gl[1], width, p.px(), Colors.withAlpha(ui.accent, 0xC0));
		}

		drawToolbar(p);
		p.pop();
	}

	private void drag(int i) {
		OverlayConfig.Widget w = widget(i);
		float[] s = size(i);
		float x = ui.mx - offX, y = ui.my - offY;
		if (snap) {
			float th = 6;
			List<Float> cx = new ArrayList<>(List.of(4f, (width - s[0]) / 2f, width - s[0] - 4f));
			List<Float> cy = new ArrayList<>(List.of(4f, (height - s[1]) / 2f, height - s[1] - 4f));
			for (int j = 0; j < 3; j++) {
				if (j == i || !widget(j).enabled) continue;
				float[] os = size(j);
				OverlayConfig.Widget o = widget(j);
				float ox = o.x * (width - os[0]), oy = o.y * (height - os[1]);
				cx.add(ox);
				cx.add(ox + os[0] - s[0]);
				cx.add(ox + os[0] + 4);
				cx.add(ox - s[0] - 4);
				cy.add(oy);
				cy.add(oy + os[1] - s[1]);
				cy.add(oy + os[1] + 4);
				cy.add(oy - s[1] - 4);
			}
			int snappedX = Integer.MIN_VALUE, snappedY = Integer.MIN_VALUE;
			for (float c : cx) {
				if (Math.abs(x - c) < th) {
					x = c;
					snappedX = Math.round(c);
					guides.add(new float[]{0, c == (width - s[0]) / 2f ? width / 2f : (c < width / 2f ? c : c + s[0])});
					break;
				}
			}
			for (float c : cy) {
				if (Math.abs(y - c) < th) {
					y = c;
					snappedY = Math.round(c);
					guides.add(new float[]{1, c == (height - s[1]) / 2f ? height / 2f : (c < height / 2f ? c : c + s[1])});
					break;
				}
			}
			if ((snappedX != Integer.MIN_VALUE && snappedX != lastSnapX) || (snappedY != Integer.MIN_VALUE && snappedY != lastSnapY)) {
				SoundFx.ui(SoundFx.Ui.SLIDE, 1.3f);
				haptic(0.15f);
			}
			lastSnapX = snappedX;
			lastSnapY = snappedY;
		}
		w.x = Ease.clamp01(width - s[0] <= 0 ? 0 : x / (width - s[0]));
		w.y = Ease.clamp01(height - s[1] <= 0 ? 0 : y / (height - s[1]));
	}

	private void dashed(Painter p, float x, float y, float w, float h, int col) {
		float dash = 4, gap = 3;
		float off = (System.currentTimeMillis() / 60f) % (dash + gap);
		float th = Math.max(p.px(), 0.6f);
		for (float i = -off; i < w; i += dash + gap) {
			float a = Math.max(0, i), b = Math.min(w, i + dash);
			if (b > a) {
				p.rect(x + a, y, b - a, th, col);
				p.rect(x + w - b, y + h - th, b - a, th, col);
			}
		}
		for (float i = -off; i < h; i += dash + gap) {
			float a = Math.max(0, i), b = Math.min(h, i + dash);
			if (b > a) {
				p.rect(x + w - th, y + a, th, b - a, col);
				p.rect(x, y + h - b, th, b - a, col);
			}
		}
	}

	private void drawToolbar(Painter p) {
		float w = 300, h = 44;
		float x = (width - w) / 2f, y = height - h - 10;
		// move the toolbar out of the way when dragging near it
		if (dragging >= 0 && ui.my > y - 30) y = 10;
		float ty = ui.anim("toolbarY", y, 10);
		// fade the toolbar a little when it covers a widget, unless you're pointing at it
		boolean covers = false;
		for (int i = 0; i < 3; i++) {
			float[] s = size(i);
			OverlayConfig.Widget wd = widget(i);
			float wx = wd.x * (width - s[0]), wy = wd.y * (height - s[1]);
			if (wd.enabled && wx < x + w && wx + s[0] > x && wy < ty + h && wy + s[1] > ty) covers = true;
		}
		float fade = ui.anim("toolbarFade", covers && !ui.over(x, ty, w, h) ? 0.55f : 1f, 8);
		p.push();
		p.alpha(fade);
		p.shadow(x, ty + 3, w, h, 14, 12, 0x90000000);
		p.round(x, ty, w, h, 14, 0xD81C2130, 0xE8101420);
		p.round4(x, ty, w, h * 0.45f, 14, 14, 0, 0, 0x22FFFFFF, 0x00FFFFFF);
		p.ring(x, ty, w, h, 14, p.px(), 0x66FFFFFF);
		p.textCentered("Drag to move • scroll to resize • right-click to hide", x + w / 2, ty + 9, 0xAAE6EAF5, 6);
		float by = ty + 20, bh = 15;
		if (ui.button("done", x + w - 70, by, 60, bh, "Done", true)) closeTo(parent, false);
		if (ui.button("reset", x + 10, by, 60, bh, "Reset", false)) {
			OverlayConfig d = new OverlayConfig();
			OverlayConfig c = ConfigManager.get();
			c.keyboard.x = d.keyboard.x;
			c.keyboard.y = d.keyboard.y;
			c.keyboard.scale = 1;
			c.mouse.x = d.mouse.x;
			c.mouse.y = d.mouse.y;
			c.mouse.scale = 1;
			c.statsWidget.x = d.statsWidget.x;
			c.statsWidget.y = d.statsWidget.y;
			c.statsWidget.scale = 1;
			ConfigManager.markDirty();
		}
		p.text("Snap", x + 84, by + 4, 0xFFFFFFFF, 8);
		snap = ui.toggle("snap", x + 110, by + 1, snap);
		if (ui.button("centerBtn", x + 146, by, 74, bh, "Center keys", false)) {
			OverlayConfig c = ConfigManager.get();
			c.keyboard.x = 0.5f;
			c.keyboard.y = 0.5f;
			ConfigManager.markDirty();
		}
		p.pop();
	}
}
