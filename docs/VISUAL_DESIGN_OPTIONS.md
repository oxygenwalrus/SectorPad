# SectorPad visual design options

Status: **TriPad selected by the user and implemented in SectorPad-owned UI**, 7 September 2026. Survey and Shipboard remain archived design alternatives; they are not selectable runtime skins.

Open [the self-contained comparison](visuals/concept-sheet.html). Each direction includes the pointer companion, keyboard, navigation selection, and manual / exact-point / locked reticles. The SVGs and PNGs are 1280 × 800:

- [TriPad SVG](visuals/tripad.svg) · [PNG](visuals/tripad.png)
- [Survey SVG](visuals/survey.svg) · [PNG](visuals/survey.png)
- [Shipboard SVG](visuals/shipboard.svg) · [PNG](visuals/shipboard.png)

The comparison uses the same specimen layout to make the style differences clear. It is a composite design sheet, not a claim that the keyboard, fleet list and combat reticle appear together in game.

## Recommendation

The user selected **TriPad**: cyan controller cues, amber focus, pale text, and restrained chamfered edges. It shares the installed game's visual vocabulary and works with Luna's existing native colors. The other two directions are retained below as design history.

The implementation is in [OverlayRenderer.java](../src/main/java/sectorpad/ui/OverlayRenderer.java), [TripadTheme.java](../src/main/java/sectorpad/ui/TripadTheme.java), and [OverlayLayout.java](../src/main/java/sectorpad/ui/OverlayLayout.java). Runtime supplies pointer ownership, clipped focus bounds and precision state; the console adapter reserves the exact shared dock height. Gameplay actions, text-entry state and original game assets were not changed by the visual implementation.

## Evidence from the installed game and current mod

All game paths below are relative to the read-only installation's `starsector-core` directory. Existing game assets were inspected; none were copied into these concept files.

| Evidence | What it establishes |
|---|---|
| `data/config/settings.json:1055–1062` | `buttonBg` is `#46DEFF`; `buttonText` is `#AADEFF`; `buttonShortcut` and `hColor` are `#FFD200`. The default font is `graphics/fonts/insignia15LTaa.fnt`. |
| `data/config/settings.json:1092–1120` | The UI uses pale cyan highlights, cyan widget borders, `#DCDCDC` standard text, and distinct existing friend/enemy colors. Controller selection should not borrow friend/enemy semantics. |
| `graphics/cursors/cursor_blue_2x.png`, `graphics/hud/cursor.png` | The installed pointer language is a sharp triangular arrow with dark separation. Keep the native arrow and its hotspot intact. |
| `graphics/ui/tripad_top_left_decor.png`, `graphics/hud/player_status_bg2.png` | Fine metal bevels, clipped corners and a small amount of bright edge detail fit the game. Large glows, scanning lines and ornamental spinning rings would add visual noise. |
| `graphics/ui/buttons/button_over.png` | Hover is a simple material/value change with an edge, useful for the new keyboard keys. |
| `graphics/fonts/insignia15LTaa.fnt`, `insignia21LTaa.fnt`, `orbitron20aa.fnt` | Insignia is appropriate for ordinary controls; Orbitron can be reserved for a short heading if needed. Font atlases already exist in the installed game. |
| [OverlayRenderer.java](../src/main/java/sectorpad/ui/OverlayRenderer.java) | The selected TriPad implementation uses native-style cyan / amber tokens, Insignia 17 and 21, bevelled panels, a pointer companion, focus brackets and distinct manual / exact-point / locked aim shapes. |
| [LunaRemappingPanel.java](../src/main/java/sectorpad/settings/LunaRemappingPanel.java#L314) | Luna buttons already use `Misc` text, highlight and player colors. The current `ControllerDiagram` at line 329 is a live grid of labelled tiles. |
| [Existing local setup screenshot](evidence/luna-controller-setup.png) | Confirms the installed cyan / amber appearance in an actual Luna panel. This existing screenshot was read; no live UI operation was performed for this study. |

Palette values called out below are design tokens. Dark panel fills and steel tones are new choices, not claims that every value is a native setting.

## Three coherent directions

| | TriPad | Survey | Shipboard |
|---|---|---|---|
| Role | Suggested default; closest match | Optional lighter treatment | Optional handheld clarity treatment |
| Base / panel | `#0B141B` / `#0D202C` | `#09151D` / `#102631` | `#101313` / `#181D1C` |
| Text / muted | `#DCDCDC` / `#A4BEC9` | `#DFF6FF` / `#A9C7D3` | `#F4F5E9` / `#C4CDC8` |
| Controller cue | `#46DEFF` | `#9BE4FF` | `#E1E4D3` |
| Selection | `#FFD200`, edge plus notch | `#78E6EC`, outline plus short rail | `#FFD200`, solid fill on own keys; pale outline on native controls |
| Shape | Chamfer, bracket, angular notch | Open arc, shallow curve, short rail | Strong rectangle, wide corner, small mechanical lock |
| Best quality | Continuity with native menus | Low visual weight | Clear state changes and stronger text |
| Tradeoff | Fine cyan needs a dark under-stroke in combat | The most delicate marks on busy backgrounds | More opaque geometry covers more of the scene |

### Pointer companion

Each concept draws **around the original pointer**. It does not replace a cursor PNG, call a cursor replacement API, hide the original arrow, or change the actual hit point.

- **TriPad:** two pairs of open cyan corners. Precision adds four amber ticks.
- **Survey:** a broken cyan halo. Precision adds sparse outer ticks.
- **Shipboard:** larger pale corners with one lower amber bar in pointer mode and two bars in precision mode.

The arrow in the concept is a newly drawn reference shape representing the unchanged native arrow. In game, SectorPad draws only the surrounding marks. Use roughly 42–48 logical pixels of width with 2–3 pixel strokes and a 3–4 pixel dark under-stroke. The exact companion offset must follow the displayed native arrow so its upper-left hotspot remains unobscured.

Show a short “Precision pointer” or “Dragging” status in SectorPad's existing footer when a mode changes; do not leave a word attached to the pointer over native text. A held drag may add a small solid lower bar; a latched drag needs a separate explicit latch shape and a visible cancel prompt. Color cannot be the only distinction. Suppress the companion on physical mouse takeover, focus loss, or unsupported ownership.

### On-screen keyboard

All three keyboard studies preserve the current five character rows and five footer actions. The selected key has an unmistakable border plus a notch, underline, or inverted fill. The dark text field separates the staged text from the key grid. “Accept” stays an explicit action; selecting a character cannot submit a field.

The shown panel is **968 × 455 logical pixels**, with **46–48 pixel key faces**, a 38-pixel text field and 22–24 pixel characters. It uses about 57% of an 800-pixel-high screen. Place it above or below the active native field, and move it when necessary to keep that field visible. A larger type option may need a taller panel or fewer visible rows with explicit paging.

The previous docked renderer used `kh = min(height * .52, 570 * scale)` and `cellH = min(49 * scale, (kh - 210 * scale) / (rows + 1))`. At 1280 × 800, scale 1, and five letter rows, that made visible key faces approximately **30.3 pixels high**. The implemented layout now uses a **968 × 460** panel with **46-pixel keys** at scale 1. Its docked fit preserves at least **170 logical pixels above the panel** at supported 720p / 800p sizes. At larger overlay scales the dock shrinks only as necessary to retain that console clearance. The centered keyboard can use the full available screen. `OverlayLayout` supplies both actual key rectangles and the console reservation, so drawing, clicks and clearance agree.

Use the same visual vocabulary for the numeric keypad, with a clearly labelled integer amount and maximum where the native transfer permits whole counts. A cargo variant should say “Pick up 37” or equivalent after native availability is known; it must not imply that accepting the number completes a trade. Show native read-back progress separately if pickup takes multiple frames. This is a visual proposal, not a change to the quantity adapter.

### Navigation selection

Draw an outline **outside the visible clipped bounds** of the current focus target. It must follow the top modal, scroll with its pane, and disappear when that target becomes hidden, disabled or stale. The concept's native rows are illustrative shapes; the production overlay leaves their text, fill and normal hover behavior untouched.

- **TriPad:** amber corners plus an outside leading triangle.
- **Survey:** pale cyan outline plus a short lower rail.
- **Shipboard:** strong pale rectangle plus amber leading triangle.

A controller confirm glyph may sit in free space next to the row. Omit it when it would cover another native control. Use an independent 3–6 pixel inset/outset treatment for borders, not a replacement fill over vanilla content. Remove the directional-focus outline when deliberate pointer movement takes over. Multi-selection needs a separate membership indicator; the focus border only means “next A target.”

### Aiming reticle

Use a **34-pixel span**, roughly **14 pixels of open centre**, and a dark under-stroke in all directions. Keep the aim point exact. The current four-arm reticle is a good starting shape.

- Manual: open cross or four open arcs.
- Exact world point: added inner corners and a tiny point.
- Locked: a separate diamond or lock shape and the genuine locked-target state.

Reticle size should stay readable in logical screen units as the camera zooms. Do not add a lead indicator, target prediction or firing-permission appearance unless gameplay actually supplies that data. A target leaving the viewport needs a distinct edge chevron; do not show an ordinary crosshair at a clamped screen coordinate that differs from its world point.

## Font, prompts and controller diagram

Use installed **Insignia 21** for 20–22 pixel action text and **Insignia 17** for secondary prompts; use an appropriately sized native font atlas where possible instead of scaling one small bitmap for every role. Shipboard can start at 23–24 pixels for key characters. Keep Orbitron limited to a short, occasional panel title. This review sheet uses system Bahnschrift / Arial Narrow as a portable approximation, so exact in-game font widths still need a render check.

Preserve the Xbox physical face-button arrangement used by Xbox and Steam Deck-style controllers. A/B/X/Y stay readable as letters inside consistent simple button shapes; never rely on the red/green button-color convention. LB/RB and LT/RT need distinct shoulder/trigger silhouettes if icons replace the current tiles. Stick movement is a ring with an offset dot; stick press is the ring plus a second inward mark. Keep the digital stick-click state separate from the continuous axis dot.

The live Luna remapping diagram keeps its existing labelled tiles and native cyan / amber colors in this implementation. A controller outline, cross-shaped D-pad and circular axis plots remain an optional design extension, not a shipped claim. Such an extension must retain every existing live value and capture state and choose a device layout from its known profile rather than guessing a physical model from a generic SDL name.

All prompts must resolve the user's effective binding. The sheet's A/B/X/Y examples do not authorize hard-coded glyphs after remapping. A disabled action needs a reason, not just dimmed lettering.

## Motion and accessibility at 1280 × 800

- Position and selection must update immediately. An optional 80–120 ms edge fade can follow that change, but cannot delay input, move the hotspot with a spring, or carry focus past its actual target.
- Keep lock, precision, latched drag and autopilot indicators stable. Avoid continuous pulse, rotation, scanning effects and blinking text.
- Respect reduced motion with an immediate static state. Hold progress, if displayed, communicates a real timed operation only.
- Keep ordinary actions at 18–22 pixels and secondary help at 16–17 pixels. The small artifact captions are review annotations, not a proposed minimum for gameplay controls.
- Every focus / reticle mark has a dark under-stroke. Opaque panel text is designed for strong contrast; combat needs a separate bright-effect and bright-nebula check.
- Use 44-pixel or larger hit areas for SectorPad-owned keyboard / overlay controls. This does not enlarge or mutate native controls.
- Size and stroke width can scale separately from text. Preserve a clear reticle centre and keep overlays within UI safe bounds at different display scales.
- Do not use friend/enemy red or green as arbitrary controller focus. Shape and text must still distinguish every state in grayscale.
- Prefer the game's existing interface sounds for focus and acceptance, rate limited to state transitions. Sound is optional feedback, never the only state signal.

## Additive implementation boundary

The selected implementation changes only SectorPad's visual code and its visual integration inputs:

1. `TripadTheme` defines cyan, amber, steel, text and dark panel tokens. Wheels, keyboard, dialogs, status and diagnostics use those tokens and new vector bevels.
2. `setNavigation(Pointer, Focus)` accepts the original pointer hotspot and a clipped, validated focus rectangle. The renderer draws surrounding marks only and hides native focus brackets behind its own modals. A multi-select plus mark reports the mode, not unit membership.
3. The extended `Aim(x, y, label, locked, precision)` preserves the existing four-argument constructor. Manual and precision shapes differ; locked aim adds a diamond and label. Off-screen coordinates use a directional edge cue instead of moving an ordinary crosshair away from its world point.
4. `OverlayLayout` owns keyboard rectangles, hit tests, scale fitting and exact dock clearance. It preserves the current character rows, footer actions, text model and quantity safeguards.
5. Original cursor, font and UI files remain untouched. The renderer loads installed Insignia 17 / 21 through existing font resource paths, and restores GL attributes and matrix stacks after drawing.

## Verification

All three SVGs were rendered locally to 1280 × 800 PNGs and visually inspected for fit, legibility and state distinction. The concepts contain only new vector geometry and text; the portable HTML embeds all three SVGs and has no external assets or network requests.

The HTML comparison was also checked in a separate headless Edge session: all three direction buttons, comparison display, return to a single direction, and 1:1 sizing worked; the measured 1:1 SVG width was 1280 pixels. A fresh load had zero console errors or warnings. Browser evidence is under `output/playwright/visual-study/`; the temporary localhost preview server and that browser session were closed afterward.

The implemented renderer compiled against the installed Starsector / LazyLib jars for Java 17. [OverlayGeometryTests.java](../src/test/java/sectorpad/ui/OverlayGeometryTests.java) passed **24,076 assertions** covering 720p / 800p, display and overlay scaling, exact typed-character hit mapping, console clearance, focus clipping, off-screen aiming and text contrast. These are logic / geometry checks, not game screenshots.

Live Windows checks covered the TriPad wheel, keyboard and quantity panel at 1280 × 800, then the wheel and docked keyboard at 1280 × 720. At 720p, the maximum supported overlay scale of 1.8 retained space for console output and correct key hit positions. The combat hook now defers SectorPad's single paint until after native target labels; the revised hub and setup panel passed a live simulation retest at 720p, including setup page navigation and closure. Native Luna settings remained unobscured after the connection hint was suppressed while that menu is open. See the [current evidence](COMPATIBILITY.md). Pointer companion, focus and aiming marks remain geometry-only evidence without a physical controller; the optional software-cursor redraw has signature checks but no live active-mode check. These keyboard/mouse observations do not establish hardware-controller usability or every combat effect.
