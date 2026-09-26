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
