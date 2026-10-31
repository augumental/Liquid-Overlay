package dev.liquidinput.sound;

import java.util.concurrent.ThreadLocalRandom;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import dev.liquidinput.LiquidInputClient;
import dev.liquidinput.config.ConfigManager;
import dev.liquidinput.config.OverlayConfig;

/** Key / click sounds and the GUI's interface sounds. */
public final class SoundFx {
	private SoundFx() {}

	public enum Ui {
		TAP("ui.tap"), TOGGLE_ON("ui.toggle_on"), TOGGLE_OFF("ui.toggle_off"), HOVER("ui.hover"),
		OPEN("ui.open"), CLOSE("ui.close"), TAB("ui.tab"), SLIDE("ui.slide"), SUCCESS("ui.success"),
		ERROR("ui.error"), WHOOSH("ui.whoosh");

		final String path;

		Ui(String path) {
			this.path = path;
		}
	}

	private static long lastHover, lastSlide;

	private static Identifier mod(String path) {
		return Identifier.fromNamespaceAndPath(LiquidInputClient.MOD_ID, path);
	}

	private static Identifier vanilla(String path) {
		return Identifier.fromNamespaceAndPath("minecraft", path);
	}

	private static void play(Identifier id, float pitch, float volume) {
		if (volume <= 0.001f) return;
		try {
			Minecraft mc = Minecraft.getInstance();
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id),
					Math.max(0.5f, Math.min(2f, pitch)), Math.min(1f, volume)));
		} catch (Throwable t) {
			// sound engine not ready yet; ignore
		}
	}

	private static float vary(float amount) {
		return 1f + (ThreadLocalRandom.current().nextFloat() * 2 - 1) * amount;
	}

	// ------------------------------------------------------------------ GUI

	public static void ui(Ui s) {
		ui(s, 1f);
	}

	public static void ui(Ui s, float pitch) {
		OverlayConfig c = ConfigManager.get();
		if (!c.guiSounds) return;
		long now = System.currentTimeMillis();
		if (s == Ui.HOVER) {
			if (!c.hoverSounds || now - lastHover < 45) return;
			lastHover = now;
		}
		if (s == Ui.SLIDE) {
			if (now - lastSlide < 28) return;
			lastSlide = now;
		}
		float vol = switch (s) {
			case HOVER -> 0.18f;
			case SLIDE -> 0.35f;
			case OPEN, CLOSE, WHOOSH -> 0.7f;
			default -> 0.6f;
		};
		play(mod(s.path), pitch * vary(0.015f), vol * c.guiVolume);
	}

	// ------------------------------------------------------------------ keys & mouse

	private static boolean shouldPlay() {
		OverlayConfig c = ConfigManager.get();
		if (!c.enabled) return false;
		Minecraft mc = Minecraft.getInstance();
		return !(c.sound.muteInChat && mc.screen instanceof ChatScreen);
	}

	private static float keyPitchFor(int key) {
		return switch (key) {
			case GLFW.GLFW_KEY_SPACE -> 0.86f;
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT,
				 GLFW.GLFW_KEY_CAPS_LOCK, GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_KP_ENTER -> 0.93f;
			default -> 1f;
		};
	}

	public static void key(int key, boolean down) {
		OverlayConfig.SoundSettings s = ConfigManager.get().sound;
		if (!s.keySounds || !shouldPlay()) return;
		if (!down && !s.releaseSounds) return;
		float pitch = keyPitchFor(key) * vary(s.pitchVariation);
		float vol = s.keyVolume * (down ? 1f : 0.55f);
		switch (s.keyPack) {
			case VANILLA -> play(vanilla("ui.button.click"), (down ? 1.6f : 1.9f) * pitch, vol * 0.5f);
			case CUSTOM -> CustomSounds.play(down ? "key" : "key_up", vol);
			default -> {
				String base = "key." + s.keyPack.name().toLowerCase();
				play(mod(down ? base : base + "_up"), pitch, vol);
			}
		}
	}

	public static void mouse(int button, boolean down) {
		OverlayConfig.SoundSettings s = ConfigManager.get().sound;
		if (!s.mouseSounds || !shouldPlay()) return;
		if (!down && !s.releaseSounds) return;
		float pitch = (button == 1 ? 0.94f : 1f) * vary(s.pitchVariation * 0.6f);
		float vol = s.mouseVolume * (down ? 1f : 0.6f);
		switch (s.mousePack) {
			case VANILLA -> play(vanilla(down ? "block.stone_button.click_on" : "block.stone_button.click_off"), pitch * 1.4f, vol * 0.35f);
			case CUSTOM -> CustomSounds.play(down ? "mouse" : "mouse_up", vol);
			default -> {
				String base = "mouse." + s.mousePack.name().toLowerCase();
				play(mod(down ? base : base + "_up"), pitch, vol);
			}
		}
	}

	/** Preview a key pack from the settings screen. */
	public static void previewKey() {
		key(GLFW.GLFW_KEY_A, true);
	}
}
