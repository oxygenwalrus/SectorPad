# Third-party notices and source provenance

SectorPad's original Java and Windows JNI implementation is distributed under the [Apache License 2.0](LICENSE). Third-party components retain their own licenses and copyright notices. The root license does not relicense OpenJDK headers or the zlib-licensed components listed below.

The runtime dependency list is `SectorPad.jar`, `jamepad-sectorpad-2.30.0.0.jar`, and `gdx-jnigen-loader-2.2.0.jar`, plus the platform-native files in the mod's `native` directory. Game, LunaLib, LazyLib, and Console Commands JARs are not part of the redistributable mod package.

| Component | Pinned source / upstream | License and retained notice |
| --- | --- | --- |
| Jamepad 2.30.0.0, including the SectorPad Java adaptation | [`libgdx/Jamepad`, commit `8b8a543c529e6af33bd6de2329f9b4fe97259641`](https://github.com/libgdx/Jamepad/tree/8b8a543c529e6af33bd6de2329f9b4fe97259641), tag `2.30.0.0`; Maven `com.badlogicgames.jamepad:jamepad:2.30.0.0` | [Apache 2.0](licenses/Jamepad-Apache-2.0.txt) and retained [original Jamepad zlib notice](licenses/Jamepad-original-zlib.txt), Copyright © 2016 William Hartman |
| GDX jnigen loader 2.2.0, unmodified | [`libgdx/gdx-jnigen`, commit `d76af74312ba938ca86f1526b07037ad8509f73e`](https://github.com/libgdx/gdx-jnigen/tree/d76af74312ba938ca86f1526b07037ad8509f73e), tag `2.2.0`; Maven `com.badlogicgames.gdx:gdx-jnigen-loader:2.2.0` | [Upstream Apache 2.0 license](https://github.com/libgdx/gdx-jnigen/blob/d76af74312ba938ca86f1526b07037ad8509f73e/LICENSE); the full Apache text is also retained in [LICENSE](LICENSE) |
| SDL included in the Jamepad native binaries | Jamepad's pinned SDL submodule, [`libsdl-org/SDL` commit `859844eae358447be8d66e6da59b6fb3df0ed778`](https://github.com/libsdl-org/SDL/tree/859844eae358447be8d66e6da59b6fb3df0ed778) | [SDL zlib notice](licenses/SDL-zlib.txt), Copyright © 1997–2024 Sam Lantinga |
| SDL GameControllerDB data | [`mdqinc/SDL_GameControllerDB` commit `55c3bc818853e77937c2373527533d7baecd42f8`](https://github.com/mdqinc/SDL_GameControllerDB/blob/55c3bc818853e77937c2373527533d7baecd42f8/gamecontrollerdb.txt) | [Database zlib notice](licenses/SDL-GameControllerDB-zlib.txt), Copyright © 1997–2025 Sam Lantinga; [upstream license](https://github.com/mdqinc/SDL_GameControllerDB/blob/55c3bc818853e77937c2373527533d7baecd42f8/LICENSE) |
| OpenJDK JNI build headers, unmodified | `openjdk/jdk17u`, tag `jdk-17.0.16+8`: [`jni.h`](https://github.com/openjdk/jdk17u/blob/jdk-17.0.16%2B8/src/java.base/share/native/include/jni.h) and [`jni_md.h`](https://github.com/openjdk/jdk17u/blob/jdk-17.0.16%2B8/src/java.base/windows/native/include/jni_md.h) | [GPL version 2 with the Classpath exception](licenses/OpenJDK-JNI-GPL2-Classpath.txt); individual copyright/license headers are retained |

## Marked Jamepad adaptation

`jamepad-sectorpad-2.30.0.0.jar` is an **altered SectorPad build**, not the unmodified upstream Jamepad artifact. The build retains the original upstream JAR and source JAR in the source tree's `vendor` directory and derives one runtime JAR through `tools/build.py`. It does not place a second Jamepad implementation earlier on the classpath to shadow the original.

The changes to `com.studiohartman.jamepad.ControllerManager` are:

1. `addMappingsFromFile` reads a classpath resource with `InputStream.readNBytes`, capped at 2 MiB with an extra-byte overflow check. It closes the stream, rejects a missing/oversized resource, and passes the bounded bytes to the existing native buffer mapping API. It no longer performs filesystem copying or temporary extraction.
2. `quitSDLGamepad` skips null controller slots so partially completed initialization can be cleaned up.
3. Imports made unnecessary by the bounded resource implementation are removed.

The generated changed source is marked with a SectorPad adaptation comment. Its build-tree location is `build/jamepad-adapter/src/com/studiohartman/jamepad/ControllerManager.java`; `SectorPad-source.zip` retains it as `third_party/jamepad/ControllerManager.java`. The reproducible transformation is retained in `tools/build.py`; its input is `vendor/jamepad-2.30.0.0-sources.jar`. The source distribution keeps the transformation, original source/license notices, and plainly identified changed source together. [Original ControllerManager source](https://github.com/libgdx/Jamepad/blob/8b8a543c529e6af33bd6de2329f9b4fe97259641/src/main/java/com/studiohartman/jamepad/ControllerManager.java).

The runtime Jamepad JAR excludes the embedded native binaries. Build-time packaging copies the upstream Windows `jamepad64.dll` and Linux `libjamepad64.so` unchanged into separate platform directories. Both contain Jamepad JNI and SDL code, so their Jamepad and SDL notices travel together. SectorPad disables Jamepad's default native extraction loader and loads the explicit mod-local path. The GDX loader remains as an unmodified binary dependency. The bundled database is a pinned resource; SectorPad does not update it over the network at runtime.

## Native build material

`src/main/native/sectorpad_input_windows.c` is SectorPad's own Windows input/observer implementation under Apache 2.0. The compiled `sectorpad-input-windows-x86_64.dll` is separate from Jamepad and does not replace a Windows or game library. Its build uses unmodified OpenJDK JNI declaration headers, retaining the GPL 2/Classpath exception license and source notices in `src/main/native/include`. The exception text must accompany those headers when they are redistributed. The game JVM is neither bundled nor modified by this component.

The native builder uses an installed Microsoft Visual C++ toolchain and pinned Microsoft Windows SDK NuGet packages `Microsoft.Windows.SDK.CPP` and `Microsoft.Windows.SDK.CPP.x64`, version `10.0.26100.9169`, as build-only material. Their downloads remain in the local `build/toolchain` cache and are not part of the mod distribution. Those tools and SDK files retain Microsoft's terms; SectorPad's Apache license does not apply to them. [Microsoft package publication](https://www.nuget.org/packages/Microsoft.Windows.SDK.CPP.x64/10.0.26100.9169).

Do not redistribute the isolated copied Starsector installation under `build/verification`, proprietary game/dependency JARs, or downloaded build toolchains as part of a SectorPad source or runtime archive. Build against a separately licensed local game installation.

## Input checksums and acknowledgements

The build verifies the vendored Maven artifacts before use. These SHA-256 values identify the upstream inputs and unchanged packaged native payloads; use `build/package-sha256.json` for a particular rebuilt mod package.

| File | SHA-256 |
| --- | --- |
| `jamepad-2.30.0.0.jar` | `65b90692053b2a0a799840c06e9c31b5bfb648c0cf50cb6b238f99747fbcb9f2` |
| `jamepad-2.30.0.0-sources.jar` | `3a5afbd8854d33bac8a0ebc316355d18fa5301096f9ecdc3a7ba06daf45ab644` |
| `gdx-jnigen-loader-2.2.0.jar` | `5a48459f9d95c2599c8c36e23708737a296465f465f64929b87a1687ae613bdb` |
| `jamepad64.dll` | `d894f675fb9c85a9bae72446f9eeab76f70585ad88a816a787339e2728d4e72f` |
| `libjamepad64.so` | `a6f9a03751a2828bb28d7444bb97ac90b5af88d36fe527900e910e8afc9ccc70` |
| `gamecontrollerdb.txt` | `affab13e91183d918fa96e3e9efa13ac0335479e60cff09fafe81dde4e64070b` |

The JNI header hashes and exact source URLs are retained in `src/main/native/include/README.md` in the source distribution. License texts remain in `licenses` in the runtime distribution.

[SSMSControllerEx](https://github.com/katemonster33/SSMSControllerEx) and the original [SSMSController](https://github.com/razuhl/SSMSController) were inspected to understand controller behavior and existing game UI paths. They are acknowledged as research references; SectorPad does not bundle their controller plugin, global input shims, or user-modified Desktop artifacts. LunaLib, LazyLib, and Console Commands remain separately installed dependencies/integrations with their respective authors' licenses.

The development source tree may retain JNA 5.18.1 JARs and [their license notice](licenses/JNA-LICENSE.txt) from an earlier prototype. They are not runtime dependencies and are not listed in `mod_info.json`. The current Windows bridge uses the mod's own JNI binary.


## Controller interface artwork (1.5.0)

- **Steam Deck SVG line art - Valve Corporation.** Supplied by Valve specifically for in-game Steam Deck input callouts. See `licenses/device-art` for the retained permission/source notice. Valve artwork is not relicensed under SectorPad's Apache license.
- **Xbox Series Button Icons and Controls - Zacksly.** CC BY 3.0. https://zacksly.itch.io/xbox-series-button-icons-and-controls . SectorPad converts/scales the controller illustration and crops prompt margins for rendering, with runtime color/opacity and live overlays. Credit and license are included under `licenses/control-prompts` and `licenses/device-art`.
- **ROG Ally illustration - victor-borges / Handheld Controller Glyphs.** MIT, copyright 2024 victor-borges, with the upstream honjow theme acknowledged. https://github.com/victor-borges/handheld-controller-glyphs . Rasterized and prepared for UI tinting; retained notices under `licenses/device-art`.
- **Input Prompts - Kenney.** CC0. https://kenney.nl/assets/input-prompts . Selected Deck and Xbox-layout prompt images are cropped/scaled for SectorPad. Notices under `licenses/control-prompts`.

These assets remain under their listed permissions. Source provenance and exact input/output hashes are recorded under `vendor/ui-art` in the source archive. See `docs/CONTROLLER_ART.md` for adaptation and validation details.
