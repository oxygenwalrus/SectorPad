# Lifecycle ordering and campaign updates

Inspected on 7 September 2026 against the installed Starsector 0.98a-RC8 Java 17 API and game classes. These are source, signature, bytecode, and API-double observations. The lifecycle checks do not launch a game or establish physical-controller behavior.

## First shortcut after a native screen change

`RuntimeHooks.beforeInput` refreshes the runtime context, attaches the mod-owned overlay to that context, and then routes the physical input batch. The order is `advance -> ensureAttached -> processInput`.

Previously, a shortcut could open a wheel using the previous screen's context and then run the context update. That update could immediately cancel the newly opened wheel. The regression test simulates a campaign-to-cargo context change and verifies that the first shortcut belongs to cargo and survives the following frame. The direct correction is the ordering change; the extra campaign script described below is an independent lifecycle source.

Null native event batches become empty lists. If context refresh fails, input routing does not run. Both input and campaign frame callbacks use the existing runtime error boundary and emergency cleanup.

## Public campaign registration

`SectorPadModPlugin.onGameLoad(boolean)` installs `SectorPadCampaignFrame` with these public `SectorAPI` operations:

```java
void removeTransientScriptsOfClass(Class scriptClass);
void addTransientScript(EveryFrameScript script);
```

The script implements:

```java
boolean isDone();
boolean runWhilePaused();
void advance(float amount);
```

`runWhilePaused()` returns true. `advance` runs only while SectorPad is enabled and `Global.getCurrentState()` is `GameState.CAMPAIGN`; the runtime owns real elapsed time instead of using campaign simulation time. `isDone()` becomes true when the runtime is disabled. The script has no fields, so it retains no controller, UI, runtime, or campaign object. Registration removes previous instances of this script class, then installs one replacement. Other scripts are untouched. The existing input/render listener also remains transient.

The runtime's existing one-millisecond monotonic duplicate guard is unchanged. Campaign input and engine script callbacks can both request an update; no background thread, timer, or installed game modification is introduced.

## Installed callback evidence

The initial suspicion that campaign pre-core input runs only when the native event list is nonempty is not supported by the inspected implementation.

| Installed method | Observed behavior |
| --- | --- |
| `BaseGameState.traverse()` | Calls `processInput` at bytecode offset 402 when no next-state transition is pending. There is no event-count condition around this call. The state `advance` call at offset 458 is conditional on nonzero elapsed time. |
| `CampaignState.processInput(...)` | Copies the native events and calls `ListenerUtil.processCampaignInputPreCore` at offset 17, without checking event count. |
| `ListenerUtil.processCampaignInputPreCore(List<InputEventAPI>)` | API source loops over the sorted campaign input listeners and invokes each listener. It does not require a nonempty event batch. |
| `CampaignState.advance(...)` | Reaches `CampaignEngine.advance` at offset 1497. Core UI, interaction dialogs, and campaign pause do not add an early return before this call. |
| `CampaignEngine.advance(...)` | Copies `transientScripts` at offsets 923–934, checks `isDone`, checks `runWhilePaused` at offset 987, and calls the script's `advance` at offset 1005. A true `runWhilePaused` bypasses the engine pause check. This script pass does not depend on the native event count. |
| `CampaignEngine.transientScripts` | The installed field is declared `private transient List<EveryFrameScript>`. |
| `TitleScreenState.processInput(...)` | Calls the combat plugin input dispatcher at offset 39 when a background combat map exists. This precedes Codex/native panel handling. A pending mission transition can return early; there is no event-count condition on the dispatcher call. |
| `CombatState.traverse()` | Calls the same dispatcher at offset 5157 when elapsed time is nonzero and a combat map exists. Its private zero-argument `processInput()` is an empty helper, not the actual plugin dispatch path. |
| Combat dispatcher `com.fs.starfarer.combat.A.B` | The `(float, native input list)` method invokes `EveryFrameCombatPlugin.processInputPreCoreControls` at offset 109 for registered plugins, without an event-count check. |

These offsets identify the inspected binaries, not a compatibility contract for future game versions. The public API source entries are `com/fs/starfarer/api/EveryFrameScript.java`, `com/fs/starfarer/api/campaign/SectorAPI.java`, and `com/fs/starfarer/api/campaign/listeners/ListenerUtil.java` in the locally installed `starfarer.api.zip`. The full proprietary source and bytecode dumps are not included in SectorPad archives.

Inspected input SHA-256 values:

- `starfarer.api.jar`: `a7ba18f3476ffe704729bd0a7a47443f035fea98a32ac2930eae8b391d013c2a`
- `starfarer_obf.jar`: `5dd222b9e266d2ac2d63b3dad4983eb05caaf5a247d7dfb82aaeba47ea774cc8`

The game can deliberately idle its main loop while inactive or transitioning. This mod does not run callbacks while the game itself is not advancing, and the added script does not establish a suspended-window or operating-system suspend/resume guarantee.

## Automated coverage

Recoverable failures at SectorPad input, frame, rendering, setup and settings boundaries disable the mod for the session before cleanup. Cleanup attempts each owned resource independently, so a failed release or preview rollback does not skip later backend/bridge closure. It also attempts a bounded diagnostic export using the game's common-data API. A live intentional call into this failure boundary wrote the report and left the native console/campaign responsive. Fatal VM/native failures are outside this recovery path; no global exception handler is installed. Windows foreground ownership gates direct game actions as well as keyboard/mouse output.

`sectorpad.game.LifecycleTests` passes 27 checks compiled with `--release 17` against the installed game and LunaLib jars. It verifies first-shortcut context ordering, failure-before-input behavior, null event batches, paused scheduling policy, active-state gating, absence of retained script fields, duplicate transient registration, preservation of unrelated scripts, and repeated-load replacement. Combat checks cover a single late draw per frame, duplicate callbacks, stale engines, restoration of the deferred mod-owned panel, and the installed public software-cursor signatures. Its `SectorAPI` implementation is a test double. It does not initialize SDL or send desktop input.

The full builder discovers this test class automatically. In an isolated Windows campaign, the first F8 after entering native cargo opened the hub and it remained open on subsequent frames. Saving with both transient hooks active completed successfully. After the complete SectorPad directory was removed and the game restarted, that same save loaded into the paused campaign with its date and resources retained. See [removal evidence](evidence/campaign-loaded-without-sectorpad.png). Physical focus/disconnect cleanup and operating-system suspend/resume remain separate device acceptance checks.
