* **Google Fonts Catalog (Official Hub)**: [fonts.google.com/icons](https://fonts.google.com/icons?utm_source=gemini)
Search and download Google's latest **Material Symbols** (the newest generation that replaced classic Material Icons). It allows real-time customization of variable font axes—**Fill**, **Weight**, **Grade**, and **Optical Size**—across *Outlined*, *Rounded*, and *Sharp* styles.
* **Material Design 3 Guidelines**: [m3.material.io/styles/icons](https://m3.material.io/styles/icons?utm_source=gemini)
Access design guidelines, icon keyline templates, and specifications on how to apply variable icon attributes across different platforms.
* **Figma Plugin**:
Search for the official **Material Symbols** plugin in the Figma Community to insert and tweak scalable vector icons directly inside design projects.
* **GitHub Repository**: [github.com/google/material-design-icons](https://github.com/google/material-design-icons?utm_source=gemini)
Access source vector files (SVGs), raw web font files, and Android Vector Drawables.
* **Developer Implementation**:
* **Web (CSS Variable Font)**:
```html
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined" />
<span class="material-symbols-outlined">search</span>

```

* **Android / Compose Multiplatform**: Add the icon name to `NAMES` in `tools/download_symbols.py`, rerun `python tools/download_symbols.py`, and use the generated `shared/src/commonMain/composeResources/drawable/symbol_*.xml` resource with `Icon(painterResource(Res.drawable.symbol_name), contentDescription = "Action")`. Import `org.jetbrains.compose.resources.painterResource` and the resources from `notifly.shared.generated.resources`. Do not add the legacy Material Icons dependency or use `Icons.Default`.
* **Flutter**: Use the official `material_symbols_icons` package from pub.dev.

## Vector loading crash — 2026-10-02

Notifly crashed while rendering its navigation icons on a Samsung Galaxy S23
Ultra (`SM-S918B`, Android 16). The fatal logcat entry at 23:32:02 Philippine
time reported:

```text
java.lang.IllegalArgumentException: Invalid color value @android:color/black
    at org.jetbrains.compose.resources.vector.ValueParsersKt.parseColorValue
```

Commit `b4bb61c` changed `tools/download_symbols.py` to emit an Android system
color reference instead of a literal mask fill. Three new resources contained
that reference: `symbol_event_upcoming.xml`, `symbol_chevron_left.xml`, and
`symbol_chevron_right.xml`.

Compose Multiplatform's `painterResource` loads these XML files with its own
vector parser. That parser requires literal hex colors and cannot resolve
external Android resources. The Bills icon is loaded by the navigation bar;
the chevrons are loaded by the bill calendar. Rendering any of these icons
therefore threw the exception on the main thread. See the upstream
[color parser](https://github.com/JetBrains/compose-multiplatform/blob/master/components/resources/library/src/commonMain/kotlin/org/jetbrains/compose/resources/vector/ValueParsers.kt)
and [vector parser](https://github.com/JetBrains/compose-multiplatform/blob/master/components/resources/library/src/commonMain/kotlin/org/jetbrains/compose/resources/vector/XmlVectorParser.kt).

`assembleDebug` succeeded because it packaged the XML without exercising this
runtime parser. The existing unit tests also did not render these icons.

The fix restores `android:fillColor="#FF000000"` in the generator and regenerates
the symbols with `python tools/download_symbols.py`. This opaque fill supplies
the icon mask; `Icon` still gets its visible tint from `MaterialTheme` through
the surrounding content color or an explicit theme tint. The user-approved
mask exception is recorded in `AGENTS.md`. Do not replace it with
`@android:color/black` or a theme attribute reference: neither resolves in this
resource parser.

`ExpressiveControlsTest.everyMaterialSymbolLoadsInCompose` now loads every
generated `symbol_` drawable through `painterResource` inside `NotiflyTheme`.
The regression test passed on the same phone, followed by the full connected
UI suite: **13 tests passed, zero failures, errors, or skips**.

Run the UI suite on an ADB-connected device from Windows PowerShell:

```powershell
.\gradlew.bat :shared:connectedDebugAndroidTest --console=plain
```
