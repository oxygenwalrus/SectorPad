# Controller refit workspace

SectorPad adds an in-game companion window over native refit. The initial adapter targets **Starsector 0.98a-RC8**. It opens automatically when controller input is active; an attached idle controller does not take over keyboard/mouse refit. Open it manually through **F8 / View → Controller workspace** while in refit.

## Use

The five sections are Ship & mounts, Hullmods, Flux & stats, Weapon groups, and Designs & actions. Ship identity and remaining OP stay visible. The ship selector is a focused row as well as a pointer target. Selecting a ship returns to its fitting. Modules use their native slot identifiers; choose the parent ship in the ship selector to return from a module.

| Default REFIT binding | Workspace operation |
| --- | --- |
| D-pad | Move through rows; left/right move through the list |
| A / B | Select / close details, leave ship selector, or return to native refit |
| LB / RB | Previous / next section |
| Left stick / right stick | Pointer / scroll list |
| L3 / R3 | Precision pointer / expanded details |
| Y / View | Designs & actions / command hub |
| Menu | Yield to native refit and send the normal game-menu key |

Keyboard arrows and Page Up/Down navigate, Enter selects, F1 expands details, and Escape returns to native refit. Mouse clicks and wheel scrolling also work. Bindings are remappable under **REFIT** in LunaLib and Controller Setup. Each schema-1 profile inherits its own UI bindings during migration, including custom and southpaw profiles. The Luna **Refit** tab includes the automatic-opening setting. Existing pointer, repeat, scroll and overlay comfort settings apply; Controller Setup's navigation reset includes the refit preference.

## Native editing and fallback

Selecting a fitting operation hides the workspace and releases SectorPad-held inputs. The adapter reveals the original target, sends an ordinary hover, waits for native pointer polling, then revalidates the owner, ship/module, variant, modal, inventory revision, target availability and pointer position before clicking. Native refit enables weapon mounts through its hover handler; SectorPad does not enable their widgets itself.

Finish or cancel the native dialog to return to a refreshed workspace. One-shot allocation/undo actions return after their input interval. Simulation retains native deployment, flight, exit menus and confirmations, then restores the workspace. Native ship-change warnings can cancel the requested change; SectorPad does not replay that change after the warning closes.

**Native refit** suppresses automatic reopening for that visit. Text fields, officer controls and third-party Additional Options deliberately stay native; reopen the workspace through the hub when finished. Focus loss, disconnect and errors release held input and suppress pending reopening. Unknown or missing controls remain accessible with the native precision pointer. The details pane explains unavailable actions. An unrecognized game version leaves the companion adapter unavailable and preserves native refit.

Hullmod table navigation recognizes the inspected native row type inside its owning modal. Disabled or restricted rows retain pointer inspection; unknown third-party rows are not treated as actionable merely because they appear in a list. Other native dialogs use the existing focus navigator restricted to the top native modal.

All new window state is transient and mod-owned. There are no direct writes to variants, cargo, credits, story points or refit dirty flags, no native listener replacement, and no patched or redistributed game classes. Native purchase/refund rules, OP limits, fitting restrictions, confirmations, restoration and saving remain authoritative.

## Verification record — 7 September 2026

Testing used a separate game copy, separate logs/saves and a copied Java runtime under `build/refit-verification/Starsector`. Installed game and existing-mod files were not edited. The original installation's 60,507-file baseline had **zero changed sizes/timestamps and zero missing files** after testing. This is a metadata integrity comparison, not a cryptographic comparison of every installed asset.

| Check | Evidence |
| --- | --- |
| Compilation and automated checks | Full Java/native build and test suite; dedicated refit lifecycle, identity invalidation, immutable snapshot, capability and logical-layout checks; profile migration regressions; native hover/focus regression |
| 1280×720 and 1280×800 | Workspace rendered in game; five sections, paged list, details and footer fit. Native ship sprite origin aligns mount markers |
| Weapon operation | Workspace → native PD Laser picker → remove → refreshed empty mount and 3 OP → owned PD Laser picker → install → refreshed fitted mount and 0 OP |
| Fighter operation | Condor bay 1 → native Talon picker → remove/refund → 2 OP → reinstall owned wing → 0 OP |
| Allocation and undo | Native vent button changed 30 to 29 and exposed 1 OP; workspace Undo restored 30 and 0 OP |
| Hullmods | Workspace opened native hullmod table; removed Accelerated Shields and installed Armored Weapon Mounts; native OP and armor updated. Native S-mod mode exposed zero-story-point restriction and cancellation |
| Weapon groups and warnings | Native ship-switch warning remained visible; entered group editor from it, changed Linked to Alternating, confirmed, and returned to workspace. A subsequent explicit ship choice switched successfully |
| Simulation | Workspace → native simulation/deployment → tactical/flight → native End Simulation confirmation → restored workspace with live fitting |
| Ships, scrolling, modules | Selected carrier through native fleet control. Revealed an offscreen fleet entry. A base station added by Console Commands solely as a disposable test fixture exposed selectable and unavailable module slot IDs; selected a Heavy Combat Module and returned to its parent through native controls |
| Focus and native choice | Focus loss yielded input to native UI; manual hub entry restored the workspace. Choosing Native refit did not immediately reopen it |
| Save removal | Quicksaved the refitted campaign with SectorPad active, restarted the isolated game with SectorPad removed, and loaded successfully into the paused campaign with the same credits, supplies and fuel |

The station fixture is not shipped with SectorPad. Its presence in the disposable save does not establish compatibility with arbitrary modular-ship mods.

## Acceptance still required

Physical controller and handheld refit journeys at both resolutions remain unverified. Also pending are market/encounter-owned refit, market purchases and refunds, representative third-party additions, long native equipment lists, full autofit/saved-variant/strip/restoration workflows, and S-mod confirmation with available story points. Native pathways are wired for these actions, but the table above is the extent of the recorded live acceptance. Automatic opening, suppression, missing capabilities and stale identity behavior have headless coverage; their full physical mixed-input acceptance is separate.

Build with `python tools/build.py` using a Java 17+ JDK. To reproduce isolated tests, use `tools/stage-verification.py --game GAME --target TEST --console`, copy the game's `jre` directory into `TEST/jre`, then use `tools/launch-verification.py --game GAME --target TEST --runtime TEST/jre/bin/javaw.exe --resolution 1280x720` (or `1280x800`). Alternatively, omit `--runtime` to read the installed Java runtime. Use `--no-sectorpad` when staging the removal/load check. Never point TEST at the installed game.
