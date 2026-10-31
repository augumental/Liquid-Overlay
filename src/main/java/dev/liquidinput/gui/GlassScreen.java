package dev.liquidinput.gui;

import java.util.concurrent.ThreadLocalRandom;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import dev.liquidinput.LiquidInputClient;
import dev.liquidinput.config.ConfigManager;
import dev.liquidinput.config.OverlayConfig;
import dev.liquidinput.input.PhysicalInput;
import dev.liquidinput.render.Colors;
import dev.liquidinput.render.Ease;
import dev.liquidinput.render.Painter;
import dev.liquidinput.sound.SoundFx;

/** Base for all of the mod's screens: frosted background, open/close animation, haptics, input plumbing. */
public abstract class GlassScreen extends Screen {
	protected final Screen parent;
	protected final Ui ui = new Ui(this);

	protected long openedAt;
	private long closingAt;
	private Screen closeTarget;
	private long lastNanos;

	// haptic spring (window nudge)
	protected float nudgeX, nudgeY, nudgeVX, nudgeVY, thump;

	private boolean swallowKey;

	protected GlassScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (openedAt == 0) {
			openedAt = System.currentTimeMillis();
			onOpened();
		}
	}

	protected void onOpened() {
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** 0 -> 1 over the opening animation. */
	protected float openT(float durationMs) {
		return Ease.clamp01((System.currentTimeMillis() - openedAt) / durationMs);
	}

	/** 0 -> 1 while closing. */
	protected float closeT() {
		return closingAt == 0 ? 0 : Ease.clamp01((System.currentTimeMillis() - closingAt) / 200f);
	}

	protected boolean closing() {
		return closingAt != 0;
	}

	public void closeTo(Screen target, boolean sound) {
		if (closingAt != 0) return;
		closingAt = System.currentTimeMillis();
		closeTarget = target;
		if (target instanceof GlassScreen gs) gs.openedAt = 0;   // replay its entrance
		if (sound) SoundFx.ui(SoundFx.Ui.CLOSE);
		ConfigManager.save();
	}

	/** Switch to another of our screens without the close animation. */
	protected void switchTo(Screen target) {
		ConfigManager.save();
		if (target instanceof GlassScreen gs) gs.openedAt = 0;
		minecraft.setScreen(target);
	}

	@Override
	public void onClose() {
		closeTo(parent, true);
	}

	@Override
	public void removed() {
		ConfigManager.save();
		if (LiquidInputClient.isCapturing()) LiquidInputClient.captureNextKey(null);
	}

	public void haptic(float strength) {
		float h = ConfigManager.get().haptics;
		if (h <= 0) return;
		float s = strength * h;
		ThreadLocalRandom r = ThreadLocalRandom.current();
		nudgeVY += 42f * s;
		nudgeVX += (r.nextFloat() - 0.5f) * 30f * s;
		thump = Math.min(1, thump + 0.6f * s);
	}

	protected int accent() {
		OverlayConfig c = ConfigManager.get();
		return c.theme.tintGui ? Colors.withAlpha(c.theme.glow, 255) : 0xFF8FB8FF;
	}

	protected int accent2() {
		OverlayConfig c = ConfigManager.get();
		if (!c.theme.tintGui) return 0xFFB89CFF;
		int a = Colors.withAlpha(c.theme.pressBottom, 255);
		// keep the two accents visibly different
		return Math.abs(Colors.luminance(a) - Colors.luminance(c.theme.glow)) < 0.02f ? Colors.darken(a, 0.25f) : a;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		OverlayConfig c = ConfigManager.get();
		if (minecraft.level == null || c.guiBlur) super.renderBackground(g, mouseX, mouseY, delta);
		float t = Ease.outCubic(openT(350)) * (1 - closeT());
		int top = Colors.mulAlpha(Colors.lerp(0x60101420, Colors.withAlpha(accent(), 0x60), 0.18f), t);
		int bot = Colors.mulAlpha(0x80080A10, t);
		g.fillGradient(0, 0, width, height, top, bot);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.1f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		PhysicalInput.frame();

		// spring for the haptic nudge
		float k = 260, damp = 18;
		nudgeVX += (-k * nudgeX - damp * nudgeVX) * dt;
		nudgeVY += (-k * nudgeY - damp * nudgeVY) * dt;
		nudgeX += nudgeVX * dt;
		nudgeY += nudgeVY * dt;
		thump = Math.max(0, thump - dt * 5);

		ui.accent = accent();
		ui.accent2 = accent2();

		Painter p = Painter.begin(g);
		ui.beginFrame(p, mouseX, mouseY, dt);
		try {
			draw(p, dt);
		} finally {
			ui.endFrame();
			p.end();
		}

		if (closingAt != 0 && closeT() >= 1) {
			Screen target = closeTarget;
			closingAt = 0;
			minecraft.setScreen(target);
		}
	}

	protected abstract void draw(Painter p, float dt);

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing()) return true;
		ui.mx = (float) event.x();
		ui.my = (float) event.y();
		ui.press(event.button());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		ui.release(event.button());
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		ui.addScroll(vertical);
		return true;
	}

	/** Start capturing the next physical key for a binding. */
	protected void captureKey(java.util.function.IntConsumer then) {
		LiquidInputClient.captureNextKey(key -> {
			swallowKey = true;
			then.accept(key);
			ConfigManager.markDirty();
		});
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (swallowKey) {
			swallowKey = false;
			return true;
		}
		if (LiquidInputClient.isCapturing()) return true;
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			onEscape();
			return true;
		}
		return onKey(event.key());
	}

	protected void onEscape() {
		onClose();
	}

	protected boolean onKey(int key) {
		return false;
	}
}
