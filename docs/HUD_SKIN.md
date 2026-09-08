# Native HUD frame skin

SectorPad includes 57 original textures for native panel, frame, decorative chassis and tab surfaces. The refined design combines Starsector's compact industrial instruments with the clean depth and restrained lighting of modern science-fiction interfaces. Dark navy recesses separate matte control surfaces from thin steel shells; clipped corners and short, subdued cyan edge lights give the chassis a consistent construction. Native text, icons, gauges, hover states and interactions continue to be drawn and handled by Starsector.

The visual reference is the readable instrumentation of Star Citizen and Mass Effect, interpreted as original geometry for Starsector's existing layouts. There are no copied franchise assets, decorative text, animated scanlines or baked selection states. A dark gap between shell and inner rail gives panels depth without gradients or visual noise. Bottom-menu light strips sit at the shared housing ends; individual controls stay neutral until the native game draws their state.

This skin intentionally supplies art at selected standard native texture paths through normal mod resource loading. It does not change files in the installed game or another mod. Texture selection occurs when the game loads its resources; the native skin applies while SectorPad is enabled, including keyboard/mouse use. It is not swapped dynamically when the last input device changes.

## Original design and coverage

The generator draws geometry from scratch. It does not read, copy, recolor, crop or redistribute original game or AashPad artwork. The inspected game provides compatibility facts such as asset names, canvas sizes, transparent tile gutters and native button positions. AashPad was inspected for its mod-resource approach and overlapping path inventory, not used as a source of pixels.

| Native texture family | Files | Canvas size | Coverage |
| --- | ---: | --- | --- |
| `graphics/ui/bgs/panel00_*.png` | 9 | 32 × 32 each | Opaque matte panel with the native nine-pixel exterior gutter |
| `graphics/ui/bgs/panel01_*.png` | 9 | 32 × 32 each | Outline-only panel; center and inner cutout remain transparent |
| `graphics/ui/bgs/ui_border1_*.png` | 8 | 8 × 8 each | Compact framed controls |
| `graphics/ui/bgs/ui_border3_*.png` | 8 | 4 × 4 each | Fine inset borders |
| `graphics/ui/bgs/ui_border4_*.png` | 8 | 4 × 4 each | Fine overlay borders |
| `graphics/ui/bgs/ui_border1b_*.png` | 6 | 16 × 8 each | Left/right side extensions, preserving their clear outer regions |
| `graphics/ui/tripad_bot_button_cap_left.png`, `..._right.png` | 2 | 8 × 34 each | Native bottom-menu end caps |
| `graphics/ui/tripad_bot_left_button_fill.png` | 1 | 121 × 20 | Matte tab fill surface |
| `graphics/ui/tripad_bot_left_button_widget2.png` | 1 | 914 × 35 | Seven-slot bottom-menu chassis |
| `graphics/ui/bottomright_location_name_field.png` | 1 | 272 × 30 | Location-name backing field |
| `graphics/ui/tripad_top_left_decor.png` | 1 | 32 × 31 | Top-left chassis corner with clear lower-right area |
| `graphics/ui/tripad_top_right_power_button_bg.png` | 1 | 64 × 32 | Top-right chassis with clear native power-button socket |
| `graphics/ui/tripad_bot_left_decor_extended.png` | 1 | 280 × 32 | Unbranded bottom-left rail, preserving the clear space above |
| `graphics/ui/tripad_bot_right_decor2.png` | 1 | 205 × 19 | Bottom-right chassis rail |

The bottom-menu chassis follows the installed native layout: first button at x=13, width 122, six-pixel spacing and y=4 from the bottom. Its frame surrounds those existing controls. Texture alpha is visual transparency; no input or hit-test mask is installed or changed.

The four decorative pieces follow the native core's existing placements: top-left, top-right, above-left of the bottom-menu holder, and bottom-right. They contain original structural geometry without baked branding. The native power button and glow remain separate original sprites, and the background socket stays clear. The logistics display and its live gauges remain native components above the bottom-left rail.

The palette is shared with SectorPad's owned overlays:

| Token | Color | Role |
| --- | --- | --- |
| Panel | `#0c1824` | Main matte surface |
| Field | `#070f19` | Recessed field/background |
| Key | `#142938` | Control surface |
| Selected | `#203e50` | Raised or selected surface |
| Cyan | `#80ddeb` | Runtime accents; blended with steel for static edge lighting |
| Focus | `#f6bf75` | Controller focus accent in owned overlays |
| Ink | `#e6f0f4` | Primary foreground |
| Muted | `#a1b8c8` | Secondary foreground |
| Steel | `#355264` | Structural edges |

Static frames use the restrained surface/steel/cyan subset. Their edge light blends 30% cyan into steel, reserving the full accent intensity for runtime controls. The game and SectorPad's runtime own dynamic text and focus colors; the textures do not bake text or permanently paint an active focus state.

## Preserved surfaces

The allowlist excludes all native icons, fonts, cursors, radar imagery, progress/flux/hull gauges, warning symbols, title artwork, loading art and standalone logos. It also excludes decorative variants outside the four current-core mappings and the asymmetric `ui_border2` family, whose larger insets and cutouts require separate layout verification. Those surfaces retain their existing art. The bottom-left rail's old baked branding is replaced by plain original geometry. This is a coherent frame skin, not a replacement of every native illustration or HUD component.

## Reproduction and checks

Python 3's standard library is sufficient; a game installation, Pillow and AashPad are not required to regenerate the assets:

```text
python tools/generate-hud-theme.py
python tools/generate-hud-theme.py --check
```

The first command writes only the 57 allowlisted PNGs under `mod/graphics`. The second compares every checked-in PNG byte-for-byte with freshly generated geometry and makes no changes. PNG encoding uses fixed uncompressed DEFLATE blocks to avoid compression-version differences. Both commands check all sprite dimensions, the opaque versus transparent center contracts, exterior padding, panel joins, repeatable horizontal and vertical edge seams, clear side-extension regions, the power-button aperture and the decorative chassis cutouts. An optional assembled preview can be written separately:

```text
python tools/generate-hud-theme.py --check --preview build/modern-hud-preview.png
```

The generated set occupies 320,808 bytes. Its deterministic manifest digest, computed over sorted relative names and encoded PNG bytes, is:

```text
e117236ad9fb7fba0f7aaae6102e6e93da3d4e0c5e41c03a5fdafc016e716598
```

All 57 PNGs were independently decoded and checked against the installed Starsector 0.98a-RC8 asset dimensions in the original compatibility pass; this refinement preserves those dimensions and adds them to the generator's checks. The refined assembled panel, tab-chassis, decorative corner/rail, repeated-edge control and backing-field preview was visually inspected. It is a geometry specimen sheet, not a game screenshot. These checks establish texture integrity and basic composition; they do not establish that every native screen, scaling factor or third-party UI combination has been visually accepted. This refinement has not had a new live in-game visual pass. See the main compatibility report for recorded earlier live checks.

## Other native skins

AashPad 1.0.1 supplies overlapping native texture paths and changes the player faction's UI palette from its application-load callback. Enabling both skins can therefore mix frame artwork and palette changes according to resource/plugin loading. Use one native HUD skin for a predictable result. SectorPad does not modify or disable AashPad.

The inspected 0.98a-RC8 `ModManager` metadata parser and `ModSpec` expose no `loadPriority` setting. Adding such a field to `mod_info.json` is not a verified way to resolve skin conflicts. No loading-priority or third-party dependency trick is part of these assets.
