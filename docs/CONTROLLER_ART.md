# Controller artwork

SectorPad 1.5.0 uses sourced controller illustrations and button prompts in its own interface. The cyan mapped rings, amber selected binding and live input indicators remain SectorPad drawing layered over the device artwork. Input discovery, bindings and calibration are unchanged. No base-game asset is edited.

## Sources and permissions

| Asset | Author/source | Permission |
| --- | --- | --- |
| Steam Deck front illustration | [Valve Steam Deck SVG line art](https://partner.steamgames.com/doc/steamhardware/steamdeck/svg) | Valve explicitly supplies the views for in-game input callouts; retained permission notice, not relicensed as Apache or CC0 |
| Xbox Series illustration and prompts | [Zacksly, Xbox Series Button Icons and Controls](https://zacksly.itch.io/xbox-series-button-icons-and-controls) | Creative Commons Attribution 3.0; source, author, license and modifications credited |
| ROG Ally illustration | [victor-borges, Handheld Controller Glyphs](https://github.com/victor-borges/handheld-controller-glyphs), originally forked from honjow's theme | MIT; copyright and license retained |
| Deck and Ally button prompts | [Kenney Input Prompts 1.5A](https://kenney.nl/assets/input-prompts) | Creative Commons CC0 |

Full notices are included under `licenses/device-art` and `licenses/control-prompts`. These assets retain their own terms; SectorPad's Apache license does not replace them. Manufacturer and product names identify the hardware; this mod is not an endorsement by the manufacturers or artists.

## Adaptation

Preserved source inputs and their provenance live under `vendor/ui-art`. Runtime PNGs live under `mod/graphics/sectorpad/controls`, and are shipped with the mod. Conversion keeps the original contours, trims unused transparent margins where needed and prepares transparent artwork for UI tinting. Button prompt aspect ratios follow the actual art. The live overlay positions are fitted to each illustration; front views use separate bumper/trigger callouts where those controls are hidden from that angle.

The hardware background is static. Available standard gamepad buttons, triggers and sticks supply live indicators; the diagram does not invent independent trackpad, rear-button or sensor readings. Unknown devices use the generic drawing. If a sourced texture cannot load, the existing vector artwork remains available, with a bounded diagnostic entry instead of repeated loading failures.

## Build and verification

`tools/generate-device-art.py --check` and `tools/generate-control-glyphs.py --check` verify the preserved inputs and runtime outputs during the normal release build without downloading files or requiring an image conversion tool. Regeneration is a separate developer operation; each script documents its conversion dependencies. The source archive includes the preserved inputs and generators; the runtime archive includes the PNGs and licenses.

The offscreen review uses the production renderers and reads these mod textures through the same SettingsAPI boundary. It checks sourced artwork is actually used, including color/opacity and restoration of sprite/OpenGL state. Live, selected mapping and disconnected fixtures cover the three identified device families and the generic fallback. A missing texture is tested explicitly. The renders use synthetic inputs and do not establish new physical Ally/Deck or native LunaLib mounting acceptance.
