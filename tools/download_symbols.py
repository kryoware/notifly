"""Refresh official Material Symbols from Google Fonts (see LICENSE-material-symbols).

Stored as vector XML because Compose resources can't render SVG on Android.
"""

import re
from pathlib import Path
from urllib.request import urlopen


NAMES = (
    "add", "arrow_back", "calendar_today", "check", "check_circle", "clear", "delete",
    "expand_more", "file_download", "help", "home", "keyboard_arrow_right", "list",
    "north_east", "notifications_active", "pending_actions", "pie_chart",
    "schedule", "search", "settings", "south_west", "swap_horiz",
)
target = Path("shared/src/commonMain/composeResources/drawable")
target.mkdir(parents=True, exist_ok=True)
for name in NAMES:
    url = f"https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/{name}/default/24px.svg"
    path = re.search(r'<path d="([^"]+)"', urlopen(url).read().decode()).group(1)
    # Fill is only a mask; Icon() tints it from the theme.
    (target / f"symbol_{name}.xml").write_text(
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp"\n'
        '    android:viewportWidth="960" android:viewportHeight="960">\n'
        f'    <group android:translateY="960"><path android:fillColor="#FF000000" android:pathData="{path}"/></group>\n'
        '</vector>\n', newline="\n")
