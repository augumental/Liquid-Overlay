package dev.liquidinput.input;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import com.sun.jna.Callback;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;

import dev.liquidinput.LiquidInputClient;

/**
 * Windows only: a low-level keyboard + mouse hook (WH_KEYBOARD_LL / WH_MOUSE_LL) that reads the
 * {@code LLKHF_INJECTED} / {@code LLMHF_INJECTED} flags Windows attaches to input that was created
 * by software (SendInput, keybd_event, mouse_event, AutoHotkey, macro tools, autoclickers).
 *
 * <p>The hook runs before the event reaches the game window, so when GLFW later delivers the same
 * event to {@link PhysicalInput} we can tell it came from software and refuse to display it.
 * Nothing is ever blocked from the game itself, and the hook only reads two integers per event.</p>
 *
 * <p>Only core JNA (bundled with Minecraft) is used. Any failure just disables the guard.</p>
 */
public final class WinInputGuard {
	private static final long WINDOW_MS = 150;

	private static final int WH_KEYBOARD_LL = 13, WH_MOUSE_LL = 14;
	private static final int WM_KEYDOWN = 0x100, WM_KEYUP = 0x101, WM_SYSKEYDOWN = 0x104, WM_SYSKEYUP = 0x105;
	private static final int WM_LBUTTONDOWN = 0x201, WM_LBUTTONUP = 0x202, WM_RBUTTONDOWN = 0x204, WM_RBUTTONUP = 0x205;
	private static final int WM_MBUTTONDOWN = 0x207, WM_MBUTTONUP = 0x208, WM_MOUSEWHEEL = 0x20A;
	private static final int WM_XBUTTONDOWN = 0x20B, WM_XBUTTONUP = 0x20C;
	private static final int WM_QUIT = 0x12;
	private static final int LLKHF_EXTENDED = 0x01, LLKHF_LOWER_IL_INJECTED = 0x02, LLKHF_INJECTED = 0x10;
	private static final int LLMHF_INJECTED = 0x01, LLMHF_LOWER_IL_INJECTED = 0x02;

	// [code * 2 + (down ? 1 : 0)] -> expiry timestamp of an "injected" mark
	private static final AtomicLongArray keyMarks = new AtomicLongArray(512 * 2);
	private static final AtomicLongArray buttonMarks = new AtomicLongArray(8 * 2);
	private static final AtomicLongArray anyKeyMarks = new AtomicLongArray(2);
	private static final AtomicLong wheelMark = new AtomicLong();

	private static volatile boolean running;
	private static volatile boolean enabled = true;
	private static volatile int threadId;
	private static Thread thread;

	// Strong refs so JNA callbacks are never collected
	private static LowLevelProc keyboardProc, mouseProc;
	private static Pointer keyboardHook, mouseHook;

	private WinInputGuard() {}

	public interface User32 extends StdCallLibrary {
		Pointer SetWindowsHookExW(int idHook, Callback lpfn, Pointer hMod, int dwThreadId);

		Pointer CallNextHookEx(Pointer hhk, int nCode, Pointer wParam, Pointer lParam);

		boolean UnhookWindowsHookEx(Pointer hhk);

		int GetMessageW(Pointer lpMsg, Pointer hWnd, int wMsgFilterMin, int wMsgFilterMax);

		boolean PostThreadMessageW(int idThread, int msg, Pointer wParam, Pointer lParam);
	}

	public interface Kernel32 extends StdCallLibrary {
		Pointer GetModuleHandleW(WString name);

		int GetCurrentThreadId();
	}

	public interface LowLevelProc extends StdCallLibrary.StdCallCallback {
		Pointer callback(int nCode, Pointer wParam, Pointer lParam);
	}

	public static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	public static boolean isRunning() {
		return running;
	}

	public static void setEnabled(boolean on) {
		enabled = on;
		if (on && !running) start();
	}

	public static synchronized void start() {
		if (running || !isWindows()) return;
		thread = new Thread(WinInputGuard::run, "LiquidInput-InputGuard");
		thread.setDaemon(true);
		thread.setPriority(Thread.MAX_PRIORITY);
		thread.start();
	}

	public static synchronized void stop() {
		if (!running) return;
		try {
			User32 u = Native.load("user32", User32.class);
			u.PostThreadMessageW(threadId, WM_QUIT, null, null);
		} catch (Throwable ignored) {
		}
	}

	private static void run() {
		try {
			User32 user32 = Native.load("user32", User32.class);
			Kernel32 kernel32 = Native.load("kernel32", Kernel32.class);
			threadId = kernel32.GetCurrentThreadId();
			Pointer module = kernel32.GetModuleHandleW(null);

			keyboardProc = (nCode, wParam, lParam) -> {
				if (nCode >= 0 && lParam != null) {
					try {
						int msg = (int) Pointer.nativeValue(wParam);
						int scan = lParam.getInt(4);
						int flags = lParam.getInt(8);
						if ((flags & (LLKHF_INJECTED | LLKHF_LOWER_IL_INJECTED)) != 0) {
							boolean down = msg == WM_KEYDOWN || msg == WM_SYSKEYDOWN;
							boolean up = msg == WM_KEYUP || msg == WM_SYSKEYUP;
							if (down || up) {
								int code = (scan & 0xFF) | ((flags & LLKHF_EXTENDED) != 0 ? 0x100 : 0);
								long until = System.currentTimeMillis() + WINDOW_MS;
								if ((scan & 0xFF) == 0) anyKeyMarks.set(down ? 1 : 0, until);
								else keyMarks.set(code * 2 + (down ? 1 : 0), until);
							}
						}
					} catch (Throwable ignored) {
					}
				}
				return user32.CallNextHookEx(null, nCode, wParam, lParam);
			};

			mouseProc = (nCode, wParam, lParam) -> {
				if (nCode >= 0 && lParam != null) {
					try {
						int msg = (int) Pointer.nativeValue(wParam);
						int mouseData = lParam.getInt(8);
						int flags = lParam.getInt(12);
						if ((flags & (LLMHF_INJECTED | LLMHF_LOWER_IL_INJECTED)) != 0) {
							long until = System.currentTimeMillis() + WINDOW_MS;
							int xb = (mouseData >>> 16) & 0xFFFF; // 1 = XBUTTON1 (back), 2 = XBUTTON2 (forward)
							switch (msg) {
								case WM_LBUTTONDOWN -> buttonMarks.set(PhysicalInput.MB_LEFT * 2 + 1, until);
								case WM_LBUTTONUP -> buttonMarks.set(PhysicalInput.MB_LEFT * 2, until);
								case WM_RBUTTONDOWN -> buttonMarks.set(PhysicalInput.MB_RIGHT * 2 + 1, until);
								case WM_RBUTTONUP -> buttonMarks.set(PhysicalInput.MB_RIGHT * 2, until);
								case WM_MBUTTONDOWN -> buttonMarks.set(PhysicalInput.MB_MIDDLE * 2 + 1, until);
								case WM_MBUTTONUP -> buttonMarks.set(PhysicalInput.MB_MIDDLE * 2, until);
								case WM_XBUTTONDOWN -> buttonMarks.set((xb == 1 ? PhysicalInput.MB_BACK : PhysicalInput.MB_FORWARD) * 2 + 1, until);
								case WM_XBUTTONUP -> buttonMarks.set((xb == 1 ? PhysicalInput.MB_BACK : PhysicalInput.MB_FORWARD) * 2, until);
								case WM_MOUSEWHEEL -> wheelMark.set(until);
								default -> {
								}
							}
						}
					} catch (Throwable ignored) {
					}
				}
				return user32.CallNextHookEx(null, nCode, wParam, lParam);
			};

			keyboardHook = user32.SetWindowsHookExW(WH_KEYBOARD_LL, keyboardProc, module, 0);
			mouseHook = user32.SetWindowsHookExW(WH_MOUSE_LL, mouseProc, module, 0);
			if (keyboardHook == null && mouseHook == null) {
				LiquidInputClient.LOGGER.warn("[LiquidInput] Could not install Windows input guard hooks.");
				return;
			}
			running = true;
			LiquidInputClient.LOGGER.info("[LiquidInput] Windows injected-input guard active.");

			Memory msg = new Memory(64);
			while (user32.GetMessageW(msg, null, 0, 0) > 0) {
				// LL hooks are dispatched while this thread waits in GetMessage; nothing else to do.
			}
			if (keyboardHook != null) user32.UnhookWindowsHookEx(keyboardHook);
			if (mouseHook != null) user32.UnhookWindowsHookEx(mouseHook);
		} catch (Throwable t) {
			LiquidInputClient.LOGGER.warn("[LiquidInput] Windows input guard unavailable: {}", t.toString());
		} finally {
			running = false;
		}
	}

	private static boolean consume(AtomicLongArray arr, int idx) {
		long until = arr.get(idx);
		if (until != 0 && System.currentTimeMillis() <= until) {
			arr.set(idx, 0);
			return true;
		}
		return false;
	}

	static boolean consumeInjectedKey(int glfwScancode, boolean down) {
		if (!running || !enabled) return false;
		int code = glfwScancode & 0x1FF;
		if (code > 0 && consume(keyMarks, code * 2 + (down ? 1 : 0))) return true;
		return consume(anyKeyMarks, down ? 1 : 0);
	}

	static boolean consumeInjectedButton(int button, boolean down) {
		if (!running || !enabled || button < 0 || button >= 8) return false;
		return consume(buttonMarks, button * 2 + (down ? 1 : 0));
	}

	static boolean consumeInjectedWheel() {
		if (!running || !enabled) return false;
		long until = wheelMark.get();
		if (until != 0 && System.currentTimeMillis() <= until) {
			wheelMark.set(0);
			return true;
		}
		return false;
	}
}
