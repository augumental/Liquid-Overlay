package dev.liquidinput;

import java.util.function.IntConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import dev.liquidinput.config.ConfigManager;
import dev.liquidinput.config.OverlayConfig;
import dev.liquidinput.gui.GlassScreen;
import dev.liquidinput.gui.IntroScreen;
import dev.liquidinput.gui.MainScreen;
import dev.liquidinput.input.PhysicalInput;
import dev.liquidinput.input.Stats;
import dev.liquidinput.input.WinInputGuard;
import dev.liquidinput.render.OverlayRenderer;
import dev.liquidinput.sound.SoundFx;

public class LiquidInputClient implements ClientModInitializer {
	public static final String MOD_ID = "liquidinput";
	public static final Logger LOGGER = LoggerFactory.getLogger("LiquidInput");

	/** When set, the next physical key press is delivered here instead (GUI key binding capture). */
	private static IntConsumer keyCapture;

	private static boolean openRequested;
	private static long openKeyDownAt;
	private static boolean openKeyClean;
	private static ClientLevel lastLevel;
	private static int saveTicker;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static void captureNextKey(IntConsumer consumer) {
		keyCapture = consumer;
	}

	public static boolean isCapturing() {
		return keyCapture != null;
	}

	@Override
	public void onInitializeClient() {
		ConfigManager.load();
		ConfigManager.get().stats.sessions++;

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("overlay"), (graphics, tickCounter) -> {
			PhysicalInput.frame();
			Minecraft mc = Minecraft.getInstance();
			OverlayConfig c = ConfigManager.get();
			if (mc.screen instanceof GlassScreen) return;            // our GUI draws its own live preview
			if (!c.showWithChat && mc.screen instanceof ChatScreen) return;
			OverlayRenderer.renderHud(graphics);
		});

		// Optionally draw on top of other menus (inventory, pause menu...).
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof GlassScreen || screen instanceof ChatScreen) return;
			ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) -> {
				if (!ConfigManager.get().showInScreens) return;
				PhysicalInput.frame();
				graphics.nextStratum();
				OverlayRenderer.renderHud(graphics);
			});
		});

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			PhysicalInput.install(client);
			if (ConfigManager.get().injectedGuard) WinInputGuard.start();
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ConfigManager.get().stats.playMillis += Stats.sessionMillis();
			ConfigManager.save();
			WinInputGuard.stop();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (openRequested) {
				openRequested = false;
				if (client.screen == null) openGui(client);
			}
			if (client.level != lastLevel) {
				lastLevel = client.level;
				if (client.level != null) OverlayRenderer.restartAppear();
			}
			// stats autosave every minute; setting changes at most every 2 s (and always when a GUI closes)
			if (++saveTicker % (20 * 60) == 0) ConfigManager.save();
			else if (saveTicker % 40 == 0) ConfigManager.saveIfDirty();
		});

		PhysicalInput.setListener(new PhysicalInput.Listener() {
			@Override
			public void onKey(int key, int scancode, boolean down) {
				handleKey(key, down);
			}

			@Override
			public void onMouse(int button, boolean down) {
				if (down) openKeyClean = false;
				SoundFx.mouse(button, down);
			}
		});

		LOGGER.info("[LiquidInput] Ready. Tap Left Ctrl in-game to open the overlay settings.");
	}

	private static void handleKey(int key, boolean down) {
		Minecraft mc = Minecraft.getInstance();
		OverlayConfig c = ConfigManager.get();

		if (down && keyCapture != null) {
			IntConsumer cb = keyCapture;
			keyCapture = null;
			cb.accept(key);
			return;
		}

		SoundFx.key(key, down);

		// --- open-GUI key (tap or press) ---
		if (key == c.openKey) {
			if (down) {
				openKeyDownAt = System.currentTimeMillis();
				openKeyClean = !PhysicalInput.anyOtherKeyDown(key);
				if (!c.openKeyTap && mc.screen == null) openRequested = true;
			} else if (c.openKeyTap && openKeyClean && System.currentTimeMillis() - openKeyDownAt < 300) {
				if (mc.screen == null) openRequested = true;
				else if (mc.screen instanceof GlassScreen gs) gs.onClose();   // tap again to close
			}
		} else if (down) {
			openKeyClean = false;
		}

		// --- toggle-overlay hotkey ---
		if (down && c.toggleKey >= 0 && key == c.toggleKey && mc.screen == null) {
			c.enabled = !c.enabled;
			ConfigManager.markDirty();
			SoundFx.ui(c.enabled ? SoundFx.Ui.TOGGLE_ON : SoundFx.Ui.TOGGLE_OFF);
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.literal(c.enabled ? "§bInput Overlay §aON" : "§bInput Overlay §cOFF"), true);
			}
		}
	}

	public static void openGui(Minecraft mc) {
		OverlayConfig c = ConfigManager.get();
		if (!c.introSeen || c.introEveryOpen) mc.setScreen(new IntroScreen(mc.screen));
		else mc.setScreen(new MainScreen(mc.screen));
	}
}
