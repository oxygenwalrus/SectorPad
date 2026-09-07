# LunaLib 2.0.5 integration evidence

Inspected installed LunaLib source and compiled API on 2026-09-07. This API investigation read game and installed mod files only. The root integration subsequently performed isolated game launches; runtime findings are identified below.

Installed source root: `C:/Program Files (x86)/Fractal Softworks/Starsector/mods/LunaLib-2.0.5/src`.

## Public settings contract

`lunalib.lunaSettings.LunaSettings` exposes Java static nullable getters `getBoolean(String,String)`, `getInt(String,String)`, `getDouble(String,String)`, `getFloat(String,String)`, `getString(String,String)`, and `getColor(String,String)`. It exposes `addSettingsListener(LunaSettingsListener)`, `removeSettingsListener(LunaSettingsListener)`, `hasSettingsListenerOfClass(Class<?>)`, and `reportSettingsChanged(String)`. `LunaSettingsListener.settingsChanged(String modID)` is the callback. Source: `lunalib/lunaSettings/LunaSettings.kt:31-48,90-267`; `lunalib/lunaSettings/LunaSettingsListener.kt:14-16`. Confirmed with `javap` against the installed `jars/LunaLib.jar`.

The normal integration is `data/config/LunaSettings.csv`. Header:

```csv
fieldID,fieldName,fieldType,defaultValue,secondaryValue,fieldDescription,minValue,maxValue,tab
```

The loader finds this file for each enabled mod and namespaces values by the mod ID; CSV rows do not need a `modID` column. Source: `lunalib/backend/ui/settings/LunaSettingsLoader.kt:50-75`.

Rendered setting types are `String`, `Int`, `Double`, `Boolean`, `Color`, `Keycode`, `Radio`, `Text`, and `Header`. Although `SettingsCreator.addEnum` exists and the enum contains `Multichoice`, neither has a rendering branch in the installed settings panel. Do not ship either type. Source: `lunalib/backend/ui/settings/LunaSettingsUISettingsPanel.kt:32-34,369-377`.

`Radio` uses a comma-separated `secondaryValue` choices string and a single string `defaultValue`. The Java runtime creation signature is `LunaSettings.SettingsCreator.addRadio(String modID, String fieldID, String fieldName, String tooltip, String defaultValue, String secondaryValue, String tab)`. Static CSV is preferable for fixed settings. Source: `lunalib/lunaSettings/LunaSettings.kt:375-379`; `lunalib/backend/ui/settings/LunaSettingsUISettingsPanel.kt:579-588`.

## Controller remapping boundary

Luna's `Keycode` control listens only to `InputEventAPI.isKeyDownEvent()` and stores LWJGL keyboard integers. It cannot record controller buttons, axes, or chords. Source: `lunalib/backend/ui/components/LunaUIKeybindButton.kt:119-158`.

There is no public custom-field registration, action-button creation, settings setter, or custom-tab callback API. The native field renderer has a closed `when` branch. Do not modify/refelectively replace Luna's settings panels, listeners, or settings map to simulate these features.

Use native Luna fields for comfort, pointer, input, and calibration preferences. Use an added custom Luna panel for controller action remapping, capture, conflict resolution, profile selection, validation, and a 15-second revertable preview. The panel is a new SectorPad-owned UI surface and must not replace any game or Luna UI elements.

`LunaBaseCustomPanelPlugin` is a public Java-compatible abstract class. Relevant compiled methods:

```java
public abstract void init();
public final void init(CustomPanelAPI panel,
    CustomVisualDialogDelegate.DialogCallbacks callbacks,
    InteractionDialogAPI dialog, boolean openedFromExistingDialog);
public final void initFromScript(CustomPanelAPI panel);
public final CustomPanelAPI getPanel();
public final boolean isOpenedFromScript();
public void advance(float amount);
public void processInput(List<InputEventAPI> events);
public void onClose();
public final void close();
```

`LunaElement(TooltipMakerAPI, float width, float height)` is open and can be subclassed from Java to override `onClick(InputEventAPI)`, `processInput(List<InputEventAPI>)`, and rendering methods. No Kotlin compiler is required. Its Kotlin callback overloads need the Kotlin runtime, already a transitive Luna dependency; Java subclass overrides avoid source-level Kotlin lambdas.

LunaElement dispatches `onClick` for every mouse-down event within its bounds and does not first check whether an event was consumed. SectorPad's own button callback checks consumption, left mouse button, double-click, and panel lifetime before acting. Source: `lunalib/lunaUI/elements/LunaElement.kt:278-340`.

## Availability and mounting

Native CSV settings are available through LunaLib's existing title-screen **Mod Settings** button and through its campaign settings command. Luna adds that title button itself. Source: `lunalib/backend/scripts/CombatHandler.kt:164-205,285-351`; `lunalib/backend/scripts/KeybindsScript.kt:45-65`.

Java can call `lunalib.lunaExtensions.SectorExtensionsKt.openLunaCustomPanel(SectorAPI, LunaBaseCustomPanelPlugin)` despite the Kotlin source comment claiming the extension file is Kotlin-only. Verified from the installed jar with `javap`. This delegates to a new public campaign interaction dialog. Source: `lunalib/lunaExtensions/SectorExtensions.kt:11-13`.

For a title-owned SectorPad panel, the caller must supply an already verified public `CustomPanelAPI` parent/mount. Luna's own title settings code obtains the title-screen root by reflection; that is not an endorsed general public opening API. Keep title mounting outside settings/model persistence and require capability verification before using a private adapter.

Do not blindly call `super.processInput(events)` for a script-mounted custom panel. Luna's base implementation handles Escape by setting the shared `CombatHandler.canBeRemoved` flag used for Luna's native title panels. Its final `close()` also clears both shared Luna selection maps. The SectorPad panel owns its script-mounted close path through `onClose()` and removes only its own added component. Source: `lunalib/lunaUI/panel/LunaBaseCustomPanelPlugin.kt:152-164,190-220`.

Native Save updates its in-memory settings before invoking listeners. New-game configuration does **not** invoke settings listeners; reload settings in `onGameLoad` as well as in the listener. Source: `lunalib/backend/ui/settings/LunaSettingsUIModsPanel.kt:621-624`.

The native Reset button iterates every settings row belonging to the selected mod, regardless of tab, and stages all default values. It provides neither a public section-reset API nor a setter for another mod to call. Source: `lunalib/backend/ui/settings/LunaSettingsUIModsPanel.kt:159-184`. SectorPad's section resets therefore own their default overrides and clearly label them in the added panel; they never edit Luna's fields, changed-settings list, or common-data file.

## Persistence boundary

Luna saves native preferences through LazyLib common data at `LunaSettings/<modID>.json`; it does not use campaign save data. Source: `lunalib/backend/ui/settings/LunaSettingsLoader.kt:141-193`; `lunalib/backend/ui/settings/LunaSettingsUIModsPanel.kt:621-624`.

Do not directly write Luna's private settings map. SectorPad action profiles and device calibration are independently versioned JSON using the public game common-data API. An isolated runtime launch rejected direct NIO access with `SecurityException: File access and reflection are not allowed to scripts` in `ProfileStore.readWithBackup`; the implementation now isolates NIO into a headless-only backend. The supported methods are `SettingsAPI.readTextFileFromCommon(String)`, `writeTextFileToCommon(String,String)`, `fileExistsInCommon(String)`, and `deleteTextFileFromCommon(String)`. The source documents a 1 MiB write limit. Source: extracted `com/fs/starfarer/api/SettingsAPI.java:401-412` from the installed API source archive.

The runtime backend verifies alternating checksum/generation snapshots and commit markers, retains a last-good backup, and rejects future schemas. It makes no atomic-rename/fsync claim because the common API does not provide those operations. It stores no game objects or campaign IDs. Directory enumeration is also absent from the API; imported files use an explicit `SectorPad/imports/index.json` list.

Preference changes that affect mapping must route through a release-all/rearm barrier before replacing runtime state. Preview mappings must remain uncommitted until explicit confirmation; timeout, panel closure, device loss, focus loss, or application exit must restore the previous active profile.

## Verification limits

The installed API and sources establish signatures and implementation behavior. They do not establish controller operation, rendered layout, title mounting safety, save/remove behavior, or real device behavior. Those require the root integration's runtime/hardware acceptance pass.
