# Liquid Input Overlay — Fabric 1.21.11

A polished keystroke / mouse overlay (think NohBoard, inside Minecraft) with a liquid-glass settings GUI.
It only ever shows **real, physical** key presses and mouse clicks.

## Build

You need **JDK 21 or newer**.

```
./gradlew build          # Windows: gradlew.bat build
```

The mod jar ends up in `build/libs/liquid-input-overlay-1.0.0.jar`. Drop it into `.minecraft/mods`
together with **Fabric API 0.141.6+1.21.11** (or newer) and Fabric Loader 0.20.1+.

No Java set up? Push this folder to a GitHub repo: the included workflow
(`.github/workflows/build.yml`) builds it and attaches the jar under *Actions → latest run → Artifacts*.

## Using it

* **Tap Left Ctrl** in-game to open the GUI (a quick tap with nothing else held, so holding Ctrl to sprint still works).
  Tap again or press Esc to close. The key and tap behaviour are configurable.
* The first time it opens you get the intro animation, ending on the big ON/OFF switch.

## Features

| Area | What you can change |
|---|---|
| Keyboard | QWERTY / AZERTY / QWERTZ / Auto (OS layout) labels · WASD, Half or Full size (F-row, nav cluster, numpad) · hide or rebind any key · key size, gap, corner radius, scale, opacity · labels, label shadow, press counters, Caps/Num-lock LEDs · key indicators on/off |
| Mouse | Physical-mouse shape or classic buttons · left / right / middle / side buttons · scroll-wheel flashes · CPS inside or below · movement pad · click counters · stats panel (CPS / KPS / totals) |
| Position | Live drag-and-drop editor on your real screen with snapping guides, scroll to resize, right-click to hide |
| Theme | 12 presets + full HSV colour picker for 8 colour slots · vertical / horizontal / diagonal / whole-board gradients · glass opacity, specular highlight, soft shadows, backplate · rainbow presses |
| Animation | Press: None / Fade / Pop / Ripple / Sink · Idle: Breathe / Rainbow wave / Shimmer · release fade, speed, bounce, glow · keys cascade in when you join |
| Sounds | 5 synthesised key packs (Thock, Clicky, Creamy, Typewriter, Bubble) + Vanilla + **Custom** · mouse packs · volumes, pitch variation, release sounds |
| Profile | Your username and head (your real skin once you're in a world), lifetime stats, 3 saved setup slots, reset tools |
| GUI feel | Sounds for every button, toggle, tab and slider tick · haptic feedback (springy window nudge + squish on press) · frosted blur |

### Custom sounds
Put WAV files in `.minecraft/config/liquidinput/sounds/` named `key.wav`, `key_up.wav`, `mouse.wav`,
`mouse_up.wav`, choose **Custom** in the Sounds tab and press **Reload**.

## How "physical input only" works

1. **No KeyMapping reads.** Mods and macros can flip Minecraft's key bindings (`setDown`, `click`) — the overlay never looks at them.
2. **GLFW-level hooks.** The mod chains its own GLFW key / mouse-button / scroll callbacks in front of Minecraft's and
   cross-checks every event against GLFW's internal key table, so fake calls into the input handlers are rejected.
3. **Windows injected-input guard.** A low-level keyboard + mouse hook reads the `LLKHF_INJECTED` / `LLMHF_INJECTED`
   flags Windows puts on software-generated input (SendInput, AutoHotkey, autoclickers), and those events are not displayed.
   The count of ignored events is shown in the GUI.

Nothing is ever blocked from the game; spoofed input is just not drawn. On macOS/Linux steps 1–2 apply
(the OS gives apps no reliable "injected" flag there), and a hardware-level emulator (e.g. a programmable
mouse or a microcontroller pretending to be a keyboard) is indistinguishable from a real device on any OS.

"Haptic feedback": PCs have no vibration API for Java mods, so feedback is tactile-feeling instead — a springy
nudge of the window, squishing buttons and layered sounds. Strength is adjustable (0 turns it off).

## Project layout

```
src/main/java/dev/liquidinput/
  LiquidInputClient.java     entrypoint, Ctrl-tap opener, hotkeys
  input/                     PhysicalInput (GLFW hooks, CPS), WinInputGuard (injected filter), Stats
  layout/KeyboardLayouts     QWERTY/AZERTY/QWERTZ geometry for WASD/Half/Full
  render/                    Painter (anti-aliased rounded shapes), OverlayRenderer (HUD widgets)
  gui/                       GlassScreen base, Ui toolkit, MainScreen, IntroScreen, LayoutEditorScreen
  sound/                     SoundFx, CustomSounds
src/main/resources/assets/liquidinput/   sounds.json, synthesised .ogg sounds, icon
```

Config lives in `.minecraft/config/liquidinput.json`.
