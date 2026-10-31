package dev.liquidinput.sound;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

import dev.liquidinput.LiquidInputClient;
import dev.liquidinput.config.ConfigManager;

/**
 * Your own sounds: drop WAV files into {@code .minecraft/config/liquidinput/sounds/} named
 * key.wav, key_up.wav, mouse.wav, mouse_up.wav, then pick "Custom" in the Sounds tab.
 * Played with a small pool of Java Sound clips so rapid typing never cuts itself off.
 */
public final class CustomSounds {
	public static final String[] NAMES = {"key", "key_up", "mouse", "mouse_up"};
	private static final int POOL = 6;
	private static final Map<String, Clip[]> pools = new HashMap<>();
	private static final Map<String, Integer> next = new HashMap<>();
	private static boolean loaded;

	private CustomSounds() {}

	public static Path folder() {
		Path p = ConfigManager.dataDir().resolve("sounds");
		try {
			Files.createDirectories(p);
			Path readme = p.resolve("README.txt");
			if (!Files.exists(readme)) {
				Files.writeString(readme, """
						Liquid Input Overlay - custom sounds
						-----------------------------------
						Put WAV files here (16-bit PCM works best) and choose "Custom" in the Sounds tab:
						  key.wav       played when a key goes down
						  key_up.wav    played when a key is released (optional)
						  mouse.wav     played on a mouse click
						  mouse_up.wav  played on mouse release (optional)
						Then press "Reload sounds" in the GUI.
						""");
			}
		} catch (Exception ignored) {
		}
		return p;
	}

	public static synchronized int reload() {
		for (Clip[] arr : pools.values()) {
			for (Clip c : arr) {
				try {
					c.close();
				} catch (Exception ignored) {
				}
			}
		}
		pools.clear();
		int found = 0;
		Path dir = folder();
		for (String n : NAMES) {
			Path f = dir.resolve(n + ".wav");
			if (!Files.isRegularFile(f)) continue;
			try {
				byte[] data;
				AudioFormat fmt;
				try (InputStream in = new BufferedInputStream(Files.newInputStream(f));
					 AudioInputStream ais = AudioSystem.getAudioInputStream(in)) {
					AudioFormat src = ais.getFormat();
					fmt = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16,
							src.getChannels(), src.getChannels() * 2, src.getSampleRate(), false);
					try (AudioInputStream conv = AudioSystem.getAudioInputStream(fmt, ais)) {
						data = conv.readAllBytes();
					}
				}
				Clip[] arr = new Clip[POOL];
				for (int i = 0; i < POOL; i++) {
					Clip clip = AudioSystem.getClip();
					clip.open(fmt, data, 0, data.length);
					arr[i] = clip;
				}
				pools.put(n, arr);
				found++;
			} catch (Exception e) {
				LiquidInputClient.LOGGER.warn("[LiquidInput] Could not load custom sound {}: {}", f, e.toString());
			}
		}
		loaded = true;
		return found;
	}

	public static synchronized boolean has(String name) {
		if (!loaded) reload();
		return pools.containsKey(name);
	}

	public static synchronized void play(String name, float volume) {
		if (!loaded) reload();
		Clip[] arr = pools.get(name);
		if (arr == null) return;
		int i = next.getOrDefault(name, 0);
		next.put(name, (i + 1) % arr.length);
		Clip clip = arr[i];
		try {
			clip.stop();
			clip.setFramePosition(0);
			if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
				FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
				float db = (float) (20 * Math.log10(Math.max(0.0001, volume)));
				gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
			}
			clip.start();
		} catch (Exception ignored) {
		}
	}
}
