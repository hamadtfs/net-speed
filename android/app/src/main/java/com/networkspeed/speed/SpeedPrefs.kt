package com.networkspeed.speed

import android.content.Context

object SpeedPrefs {
  private const val FILE = "net_speed_prefs"
  private const val KEY_OVERLAY = "overlay_enabled"
  private const val KEY_OFFSET = "overlay_offset_dp"
  private const val KEY_DARK_TEXT = "overlay_dark_text"
  private const val KEY_AUTO_START = "auto_start_on_boot"

  const val DEFAULT_OFFSET_DP = 130
  const val MIN_OFFSET_DP = 0
  const val MAX_OFFSET_DP = 400

  private fun prefs(context: Context) =
    context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

  fun isOverlayEnabled(context: Context): Boolean =
    prefs(context).getBoolean(KEY_OVERLAY, false)

  fun setOverlayEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_OVERLAY, enabled).apply()
  }

  fun isDarkText(context: Context): Boolean =
    prefs(context).getBoolean(KEY_DARK_TEXT, false)

  fun setDarkText(context: Context, dark: Boolean) {
    prefs(context).edit().putBoolean(KEY_DARK_TEXT, dark).apply()
  }

  fun isAutoStartEnabled(context: Context): Boolean =
    prefs(context).getBoolean(KEY_AUTO_START, false)

  fun setAutoStartEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_AUTO_START, enabled).apply()
  }

  /** Distance in dp from the right edge of the screen to the right edge of the overlay text. */
  fun overlayOffsetDp(context: Context): Int =
    prefs(context).getInt(KEY_OFFSET, DEFAULT_OFFSET_DP)

  fun setOverlayOffsetDp(context: Context, offset: Int) {
    prefs(context).edit()
      .putInt(KEY_OFFSET, offset.coerceIn(MIN_OFFSET_DP, MAX_OFFSET_DP))
      .apply()
  }
}
