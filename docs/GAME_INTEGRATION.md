# Game integration and evidence

SectorPad adds a combat plugin, one transient campaign listener, one stateless transient campaign frame script, and its own custom panels. It does not replace the game UI, install a ship AI, patch a game class, replace an LWJGL input implementation, change a game native library, or serialize controller objects into campaign saves.

## Installed API evidence

Inspected on 7 September 2026 using the installed `starfarer.api.zip`, `starfarer.api.jar`, and `starfarer_obf.jar`. The installed runtime reports Zulu Java 17.0.10+7, x86-64. The following are static source/signature/bytecode observations; they do not substitute for running the acceptance journey.

| Integration | Verified entry point | SectorPad behavior |
|---|---|---|
| Application and campaign lifecycle | `BaseModPlugin.onApplicationLoad()` and `onGameLoad(boolean)` | Initialize the runtime and register the transient listener and stateless frame script, removing previous instances of their own classes on load. |
| Campaign frame updates | `SectorAPI.addTransientScript(EveryFrameScript)` with `runWhilePaused() == true` | Refresh context before shortcut routing and keep the controller clock scheduled while campaign UI is paused. See [lifecycle evidence](LIFECYCLE.md). |
| Campaign input | `CampaignInputListener.processCampaignInputPreCore(List<InputEventAPI>)` | Advance controller routing before ordinary UI/fleet controls. |
| Campaign rendering | `CampaignUIRenderingListener.renderInUICoordsAboveUIAndTooltips(ViewportAPI)` | Draw the controller overlay above the campaign UI and its tooltips. |
| Combat and title input | `EveryFrameCombatPlugin.processInputPreCoreControls(float,List<InputEventAPI>)` | The title background engine invokes the same callback before the title panel processes input. |
| Title overlay | Existing state's `getOverlayPanelForCodex()` returns a `UIPanelAPI` | Append one transparent, mod-owned render panel. The installed title draws it after the ordinary screen tree even when the Codex is closed. Codex visibility and original panels remain untouched. |
| Combat overlay | `EveryFrameCombatPlugin.renderInUICoords` and `processInputPreCoreControls` | The early rendering callback arms one late pass. Installed 0.98a calls pre-core input after native HUD/target-label rendering and before the display swap; SectorPad draws once there, including while paused. Its ordinary panel does not also draw in combat. |
| Settings panel | `SettingsAPI.createCustom`, `UIPanelAPI.addComponent`, `UIComponentAPI.render` / `setOpacity`, and Luna's `initFromScript` | Mount one mod-owned Luna panel on the active input root. In combat, defer only that panel's early paint, restore its opacity, and render it during the late pass before normal native input. Removal targets only that same panel. |
| Ship piloting | `ShipAPI.giveCommand(ShipCommand,Object,int)` and `CombatUIAPI.setDisablePlayerShipControlOneFrame(boolean)` | Emit the usual ship commands while controller gameplay owns the frame. No velocity/facing/acceleration stats are assigned. |
| Campaign travel | `CampaignFleetAPI.setMoveDestinationOverride(float,float)` | Request a destination in the direction of stick travel, respecting fleet movement. The override flag is transient in the installed fleet implementation. |
| Abilities | `AbilityPlugin.isUsable()` and `pressButton()` | Invoke the same ability-button path when usable. |
| Game screens | `CampaignUIAPI.showCoreUITab(CoreUITabId)` | Open the original fleet, refit, cargo, map, intel, character and outposts interfaces. |
| Tactical objectives | Native `canCreateAssignments()` followed by `CombatTaskManagerAPI.createAssignment(type,target,true)` | Add an objective on the piloted ship's visible hostile target when the native rules permit it, including an open communications channel at zero remaining command points. The normal manager charges the cost. |
| Selected-unit orders | Original warroom `getSelectionManager()` and `rightClickReleased(C,false)` | The tactical hub/selection wheel offers **Order selected here**. It captures the native selected members and map point, revalidates the selection/context, and uses the native move/escort/attack handler. The game owns target interpretation, waypoints, permissions, and costs. |

The copied input-list behavior is significant: `CampaignState.processInput` and the combat plugin dispatcher copy their original event list before notifying plugins. Appending an event to a callback list does not enqueue it into the original game UI. Combat `advance` also receives an unmodifiable list. Vanilla consumers cast events to the game's concrete event type. SectorPad therefore does not implement fake `InputEventAPI` injection or replace the underlying LWJGL mouse/keyboard implementation; the separate bridge uses ordinary focused input output.

The combat draw order is a verified installed-version detail, not an API promise of a late-render callback. In `CombatState.traverse`, normal widget/Codex panels render at bytecode offsets 4546/4554, native target and weapon labels at 4633–4661, the pre-core plugin dispatch at 5157, and the display swap at 8103. The pre-core amount is wall-clock frame time, so pausing simulation does not stop the late pass. The initial zero-delta frame has no pre-core pass. `CombatOverlayPass` pairs a rendered frame with at most one late draw and discards a stale engine. It restores the original opacity of SectorPad's deferred settings panel before native input; original panels are never made transparent or reordered.

When a SectorPad modal is open and the game's existing software-cursor setting and active flag are both true, `NativeCombatCursor` invokes the installed public cursor renderer once more above the modal. That guard avoids its cursor-mode activation branch; the normal chosen cursor, coordinates and assets are retained. It never changes either cursor setting, hides/replaces a native cursor, or invokes the redraw for ordinary native pointer-only frames. The draw uses a public method handle because the installed class's package is a Java keyword. All added GL painting restores its attribute and matrix state. Title and campaign keep their existing render routes. Combat render order and cursor signatures must be rechecked for a different game version.

## Controller behavior

- TwinStick requests a world-relative velocity with the left stick and hull-facing/weapon aim with the right stick.
- Directional turns the hull towards left-stick travel while the right stick controls weapon aim independently.
- Orbital maps left-stick horizontal input to tangent travel around the piloted ship's current visible target and vertical input to approach/retreat. It faces that target by default. Without a valid target it retains ordinary world-relative travel.
- Partial combat stick deflection requests partial velocity. Feedback compares actual velocity with that request and issues normal forward/reverse/strafe commands. Releasing controller-owned travel issues the usual decelerate command.
- A held shield trigger lowers only a shield/cloak raised by that hold. A shield already active before the hold is preserved. Toggle mode retains its state after release.
- Target cycling excludes hidden, neutral, friendly, dead, hulk and untargetable ships. It does not reveal fog-of-war contacts.
- Campaign target cycling uses only visible entities in the player's current location. Interact lays in the ordinary course instead of opening remote encounters directly.
- Refit operations, quantities, trading, fleet selection, map selection, simulation entry, save/load, and colony actions remain normal UI workflows through the pointer/input bridge.
- Fleet Orders and Full Retreat open the original dialogs. Full Retreat retains the game's own confirmation. Retreat Ship explicitly addresses the piloted ship, checks the native direct-order or free-retreat permission, and is marked for a fresh SectorPad confirmation. An open communications channel can permit orders with zero remaining command points.
- Core navigation follows the current encounter's `getCoreUI()` before the standalone campaign core, skips disabled native tab buttons, and preserves nested modal ownership. Encounter-owned maps receive the Map context; replacing the core changes input identity.
- `GameActions.nativeOperationActive()` reads the verified cargo pickup handlers and refit's public picker-dialog flag. `canChangeCoreTab()` also requires an existing core and excludes nested modals. No public warroom waiting-state or generic colony-placement getter was established, so this is not a universal native-operation detector. Runtime guards additionally cover SectorPad's own held/latched input.

## Pause and transition behavior

Pause leases retain the original pause state, engine/sector reference, context identity, nesting depth, focus state and observed independent input epoch. A pre-existing pause is preserved. A new native key/click, an explicit pause request, disconnection cleanup, focus loss, replacement ship, or changed modal owner prevents automatic resumption. An older lease cannot resume a newer context.

Starsector exposes a pause boolean rather than a shared owner registry. A third-party mod silently reasserting the same boolean cannot be distinguished from the existing pause through this API. Compatibility testing must include other pause-controlling mods; no universal ownership guarantee is inferred from the boolean alone.

## Verified control enum names

The installed controls enum contains `GENERAL_PAUSE`, `GENERAL_CODEX`, `GENERAL_EXPAND_TOOLTIP`, `GENERAL_ZOOM_IN`, `GENERAL_ZOOM_OUT`, `FAST_FORWARD`, `SHIP_SHOW_WARROOM`, `C2_TOGGLE_AUTOPILOT`, `C2_SHOW_REINFORCEMENTS`, `C2_SEARCH_AND_DESTROY`, `C2_FULL_RETREAT`, `C2_CANCEL_ASSIGNMENT`, `REFIT_RUNSIM`, `REFIT_WEAPON_GROUPS`, `CORE_CONFIRM`, `TAB_1` through `TAB_0`, and `SUBTAB_1` through `SUBTAB_4`.

LunaLib 2.0.5's campaign settings shortcut is `LunaSettings.getInt("lunalib", "luna_SettingsKeybind_new")`; its CSV default is key code 61 (F3), with Shift+F2 as a campaign fallback. The installed title-screen implementation exposes a Mod Settings button. A guessed `MOD_SETTINGS` game control enum is not a valid replacement for that button.

## Validation limits

`GameIntegrationTests` uses Java dynamic-proxy API test doubles and performs no game launch or input output. It checks steering/velocity requests, neutral handoff, hold/toggle defense ownership, target visibility, stale-action rejection, nested/external pause handling, campaign movement and ability gating. Focused cases cover zero-point native permissions, free retreat, encounter cores/maps, disabled tabs, active pickups, selection changes, and preservation of the chosen world point. Public method-handle signature checks resolve the native command, cargo, core, and warroom entry points against the local jars. `LifecycleTests` separately checks the combat pass's single-draw policy, deferred own-panel restoration, stale-frame rejection and native software-cursor signatures. Live Windows checks passed a selected-unit tactical waypoint order through the hub and an autopilot toggle. At 1280×720, the revised [combat hub](evidence/tripad-combat-hub-1280x720.png) covered the native target labels correctly, and [Controller Setup](evidence/tripad-combat-setup-1280x720.png) supported page navigation and closure over deployment. Physical-controller latency, firing, suspend/resume and trigger independence remain unverified. The optional software-cursor redraw has signature evidence; its active-mode branch has not been exercised live.
