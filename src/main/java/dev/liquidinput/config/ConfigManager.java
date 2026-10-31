package dev.liquidinput.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;

import dev.liquidinput.LiquidInputClient;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static OverlayConfig config = new OverlayConfig();
	private static boolean dirty;

	private ConfigManager() {}

	public static OverlayConfig get() {
		return config;
	}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("liquidinput.json");
	}

	public static Path dataDir() {
		Path p = FabricLoader.getInstance().getConfigDir().resolve("liquidinput");
		try {
			Files.createDirectories(p);
		} catch (IOException ignored) {
		}
		return p;
	}

	public static void load() {
		Path f = file();
		if (Files.exists(f)) {
			try {
				OverlayConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), OverlayConfig.class);
				if (c != null) config = sanitize(c);
			} catch (Exception e) {
				LiquidInputClient.LOGGER.error("[LiquidInput] Could not read config, using defaults", e);
			}
		}
	}

	private static OverlayConfig sanitize(OverlayConfig c) {
		OverlayConfig d = new OverlayConfig();
		if (c.keyboard == null) c.keyboard = d.keyboard;
		if (c.mouse == null) c.mouse = d.mouse;
		if (c.statsWidget == null) c.statsWidget = d.statsWidget;
		if (c.theme == null) c.theme = d.theme;
		if (c.anim == null) c.anim = d.anim;
		if (c.sound == null) c.sound = d.sound;
		if (c.stats == null) c.stats = d.stats;
		if (c.keyboard.hidden == null) c.keyboard.hidden = d.keyboard.hidden;
		if (c.keyboard.remap == null) c.keyboard.remap = d.keyboard.remap;
		if (c.keyboard.lang == null) c.keyboard.lang = OverlayConfig.Lang.QWERTY;
		if (c.keyboard.size == null) c.keyboard.size = OverlayConfig.Size.KEYSTROKES;
		if (c.mouse.style == null) c.mouse.style = OverlayConfig.MouseStyle.SHAPE;
		if (c.mouse.cps == null) c.mouse.cps = OverlayConfig.CpsPos.INSIDE;
		if (c.theme.gradient == null) c.theme.gradient = OverlayConfig.GradientMode.VERTICAL;
		if (c.anim.press == null) c.anim.press = OverlayConfig.PressAnim.POP;
		if (c.anim.idle == null) c.anim.idle = OverlayConfig.IdleAnim.NONE;
		if (c.sound.keyPack == null) c.sound.keyPack = OverlayConfig.KeyPack.THOCK;
		if (c.sound.mousePack == null) c.sound.mousePack = OverlayConfig.MousePack.CRISP;
		while (c.presetSlots == null || c.presetSlots.size() < 3) {
			if (c.presetSlots == null) c.presetSlots = d.presetSlots;
			else c.presetSlots.add("");
		}
		return c;
	}

	public static void markDirty() {
		dirty = true;
	}

	public static void saveIfDirty() {
		if (dirty) save();
	}

	public static void save() {
		dirty = false;
		try {
			Files.writeString(file(), GSON.toJson(config), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LiquidInputClient.LOGGER.error("[LiquidInput] Could not save config", e);
		}
	}

	/** Snapshot of the visual setup (no stats, no slots) as JSON. */
	public static String exportSetup() {
		JsonObject o = GSON.toJsonTree(config).getAsJsonObject();
		o.remove("stats");
		o.remove("presetSlots");
		o.remove("introSeen");
		return GSON.toJson(o);
	}

	public static boolean importSetup(String json) {
		if (json == null || json.isBlank()) return false;
		try {
			OverlayConfig in = GSON.fromJson(json, OverlayConfig.class);
			if (in == null) return false;
			in.stats = config.stats;
			in.presetSlots = config.presetSlots;
			in.introSeen = true;
			config = sanitize(in);
			save();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static void resetAll() {
		OverlayConfig fresh = new OverlayConfig();
		fresh.stats = config.stats;
		fresh.presetSlots = config.presetSlots;
		fresh.introSeen = true;
		config = fresh;
		save();
	}
}
