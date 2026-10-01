package ph.notifly.ui

import android.os.Build

internal actual val canBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
