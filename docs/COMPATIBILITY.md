# Compatibility and verification

This is an evidence snapshot for SectorPad `1.0.0`, dated **7 September 2026**. Implementation coverage, automated verification, live UI verification, and physical-device acceptance are different levels of evidence. No physical controller was available for this build. Full production or handheld compatibility is not established.

## Environment and evidence

The inspected installation is Starsector **0.98a-RC8**, running 64-bit Java 17, with LunaLib **2.0.5** and LazyLib **3.0.0**. Optional Console Commands **4.0.9** supplied the inspected console API. Compilation uses Java `--release 17`. The manifest requires LunaLib and LazyLib; Console Commands remains optional.

| Area | Established evidence | Limit |
| --- | --- | --- |
| Build and static integration | Java compilation against the installed game/dependency APIs; Windows JNI compilation with warnings treated as errors | Successful compilation does not validate a complete game journey |
| Automated behavior | Headless suites cover routing, neutral/release gates, pointer accumulation, UI delivery sequencing, navigation, settings validation, previews, storage recovery, and adapter logic | These use controlled inputs and fixtures, not a physical gamepad |
| SDL backend | Packaged native library initializes inside the isolated Windows game; passive controller probe runs | No connected controller was available to verify axes, buttons, simultaneous triggers, or hotplug |
| Windows input backend | Mod-local JNI loads under the normal script restrictions; passive observer lifecycle checks pass | Live physical/synthetic overlap, keyboard layout, and focus-loss behavior still require device acceptance |
| Added UI | Native Luna settings and Controller Setup mounted in title, campaign, cargo and refit. TriPad hub/keyboard were visually checked at 1280×800 and 1280×720; quantity entry at 800p. At 720p, overlay scale 1.8 retained console clearance and correct keyboard hits. The corrected combat hub and setup panel were checked over the native HUD/deployment picker, including setup navigation and close. Hints hide while native Luna settings is open | Keyboard/mouse operation on Windows; physical handheld readability and every third-party mount path remain unverified. These fit checks concern SectorPad panels; the original game's UI retains its own sizing |
| Native mouse and text delivery | Hub → Select opened the original New Game button. Staged character text was applied to the captured original name field after focus moved | General scrolling, dragging and mixed physical/controller ownership need live device checks |
| Cargo quantities | SectorPad captured the Supplies stack before the hub opened, picked up exactly 7 from 219 through the original cargo handlers, and native Escape restored all 219 | This verifies pickup/cancellation; it does not certify every market transaction or custom cargo widget |
| Simulation and tactical actions | Refit simulation, deployment picker and combat hub mounted. Order selected here created the original waypoint at the captured map point and assigned the selected ship. Hub → Autopilot toggled the game's autopilot | Sustained controller flight, aiming, firing, tactical multiselection and every command remain device acceptance work |
| Profiles and wheel order | Southpaw commit and process-restart persistence; 15-second expiry restored the saved profile; Standard restored. A custom wheel order was saved, applied to the hub, and retained after discarding a draft reset and a full game restart | Physical remap capture and per-device calibration remain unverified |
| Console Commands | Top-left preference on/off persisted in native Luna. Ordinary keyboard console retained the native bottom layout. TriPad applied staged help without executing it; separate Enter executed help. Scrollback remained above the docked keyboard | Verified against installed 4.0.9; incompatible versions fail closed |
| Campaign save/load/removal | Saved with transient listener and frame hooks active, removed the complete SectorPad directory, restarted and loaded the same campaign successfully at 1280×720. The 219 supplies, 253 fuel, 52,000 credits and paused date were retained | One isolated campaign and installed game version; arbitrary third-party combinations are not established |
| Linux / Steam Deck | Linux x86-64 SDL native is packaged; Linux fallback exists | No Linux, SteamOS, compositor, suspend/resume, docking, or physical Deck verification |

Screenshots retain the [TriPad console and visible output](evidence/tripad-console-output-1280x800.png), [exact native quantity pickup](evidence/native-quantity-seven.png), [native tactical waypoint](evidence/native-tactical-order.png), [saved wheel order](evidence/wheel-order-applied.png), and [campaign loaded after removal](evidence/campaign-loaded-without-sectorpad.png). They demonstrate the specified keyboard/mouse journeys, not physical controller operation. Test counts and rebuilt binary hashes can change; use the actual build output and package manifest for a particular artifact.

The smaller-resolution evidence includes the [720p keyboard](evidence/tripad-console-keyboard-1280x720.png), [maximum overlay scale](evidence/tripad-console-1280x720-scale-1.8.png), [corrected combat hub](evidence/tripad-combat-hub-1280x720.png), and [unobscured native Luna settings](evidence/luna-settings-1280x720.png). The maximum-scale check used an isolated test preference; the release default remains 1.0.

The verification launcher uses an isolated copied game with separate mods, saves, logs, and screenshots. Its JVM module-open flags follow the original installation's flags. The original game directory and the user's Desktop SSMSControllerEx source/JAR/patch were preserved. This document does not authorize replacing those files.

Startup logs contain two ship-data loader errors for `flare` and `module_hightech_decor`. The same records occur in the removed-mod baseline, where SectorPad is absent. The final combat and maximum-scale sessions produced no additional error/fatal records or SectorPad runtime-failure records. Original ship data was not changed to suppress these existing messages.

## Input and platform boundaries

SectorPad owns an SDL controller manager, mod-local native files, game callbacks, and its added panels. It does not replace the global LWJGL Mouse/Keyboard implementation or the game's classes. The library loader calls `System.load` on explicit native paths inside the mod. Runtime persistence uses the public common-data API. UI discovery uses public methods and `MethodHandles.publicLookup`; it does not enable private access, bypass the script classloader, or alter game security settings.

The mouse adapter constructs the game's own concrete mouse events and sends them on the game thread through the current original UI root's public input processor. It tracks the current UI identity and focus. It does not append synthetic interface objects to a copied pre-core event list: those lists are not the original input queue. This adapter depends on the exact public signatures inspected in 0.98a-RC8, so a game update requires another signature and live-behavior check.

The Windows JNI output uses SendInput for keyboard/Unicode actions and observes non-injected physical held state while the game is focused. Its observer retains held bits and release counters, not typed text. Pointer movement preserves fractional motion before quantization and re-synchronizes on physical input or context/display changes. Automated ownership tests establish the modeled logic; mixed physical/controller input still needs live acceptance. No clipboard replacement or runtime telemetry is used.

Linux uses a Robot fallback for desktop keyboard output. It cannot reliably distinguish overlapping physical and synthetic holds, and compositor acceptance is unverified. It does not have the Windows native observer's physical-ownership capability. Existing public text fields can be edited through the validated text-field adapter; arbitrary Linux Unicode output is not claimed. The upstream Jamepad Linux instructions list libudev and libevdev as dependencies supplied by the operating system. [Jamepad platform instructions](https://github.com/libgdx/Jamepad/tree/8b8a543c529e6af33bd6de2329f9b4fe97259641).

Only Windows and Linux **x86-64** native paths are selected by the loader. macOS, 32-bit, and ARM are not supported by this package. Standard SDL sticks, buttons, and separate trigger axes are the input model. Guide, gyro, touchpads, and independent rear-paddle actions are not exposed as SectorPad bindings. Steam Input or a desktop controller mapper can produce duplicate keyboard/mouse and SDL actions; a tested single-owner configuration is still needed for each handheld environment.

Runtime reconnect detection uses SDL instance identity. Persistent calibration uses controller name plus the standard-gamepad layout, so reconnects retain calibration but two same-name devices share it. This is not per-physical-serial calibration. Wired/Bluetooth name changes may choose a different calibration entry.

## Other mods and UI coverage

SSMSController and SSMSControllerEx are not dependencies or bundled runtime code. Their global input shims conflict with SectorPad's input ownership; running both controller drivers is unsupported. Other mods that bundle the same Jamepad/GDX package names may also cause shared-classpath version conflicts. The included Jamepad adaptation is the sole Jamepad implementation within SectorPad itself.

The navigator reads visible native controls and modal ownership. Supported scroll panes, map pan/zoom, and existing text fields are adapted through their public operations. Unknown custom widgets can fall back to pointer interaction, but complete third-party UI navigation is not guaranteed. The game update risk includes public methods on obfuscated concrete classes, even though access is public.

Console integration is optional and checks the existing Console Commands API. While active, its controller layout moves/resizes existing console panels and recreates suggestions through that mod's public operations. Applying staged text does not execute a command. This is deliberate UI adaptation; it is not a claim that every original UI position remains unchanged. An incompatible console API leaves that integration unavailable.

Combat uses normal ship commands and the existing game controls; no AI implementation is replaced. Campaign movement uses the transient destination override. The tactical hub and selection wheel expose **Order selected here** separately from objectives on the piloted ship's current target. That action captures the native selected units and map point, then delegates to the original context-order handler after revalidation. Native assignment/direct-order permissions preserve open-channel orders at zero command points and the game's free-retreat allowance. Headless behavior and public signatures are checked; the live simulation verified a selected-ship waypoint order and an autopilot toggle. The wider tactical/controller journey remains pending.

Core switching follows the encounter-owned core where present, skips disabled native tabs, and rejects nested dialogs and known public cargo-pickup/refit-picker state. The native-operation detector does not claim to recognize private warroom waiting state or every colony/custom-mod placement mode; no verified public getter for those was established. SectorPad's own held/latched input is guarded separately by the runtime. Pause ownership preserves a pause already present when SectorPad opens an overlay, but the game's boolean pause API cannot distinguish another mod silently reasserting the same pause value. These boundaries are described further in [game integration](GAME_INTEGRATION.md).

## Remaining acceptance work

Before calling a device/platform combination production-ready, record an actual game run covering:

- Independent LT/RT and simultaneous trigger presses, stick centers/deadzones, slow pointer movement, wired/Bluetooth reconnect, device switching, and the fixed recovery chord.
- Physical keyboard/mouse overlap, alt-tab and lost focus during holds, disconnect during drag/text entry, and neutral acknowledgement before resuming.
- Windowed/fullscreen and DPI changes, long map pan beyond the screen edge, zoom bounds, scroll-pane ownership, click/drag/drop, and modal transitions.
- Physical-controller character creation/text, campaign travel/interactions, cargo transactions, refit controls, deployment/tactical selection, piloted combat, and every command-wheel action in its actual context. The Windows keyboard/mouse checks above establish only their listed pathways.
- Physical remap capture, same-name device calibration, focus/disconnect rollback during previews, and repeat save/load/removal with the intended third-party mod set. Keyboard-driven profile rollback/persistence, Console Apply versus Run and isolated removal have passed.
- SteamOS input delivery, platform libraries, Deck readability, dock/undock, suspend/resume, and a single input-owner configuration.

Automated fault injection tests cover modeled incomplete profile writes. The public storage API does not provide atomic rename, fsync, or cross-process locking; power-loss durability and competing game processes are not certified. [Settings and profile storage details](SETTINGS.md).
