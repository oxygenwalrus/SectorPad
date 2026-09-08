# Handheld startup and diagnostics

SectorPad discovers an available controller as soon as its runtime starts. You can launch Starsector using a keyboard, mouse or touchscreen and then use the controller without restarting the game or pressing a function key. Keyboard and mouse activity does not turn off controller discovery. Regaining window focus while no pad is available now starts a clean rescan, which covers launcher and compositor handoffs that expose the controller after the game window appears.

SDL checks connection changes every poll. SectorPad also retries disconnected controller slots once per second to recover when a handheld changes modes without a usable connection notification. This fallback leaves connected controllers open. If no usable SDL slot exists for ten seconds, SectorPad restarts its own SDL session; repeated empty discovery backs off to thirty seconds. This full rescan stops while any slot is connected, even if an explicit different slot is selected. Automatic selection retains the active physical instance when SDL device indices change; an explicitly selected device waits for that slot instead of silently choosing another controller. Backend initialization failures are logged and retried after five seconds.

## SteamOS with Lutris/Wine or Proton

Version 1.1.1 added a direct, read-only Windows XInput controller path. A Windows JVM running through Wine still needs Windows native libraries; SectorPad detects Wine through the existing `ntdll` feature export and uses XInput first in Automatic mode. It does not try to load a Linux `.so` into that Windows process. Native Linux remains on SDL. If the preferred API exposes no controller and the controller slot is automatic, the alternate API is tried. Once an API has a connected controller, it keeps ownership until loss, a setting change or manual reconnect. Inputs from both APIs are never merged.

For the Ally setup reported through Steam and Lutris:

1. Update the complete SectorPad mod folder, including its `native` folder, then restart Starsector. The new Java code needs the matching bundled DLL.
2. Leave **LunaLib > SectorPad > General > Controller input backend** on **Automatic** and **Controller slot** on **-1** initially. The controller should be discovered without a button press.
3. If input remains unavailable, open **F10 Controller Setup** using keyboard, or open Controller Setup through LunaLib with mouse/touch. Click **Reconnect controller** at the bottom right. It is also available under Tools. The action releases held mod input, cancels an unconfirmed binding preview, reopens discovery, and retains saved mappings. Release sticks/buttons, then press A if the reconnect prompt appears.
4. Open **Controller Setup > Tools > Run 10-second controller discovery test**, then move both sticks and press several buttons. The status independently reports SDL and XInput as missing, connected/idle, or input seen. The last result remains visible after the timer finishes and is also recorded in diagnostics. The test observes both APIs but gameplay still uses exactly one input source.
5. If SDL is shown as connected but produces no input, choose **XInput** in the native LunaLib backend selector and Save. Choose **SDL** to explicitly compare the other path. These settings persist; changing them creates a release/rearm boundary. XInput accepts slot values 0-3; SDL accepts 0-7. An explicit slot never falls through to the other API's same-numbered device.
6. If neither path sees a controller, check that the Steam shortcut uses gamepad outputs and that the controller reaches the Wine game process. A keyboard/mouse-only layout cannot be discovered as a gamepad. SectorPad does not alter Steam layouts, Lutris runners, Wine registry entries or device permissions. Valve documents how [Steam Input gamepad emulation](https://partner.steamgames.com/doc/features/steam_controller/steam_input_gamepad_emulation_bestpractices) presents controller input through gamepad APIs.
7. Export diagnostics from **Controller Setup > Tools > Export diagnostic report** after trying the controls. Send `diagnostics.json.data` (or `diagnostics.json`, depending on the game's storage wrapper) and the game log with the test result.

The report distinguishes `wine_runtime`, `backend_mode`, `active_backend`, `requested_slot`, `discovery_test_sdl`, `discovery_test_xinput`, SDL mapped-gamepad availability and XInput API results. `bridge.surface_focus` and `bridge.native_focus` distinguish the game's window focus from the Windows/Wine foreground check. It also records the selected `gyro_mode` and mouse-compatible touch path, without logging raw motion or touch coordinates. Both focus protections remain in force; reconnect does not send input to another app. Report values contain no controller samples, hardware serials, Wine prefix path or environment-variable dump.

This change has automated routing/recovery tests and a passive native Windows probe. The reported ROG Ally SteamOS/Lutris/Wine combination has not been reproduced locally; the user's next device test is required to establish whether the runner exposes its controller through either API. Discovery recovery cannot create a controller hidden from both APIs.

## ROG Ally and other Windows handhelds

For an Ally's built-in controls, use a gamepad configuration for Starsector and keep the embedded controller enabled. Armoury Crate's Desktop Mode can map controls to keyboard and mouse functions; Gamepad Mode provides the controller-oriented configuration. ASUS documents both modes and their configurable mappings in its [Armoury Crate SE introduction](https://www.asus.com/support/faq/1050220/). Its [ROG Ally FAQ](https://www.asus.com/support/faq/1050046/) explains Auto Mode, game profiles and the Embedded Controller toggle.

If Auto Mode keeps using desktop controls, check the game profile and the actual Starsector executable selected by Armoury Crate, or select Gamepad Mode through Command Center. These are device settings owned by ASUS software. SectorPad does not change Armoury Crate, firmware, Windows settings or its launch profile, and cannot detect a gamepad that the operating system does not expose. Once the device becomes available to SDL, discovery continues automatically.

If launching through Steam Input or another mapper, use one controller path for each action. Avoid a layout that emits keyboard or mouse actions for the same buttons SectorPad handles as a gamepad; that can cause duplicate actions or repeated handoff to physical-input ownership. SectorPad does not rewrite Steam Input layouts. A mapper's keyboard/mouse-only output does not establish that SDL has a connected controller.

## Touchscreen interaction

Wine, gamescope and desktop environments commonly present a single touchscreen contact to Starsector as ordinary mouse events. SectorPad recognizes that path on its own setup, refit, radial and keyboard overlays. A tap activates only when the contact is released within a small movement tolerance. Vertical dragging scrolls lists without also clicking a row. Dragging around a radial menu updates its highlighted wedge and commits on release. A stationary long press cancels an overlay; in the refit workspace it opens or closes details.

The game and third-party native panels still receive the platform's normal mouse-compatible touch behavior. SectorPad does not replace their input handlers. Multi-finger gestures and raw SDL touch contacts are outside this version because the installed Jamepad bridge exposes controller state rather than SDL touch events.

## Gyro aiming

SectorPad 1.2.0 accepts gyro motion when Steam Input exposes it as mouse movement. Valve recommends mouse-style gyro output for accurate aiming and supports simultaneous controller and mouse sources. Configure the Steam shortcut so the normal buttons and sticks remain a gamepad, then set the gyro behavior to **As Mouse** or its current mouse-output equivalent. Valve's setup guide is [Steam Input: Getting Started for Developers](https://partner.steamgames.com/doc/features/steam_controller/getting_started_for_devs).

In **LunaLib > SectorPad > Gyro and touch**:

- **Combat aim** refines the retained right-stick aim direction and captures mouse motion while the selected activation is held, preventing the same gyro event from also driving native mouse aim.
- **Pointer** leaves Steam Input's normal mouse cursor behavior available without feeding it into combat aim.
- **Aim + pointer** enables combat refinement while preserving that cursor path.
- Activation can be Always, Hold LT, Hold RT, Hold LB, or Hold R3. Hold LT is the default.
- Sensitivity, smoothing, and both axis inversions are adjustable. Changing settings crosses the usual release/rearm boundary.

XInput carries buttons, sticks and triggers but no gyro sensor data. An external Xbox controller therefore cannot supply gyro. On a ROG Ally, Steam Input must receive the built-in motion sensor from the handheld layer. [HHD](https://github.com/hhd-dev/hhd) and [InputPlumber](https://github.com/ShadowBlip/InputPlumber) are examples of Linux handheld layers that can expose motion and controller devices, but they are separate software and are not installed or configured by SectorPad. If the pad itself is missing from both discovery-test paths, gyro aim remains disabled so mouse movement cannot unexpectedly seize combat aim.

## Access from the main menu

Release all controller buttons, triggers and sticks once after connection so input can arm from a neutral state. With the unobstructed Starsector title menu focused:

| Button | Action |
| --- | --- |
| X | Open Controller Setup, including remapping and calibration |
| Y | Open LunaLib's native mod settings menu |
| Right stick click (R3) | Open SectorPad's keyboard |
| View | Open the command hub using the default binding |

The fixed X, Y and R3 shortcuts remain available when editable gameplay controls are disabled. They apply only to the unobstructed title menu: an existing dialog, Codex screen, Console Commands window, LunaLib menu or SectorPad modal retains its own controls. View follows the editable hub binding. Holding View + Menu together for two seconds opens recovery and restores unconfirmed control changes; this reserved recovery chord remains available outside the title shortcuts too.

F8 (hub), F9 (keyboard) and F10 (Controller Setup) remain the default keyboard shortcuts and can be changed in LunaLib. Opening LunaLib uses its configured native shortcut. No base-game menu element is replaced.

After a disconnect, device change or detected long input interruption, SectorPad releases its held input and can pause the game according to the disconnect setting. On reconnection, release the controls and press A to acknowledge; resume using the normal pause control. Reconnection does not automatically resume the game. Losing focus also releases SectorPad's input ownership.

## Exporting a diagnostic report

Use either **Command hub → Export diagnostics** or **Controller Setup → Tools → Export diagnostic report**. The result is written through Starsector's common-data API to:

```text
SectorPad/exports/diagnostics.json
```

This is relative to Starsector's common-data directory, not the mod directory or a save. Each export replaces this one report. The UI reports whether saving and read-back verification succeeded. Nothing is uploaded automatically.

On the inspected Windows installation, the game stores it physically as `saves/common/SectorPad/exports/diagnostics.json.data`. The `.data` file contains the JSON report; include it when requesting support. The game's configured saves location determines the parent directory.

The report contains curated runtime versions, controller connection and focus state, context changes, event counts and exception types. Its in-memory history is capped at 128 events, with at most 64 state keys and 64 event counters. It excludes typed text, raw controller samples, device identifiers, profile names, save contents, exception messages and stack frames.

SectorPad also writes diagnostic events and error stack traces to the existing `starsector.log`. Repeated normal events and repeated errors for a given code are each throttled to one log entry per 30 seconds. State transitions bypass that per-code throttle so quick context or pause changes remain visible; unchanged state is deduplicated. Each 30-second window permits up to 60 normal diagnostic entries and reserves 12 additional error traces so routine events cannot consume the error budget. Suppression counts remain visible in the report. These bounds apply to SectorPad's diagnostic journal, not to logging by the game or other mods. The complete game log can contain paths, exception text and unrelated mod output; review it before sharing it.

When a recoverable exception reaches a SectorPad input, frame or rendering boundary, the mod disables its callbacks for that session, attempts each cleanup step independently, releases its input, restores its overlays/console adaptation and attempts to save the same diagnostic report automatically. A failure in one cleanup step is recorded and does not prevent later cleanup attempts. Backend failures that can be retried use the five-second retry path instead.

These boundaries cover SectorPad's own callbacks. They do not replace the JVM's global exception handler, catch failures in unrelated mods or recover from a fatal JVM/native crash, forced termination or process-wide memory failure. Such a crash may prevent cleanup and report export; retain `starsector.log` and any JVM crash report produced by the runtime.

## Developer-only physical input observation

The source distribution includes `sectorpad.core.BackendProbe`. The normal `tools/build.py --probe` invocation remains a short initialization check. With the compiled test/runtime classpath and mod dependencies, append an observation duration to the probe:

```text
java -noverify -cp <compiled-test-and-runtime-classpath> sectorpad.core.BackendProbe <packaged-native-directory> 60
```

The native directory is the directory containing `windows-x86_64` or `linux-x86_64`. The optional duration accepts 1–300 seconds. Run this standalone observation with Starsector closed. It reads controller state and prints device connection changes, button press/release edges, stick/trigger extrema and whether both triggers exceeded 0.55 together. It creates no keyboard, mouse, rumble or gameplay output and does not record keyboard input. Unlike the in-game support report, this explicitly requested developer probe prints the controller's SDL name and button activity.

During an observation, move both sticks through their ranges, press and release the buttons, operate each trigger separately and both together, and optionally disconnect/reconnect. A successful initialization proves enumeration only. Axis availability flags do not prove that the triggers were physically operated independently, and standalone samples do not establish in-game behavior.

The injectable discovery tests cover startup, missed notifications, bounded retries, explicit selection and SDL reindexing. Physical Xbox/handheld acceptance, Ally mode switching, in-game flight/aiming, focus changes, suspend/resume and Linux/SteamOS behavior require separate recorded runs on those setups. See [the compatibility report](COMPATIBILITY.md) for completed verification and outstanding tests.
