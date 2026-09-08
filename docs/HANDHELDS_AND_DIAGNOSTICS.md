# Handheld startup and diagnostics

SectorPad discovers an available controller as soon as its runtime starts. You can launch Starsector using a keyboard, mouse or touchscreen and then use the controller without restarting the game or pressing a function key. Keyboard and mouse activity does not turn off controller discovery.

SDL checks connection changes every poll. SectorPad also retries disconnected controller slots once per second to recover when a handheld changes modes without a usable connection notification. This fallback leaves connected controllers open. If no usable SDL slot exists for ten seconds, SectorPad restarts its own SDL session; repeated empty discovery backs off to thirty seconds. This full rescan stops while any slot is connected, even if an explicit different slot is selected. Automatic selection retains the active physical instance when SDL device indices change; an explicitly selected device waits for that slot instead of silently choosing another controller. Backend initialization failures are logged and retried after five seconds.

## SteamOS with Lutris/Wine or Proton

Version 1.1.1 adds a direct, read-only Windows XInput controller path. A Windows JVM running through Wine still needs Windows native libraries; SectorPad detects Wine through the existing `ntdll` feature export and uses XInput first in Automatic mode. It does not try to load a Linux `.so` into that Windows process. Native Linux remains on SDL. If the preferred API exposes no controller and the controller slot is automatic, the alternate API is tried. Once an API has a connected controller, it keeps ownership until loss, a setting change or manual reconnect. Inputs from both APIs are never merged.

For the Ally setup reported through Steam and Lutris:

1. Update the complete SectorPad mod folder, including its `native` folder, then restart Starsector. The new Java code needs the matching bundled DLL.
2. Leave **LunaLib > SectorPad > General > Controller input backend** on **Automatic** and **Controller slot** on **-1** initially. The controller should be discovered without a button press.
3. If input remains unavailable, open **F10 Controller Setup** using keyboard, or open Controller Setup through LunaLib with mouse/touch. Click **Reconnect controller** at the bottom right. It is also available under Tools. The action releases held mod input, cancels an unconfirmed binding preview, reopens discovery, and retains saved mappings. Release sticks/buttons, then press A if the reconnect prompt appears.
4. If SDL is shown as connected but produces no input, choose **XInput** in the native LunaLib backend selector and Save. Choose **SDL** to explicitly compare the other path. These settings persist; changing them creates a release/rearm boundary. XInput accepts slot values 0-3; SDL accepts 0-7. An explicit slot never falls through to the other API's same-numbered device.
5. If neither path sees a controller, check that the Steam shortcut uses gamepad outputs and that the controller reaches the Wine game process. A keyboard/mouse-only layout cannot be discovered as a gamepad. SectorPad does not alter Steam layouts, Lutris runners, Wine registry entries or device permissions. Valve documents how [Steam Input gamepad emulation](https://partner.steamgames.com/doc/features/steam_controller/steam_input_gamepad_emulation_bestpractices) presents controller input through gamepad APIs.
6. Export diagnostics from **Controller Setup > Tools > Export diagnostic report** after trying the controls. Send `diagnostics.json.data` (or `diagnostics.json`, depending on the game's storage wrapper) and the game log with the test result.

The report now distinguishes `wine_runtime`, `backend_mode`, `active_backend`, `requested_slot`, SDL open-slot availability and XInput API results. `bridge.surface_focus` and `bridge.native_focus` distinguish the game's window focus from the Windows/Wine foreground check. Both focus protections remain in force; reconnect does not send input to another app. Report values contain no controller samples, hardware serials, Wine prefix path or environment-variable dump.

This change has automated routing/recovery tests and a passive native Windows probe. The reported ROG Ally SteamOS/Lutris/Wine combination has not been reproduced locally; the user's next device test is required to establish whether the runner exposes its controller through either API. Discovery recovery cannot create a controller hidden from both APIs.

## ROG Ally and other Windows handhelds

For an Ally's built-in controls, use a gamepad configuration for Starsector and keep the embedded controller enabled. Armoury Crate's Desktop Mode can map controls to keyboard and mouse functions; Gamepad Mode provides the controller-oriented configuration. ASUS documents both modes and their configurable mappings in its [Armoury Crate SE introduction](https://www.asus.com/support/faq/1050220/). Its [ROG Ally FAQ](https://www.asus.com/support/faq/1050046/) explains Auto Mode, game profiles and the Embedded Controller toggle.

If Auto Mode keeps using desktop controls, check the game profile and the actual Starsector executable selected by Armoury Crate, or select Gamepad Mode through Command Center. These are device settings owned by ASUS software. SectorPad does not change Armoury Crate, firmware, Windows settings or its launch profile, and cannot detect a gamepad that the operating system does not expose. Once the device becomes available to SDL, discovery continues automatically.

If launching through Steam Input or another mapper, use one controller path for each action. Avoid a layout that emits keyboard or mouse actions for the same buttons SectorPad handles as a gamepad; that can cause duplicate actions or repeated handoff to physical-input ownership. SectorPad does not rewrite Steam Input layouts. A mapper's keyboard/mouse-only output does not establish that SDL has a connected controller.

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
