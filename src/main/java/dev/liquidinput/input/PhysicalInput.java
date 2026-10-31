package dev.liquidinput.input;

import java.util.ArrayDeque;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

import net.minecraft.client.Minecraft;

import dev.liquidinput.LiquidInputClient;

/**
 * Tracks genuine, physical input only.
 *
 * <p>Instead of reading Minecraft's KeyMapping state (which any mod or macro can flip with
 * {@code setDown()} / {@code click()}), we sit directly on the GLFW window callbacks, in front of
 * Minecraft's own handlers. Every event is then cross-checked against GLFW's internal key/button
 * table, so a fake call into a callback that didn't come from the OS is rejected. On Windows the
 * {@link WinInputGuard} low-level hook additionally rejects events flagged as software-injected
 * (SendInput / AutoHotkey / autoclickers).</p>
 *
 * <p>We never block anything from reaching the game; we only refuse to display it.</p>
 */
public final class PhysicalInput {
	public static final int KEY_SLOTS = 400;          // GLFW_KEY_LAST is 348
	public static final int MOUSE_SLOTS = 8;

	public static final int MB_LEFT = 0, MB_RIGHT = 1, MB_MIDDLE = 2, MB_BACK = 3, MB_FORWARD = 4;

	private static final boolean[] keyDown = new boolean[KEY_SLOTS];
	private static final long[] keyPressAt = new long[KEY_SLOTS];
	private static final long[] keyReleaseAt = new long[KEY_SLOTS];
	private static final int[] keyCount = new int[KEY_SLOTS];

	private static final boolean[] mouseDown = new boolean[MOUSE_SLOTS];
	private static final long[] mousePressAt = new long[MOUSE_SLOTS];
	private static final long[] mouseReleaseAt = new long[MOUSE_SLOTS];
	private static final int[] mouseCount = new int[MOUSE_SLOTS];

	@SuppressWarnings("unchecked")
	private static final ArrayDeque<Long>[] clickTimes = new ArrayDeque[MOUSE_SLOTS];
	private static final ArrayDeque<Long> keyTimes = new ArrayDeque<>();

	private static long lastScrollUp, lastScrollDown;
	private static double lastCursorX = Double.NaN, lastCursorY = Double.NaN;
	private static double moveX, moveY;     // smoothed mouse velocity (pixels / frame-ish)

	private static long blockedEvents;
	private static int lastMods;
	/** We enable lock-key mods for the LEDs but hide them from Minecraft so its behaviour is unchanged. */
	private static final int LOCK_MODS = GLFW.GLFW_MOD_CAPS_LOCK | GLFW.GLFW_MOD_NUM_LOCK;
	private static boolean installed;
	private static long windowHandle;

	// Keep strong references: native code points at these.
	private static GLFWKeyCallback ourKey, prevKey;
	private static GLFWMouseButtonCallback ourButton, prevButton;
	private static GLFWScrollCallback ourScroll, prevScroll;
	private static GLFWCursorPosCallback ourCursor, prevCursor;

	/** Physical event listener (used by the GUI for key capture, sounds, the Ctrl-tap opener...). */
	public interface Listener {
		default void onKey(int key, int scancode, boolean down) {}
		default void onMouse(int button, boolean down) {}
	}

	private static Listener listener;

	static {
		for (int i = 0; i < MOUSE_SLOTS; i++) clickTimes[i] = new ArrayDeque<>();
	}

	private PhysicalInput() {}

	public static void setListener(Listener l) {
		listener = l;
	}

	public static boolean isInstalled() {
		return installed;
	}

	/** Chains our callbacks in front of Minecraft's. Call once the window exists (CLIENT_STARTED). */
	public static void install(Minecraft mc) {
		if (installed) return;
		windowHandle = mc.getWindow().handle();

		ourKey = GLFWKeyCallback.create(PhysicalInput::handleKey);
		prevKey = GLFW.glfwSetKeyCallback(windowHandle, ourKey);

		ourButton = GLFWMouseButtonCallback.create(PhysicalInput::handleButton);
		prevButton = GLFW.glfwSetMouseButtonCallback(windowHandle, ourButton);

		ourScroll = GLFWScrollCallback.create(PhysicalInput::handleScroll);
		prevScroll = GLFW.glfwSetScrollCallback(windowHandle, ourScroll);

		ourCursor = GLFWCursorPosCallback.create(PhysicalInput::handleCursor);
		prevCursor = GLFW.glfwSetCursorPosCallback(windowHandle, ourCursor);

		// Lets GLFW report Caps Lock / Num Lock state in the modifier bits (used for the lock LEDs).
		try {
			GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_LOCK_KEY_MODS, GLFW.GLFW_TRUE);
		} catch (Throwable ignored) {
		}

		installed = true;
		LiquidInputClient.LOGGER.info("[LiquidInput] Physical input hooks installed (GLFW window {}).", windowHandle);
	}

	// ------------------------------------------------------------------ GLFW callbacks

	private static void handleKey(long window, int key, int scancode, int action, int mods) {
		try {
			lastMods = mods;
			if (action != GLFW.GLFW_REPEAT) recordKey(window, key, scancode, action == GLFW.GLFW_PRESS);
		} catch (Throwable t) {
			LiquidInputClient.LOGGER.error("[LiquidInput] key hook error", t);
		}
		if (prevKey != null) prevKey.invoke(window, key, scancode, action, mods & ~LOCK_MODS);
	}

	private static void handleButton(long window, int button, int action, int mods) {
		try {
			recordButton(window, button, action == GLFW.GLFW_PRESS);
		} catch (Throwable t) {
			LiquidInputClient.LOGGER.error("[LiquidInput] mouse hook error", t);
		}
		if (prevButton != null) prevButton.invoke(window, button, action, mods & ~LOCK_MODS);
	}

	private static void handleScroll(long window, double dx, double dy) {
		try {
			if (!WinInputGuard.consumeInjectedWheel()) {
				long now = System.currentTimeMillis();
				if (dy > 0) lastScrollUp = now;
				else if (dy < 0) lastScrollDown = now;
			} else {
				blockedEvents++;
			}
		} catch (Throwable ignored) {
		}
		if (prevScroll != null) prevScroll.invoke(window, dx, dy);
	}

	private static void handleCursor(long window, double x, double y) {
		if (!Double.isNaN(lastCursorX)) {
			double dx = x - lastCursorX, dy = y - lastCursorY;
			// Ignore teleports (cursor re-centering when grabbing / ungrabbing)
			if (Math.abs(dx) < 400 && Math.abs(dy) < 400) {
				moveX += dx;
				moveY += dy;
			}
		}
		lastCursorX = x;
		lastCursorY = y;
		if (prevCursor != null) prevCursor.invoke(window, x, y);
	}

	// ------------------------------------------------------------------ recording

	private static void recordKey(long window, int key, int scancode, boolean down) {
		if (key < 0 || key >= KEY_SLOTS) return;

		// 1) GLFW must agree: it updates its own key table from the OS message right before calling us.
		int real = GLFW.glfwGetKey(window, key);
		if ((down && real != GLFW.GLFW_PRESS) || (!down && real != GLFW.GLFW_RELEASE)) {
			blockedEvents++;
			return;
		}
		// 2) Windows: reject events the OS flagged as injected by software.
		if (WinInputGuard.consumeInjectedKey(scancode, down)) {
			blockedEvents++;
			// still make sure a fake press can never leave a key stuck "down"
			if (!down) keyDown[key] = false;
			return;
		}

		long now = System.currentTimeMillis();
		if (down) {
			if (keyDown[key]) return;
			keyDown[key] = true;
			keyPressAt[key] = now;
			keyCount[key]++;
			keyTimes.addLast(now);
			Stats.onKey();
		} else {
			if (!keyDown[key]) return;
			keyDown[key] = false;
			keyReleaseAt[key] = now;
		}
		if (listener != null) listener.onKey(key, scancode, down);
	}

	private static void recordButton(long window, int button, boolean down) {
		if (button < 0 || button >= MOUSE_SLOTS) return;

		int real = GLFW.glfwGetMouseButton(window, button);
		if ((down && real != GLFW.GLFW_PRESS) || (!down && real != GLFW.GLFW_RELEASE)) {
			blockedEvents++;
			return;
		}
		if (WinInputGuard.consumeInjectedButton(button, down)) {
			blockedEvents++;
			if (!down) mouseDown[button] = false;
			return;
		}

		long now = System.currentTimeMillis();
		if (down) {
			if (mouseDown[button]) return;
			mouseDown[button] = true;
			mousePressAt[button] = now;
			mouseCount[button]++;
			clickTimes[button].addLast(now);
			Stats.onClick(button);
		} else {
			if (!mouseDown[button]) return;
			mouseDown[button] = false;
			mouseReleaseAt[button] = now;
		}
		if (listener != null) listener.onMouse(button, down);
	}

	/** Once per rendered frame: prune CPS windows, decay movement, catch focus loss. */
	private static long lastFrameNanos;

	public static void frame() {
		long now = System.currentTimeMillis();
		long nanos = System.nanoTime();
		float dtFrames = lastFrameNanos == 0 ? 1 : Math.min(30f, (nanos - lastFrameNanos) / 16_666_666f);
		lastFrameNanos = nanos;
		for (ArrayDeque<Long> q : clickTimes) {
			while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
		}
		while (!keyTimes.isEmpty() && now - keyTimes.peekFirst() > 1000) keyTimes.pollFirst();
		double decay = Math.pow(0.82, dtFrames);
		moveX *= decay;
		moveY *= decay;

		// If the window lost focus while keys were held we never get the release: resync from GLFW.
		if (installed && windowHandle != 0 && (now & 0xF) == 0) {
			for (int k = 32; k < KEY_SLOTS; k++) {
				if (keyDown[k] && GLFW.glfwGetKey(windowHandle, k) != GLFW.GLFW_PRESS) {
					keyDown[k] = false;
					keyReleaseAt[k] = now;
				}
			}
			for (int b = 0; b < MOUSE_SLOTS; b++) {
				if (mouseDown[b] && GLFW.glfwGetMouseButton(windowHandle, b) != GLFW.GLFW_PRESS) {
					mouseDown[b] = false;
					mouseReleaseAt[b] = now;
				}
			}
		}
		Stats.peakCps(cps(MB_LEFT), cps(MB_RIGHT));
	}

	// ------------------------------------------------------------------ queries

	public static boolean isKeyDown(int key) {
		return key >= 0 && key < KEY_SLOTS && keyDown[key];
	}

	public static long keyPressedAt(int key) {
		return key >= 0 && key < KEY_SLOTS ? keyPressAt[key] : 0;
	}

	public static long keyReleasedAt(int key) {
		return key >= 0 && key < KEY_SLOTS ? keyReleaseAt[key] : 0;
	}

	public static int keyPressCount(int key) {
		return key >= 0 && key < KEY_SLOTS ? keyCount[key] : 0;
	}

	public static boolean isMouseDown(int b) {
		return b >= 0 && b < MOUSE_SLOTS && mouseDown[b];
	}

	public static long mousePressedAt(int b) {
		return b >= 0 && b < MOUSE_SLOTS ? mousePressAt[b] : 0;
	}

	public static long mouseReleasedAt(int b) {
		return b >= 0 && b < MOUSE_SLOTS ? mouseReleaseAt[b] : 0;
	}

	public static int mouseClickCount(int b) {
		return b >= 0 && b < MOUSE_SLOTS ? mouseCount[b] : 0;
	}

	public static int cps(int b) {
		return b >= 0 && b < MOUSE_SLOTS ? clickTimes[b].size() : 0;
	}

	public static int kps() {
		return keyTimes.size();
	}

	public static long lastScrollUp() {
		return lastScrollUp;
	}

	public static long lastScrollDown() {
		return lastScrollDown;
	}

	public static double moveX() {
		return moveX;
	}

	public static double moveY() {
		return moveY;
	}

	public static boolean capsLock() {
		return (lastMods & GLFW.GLFW_MOD_CAPS_LOCK) != 0;
	}

	public static boolean numLock() {
		return (lastMods & GLFW.GLFW_MOD_NUM_LOCK) != 0;
	}

	public static long blockedEvents() {
		return blockedEvents;
	}

	public static void resetCounters() {
		java.util.Arrays.fill(keyCount, 0);
		java.util.Arrays.fill(mouseCount, 0);
	}

	public static boolean anyOtherKeyDown(int exceptKey) {
		for (int k = 0; k < KEY_SLOTS; k++) if (k != exceptKey && keyDown[k]) return true;
		for (boolean b : mouseDown) if (b) return true;
		return false;
	}
}
