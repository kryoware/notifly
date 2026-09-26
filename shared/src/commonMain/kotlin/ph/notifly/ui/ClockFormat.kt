package ph.notifly.ui

import androidx.compose.runtime.Composable

/** Whether the platform's clock settings or locale use 24-hour time. */
@Composable
internal expect fun is24HourClock(): Boolean
