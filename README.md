# SectorPad

SectorPad adds controller input, a command hub, an on-screen keyboard, and LunaLib remapping to Starsector. It uses SDL gamepad mappings for standard Xbox-style controls and Steam Deck-style layouts. It adds its own panels and adapts existing public game operations; it does not install replacements for the game's UI classes, LWJGL input implementation, JVM, or native libraries.

Version 1.1.1 adds Wine-aware controller discovery, a direct XInput fallback, and a **Reconnect controller** button in F10 Controller Setup. For SteamOS/Lutris testing, see [Wine recovery instructions](docs/HANDHELDS_AND_DIAGNOSTICS.md#steamos-with-lutriswine-or-proton).

Version 1.1.0 added a modern TriPad visual style across refit, the radial hub, keyboard and controller setup. It also supplies an original [native HUD frame skin](docs/HUD_SKIN.md) through mod textures and the session's player UI palette. Matte blue-grey panels, cyan accents and amber selection share a consistent hierarchy. Native icons, gauges and gameplay controls retain their original behavior. Use one native skin at a time: AashPad and similar skins can conflict with these texture paths and colors.

**Validation status, 8 September 2026:** the mod builds and passes its automated checks. Isolated Windows game tests cover character text entry, exact cargo pickup/cancellation, profile persistence and rollback, wheel customization, native tactical orders, autopilot toggling, Console layout/text entry and controlled failure cleanup/export. These journeys used keyboard and mouse. A connected XInput controller is detected at startup, and the user confirmed physical X pause/unpause and campaign fleet movement after the context fix. Controller combat, mixed input and SteamOS operation remain unverified. The package version `1.1.1` is an identifier, not hardware or production certification. See [compatibility and evidence](docs/COMPATIBILITY.md).

The [controller refit workspace](docs/REFIT_WORKSPACE.md) adds five fitting sections, ship/mount selection and native picker handoffs. Open it from the refit command hub, or automatically when controller input is active. Weapon/fighter fitting, allocation, undo, modules and simulation return have isolated Windows checks; physical refit acceptance remains pending.

## Install and open

| Requirement | Target inspected for this build |
| --- | --- |
| Starsector | 0.98a-RC8, Java 17, 64-bit |
| LunaLib | 2.0.5 |
| LazyLib | 3.0 / installed 3.0.0 |
| Platform | Windows x86-64 tested for the documented game journeys; Linux x86-64 backend included but unverified |
| Optional Console Commands | 4.0.9 inspected; signatures checked before integration |

1. Exit Starsector. Install LunaLib and LazyLib separately if needed.
2. Extract the `SectorPad` directory from `SectorPad.zip` into the game's `mods` directory. The result should be `mods/SectorPad/mod_info.json`.
3. Enable SectorPad, LunaLib, and LazyLib in the normal mod launcher. Disable another controller driver such as SSMSController/SSMSControllerEx before testing SectorPad; simultaneous input owners are not supported.
4. Start the game. Press **F8** for the command hub, **F9** for the on-screen keyboard, or **F10** for Controller Setup. These native Luna keyboard shortcuts can each be changed or cleared.
5. Connect the controller, release every control and center both sticks, then acknowledge the reconnect prompt with **A**. Choose the active device, profile, and comfort settings through LunaLib or Controller Setup.

Keep the keyboard and mouse available during initial testing. Controller Setup and the command hub are usable without a controller. Installation is confined to the new mod directory and the normal launcher selection; no base-game patch or security-setting change is required.

On the unobstructed main menu, **X opens Controller Setup, Y opens LunaLib Mod Settings, and R3 opens the keyboard**. These shortcuts need no function keys. SectorPad detects controllers independently of keyboard/mouse use and rescans disconnected slots once per second. On a ROG Ally, enable its embedded controller and use a Gamepad configuration for Starsector; SectorPad cannot enable a device hidden by system software.

For troubleshooting, choose **Export diagnostics** in the command hub or Controller Setup's Tools section. The bounded local report records runtime versions, connection/focus/context transitions and error types without typed text or save data. SectorPad also logs throttled errors to `starsector.log`; callback failures stop its input, attempt cleanup independently, and export a report automatically. See [handheld setup and diagnostics](docs/HANDHELDS_AND_DIAGNOSTICS.md).

## Standard controls

These are the built-in Standard profile. Southpaw swaps the sticks. UI, Campaign, Combat, Map, Tactical, and Deployment mappings can be edited separately. The hub's Controls entry shows the effective bindings for the current context.

| Control | Menus | Campaign | Piloted combat |
| --- | --- | --- | --- |
| Left stick | Pointer | Fleet movement | Ship movement |
| Right stick | Scroll; map pan where applicable | Pointer | Aim |
| A / B | Confirm or primary hold / cancel | Interact / cancel course | Target / vent |
| X / Y | Secondary action / selection actions | Pause / map | Autofire / tactical map |
| LB / RB | Previous / next tab | Abilities / fast-forward | Weapon groups / ship system |
| LT / RT | Previous / next subtab; map zoom | Zoom out / in | Shield or cloak / fire |
| D-pad | Directional navigation | Left/right target; up intel; down fleet | Left/right weapon group; up fighters; down autopilot |
| L3 / R3 | Precision pointer / tooltip | Pointer mode / recenter | Brake / target lock |
| View / Menu | Command hub / game menu | Command hub / game menu | Command hub / game menu |

Map and tactical controls use the existing map's pan/zoom operations where recognized. Adjacent scroll panes own scrolling under the pointer. Custom mod interfaces may expose fewer navigable controls and require the pointer. Live checks cover an exact cargo pickup and a native tactical waypoint order; general drag, scrolling and sustained controller input still require device testing.

In Controller Setup, D-pad or left stick selects a row, A chooses it, LB/RB changes context, and View changes section. With a keyboard, use arrows, Enter, P to change section, and Escape. Remapping waits for neutral input before capture. **Y** previews a draft for 15 seconds; **A/Enter** commits and **B/Escape** rolls back. Loss of focus, disconnect, or preview expiry restores the previous committed profile.

The fixed recovery chord is **View + Menu held for two seconds**. It opens Controller Setup outside the editable mappings. Restore Standard Controls in Tools also uses a confirmation preview. Guide is reserved and is never bound by SectorPad.

## Text, profiles, and optional Console

Select a text field before opening the on-screen keyboard. Edits are staged; Apply commits to the still-valid target field. Confirming a form or executing a console command is a separate action. Windows has Unicode input as a fallback; direct field editing does not assume a US keyboard layout or overwrite the clipboard.

SectorPad's TriPad styling uses cyan controller cues, amber navigation focus, bevelled panels, and the game's Insignia fonts. Its pointer brackets surround the existing cursor. Manual aim, precision aim, and locked targets have distinct shapes. Luna's overlay scale preference affects SectorPad's drawing; the docked keyboard reserves space for the console input and output above it.

With Console Commands installed, SectorPad can open that mod's existing console and adapt its public panel layout for text entry. **Console at top left** in LunaLib defaults to enabled for controller sessions; entering Console from SectorPad's hub selects that layout. Ordinary keyboard-only console sessions retain their native placement. This adjusts the existing input/log/suggestion layout while active; it does not replace the Console Commands files. Applying keyboard text sets console input. A separate Run/Enter action executes it.

Profiles and calibration use the game's common-data API under the logical `SectorPad/` namespace, separate from serialized campaign objects. Same-name standard gamepads share calibration; the backend does not provide a persistent physical serial identity here. Tools supports named profiles, export, and validated import with an explicit import index. Detailed instructions and storage limits are in [settings and profiles](docs/SETTINGS.md).

To remove the mod, exit the game, disable SectorPad in the launcher, and remove only its mod directory. Common-data profiles can be retained for later use. An isolated campaign saved with SectorPad's transient hooks active loaded successfully after the complete mod directory was removed.

## Build from source

Use Python 3.11 or newer and a Java 17+ JDK with `java` and `javac` on `PATH`. A full Windows build also needs Visual Studio 2022 Community C++ tools. The native builder downloads pinned Windows SDK packages into the project's `build/toolchain` cache when needed; it does not install an SDK or change system settings. The build reads the local game's API and dependency JARs.

From this source directory:

```powershell
python tools/build.py --game 'C:\Program Files (x86)\Fractal Softworks\Starsector'
python tools/build.py --game 'C:\Program Files (x86)\Fractal Softworks\Starsector' --probe
```

The first command compiles Java/native code, runs automated checks, and creates `build/SectorPad.zip`, `build/SectorPad-source.zip`, `build/package/SectorPad`, and `build/package-sha256.json`. Both archives include documentation and dependency notices. The source archive includes the original vendored build inputs, the marked Jamepad adaptation, and a file checksum manifest. The runtime package's `BUILD-INFO.json` identifies that source manifest. The optional probe initializes SDL and the Windows input observer and reads controller availability; it does not send keyboard or mouse input. `python tools/build.py --logic-only` runs the portable core tests without the game classpath or native build. Full release packaging currently requires the Windows JNI binary.

The build does not install or enable SectorPad. Verification tools create a separate game copy under `build/verification`; that copy and downloaded toolchains are local test material and must not be redistributed. See [game integration](docs/GAME_INTEGRATION.md) for architecture and [third-party notices](THIRD_PARTY_NOTICES.md) for the exact dependency sources, retained licenses, and marked Jamepad changes.
