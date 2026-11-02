package com.networkspeed.speed

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * Draws the speed as one-line text (e.g. "11KB/s", "1.2MB/s") inside the status bar strip.
 * Third-party apps cannot add icons to the system icon area, so this is a non-touchable
 * overlay window aligned with the status bar.
 */
class StatusBarOverlay(private val context: Context) {
  private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private var textView: TextView? = null
  private var params: WindowManager.LayoutParams? = null

  fun show(offsetDp: Int, darkText: Boolean) {
    if (!Settings.canDrawOverlays(context)) {
      hide()
      return
    }
    if (textView != null) {
      setOffset(offsetDp)
      applyColor(darkText)
      return
    }

    val text = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
      includeFontPadding = false
      gravity = Gravity.CENTER_VERTICAL or Gravity.END
      letterSpacing = -0.02f
      text = "0KB/s"
    }

    val lp = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      statusBarHeight(),
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.END
      x = dpToPx(offsetDp)
      y = 0
      alpha = 1f
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        fitInsetsTypes = 0
      }
    }

    try {
      windowManager.addView(text, lp)
      textView = text
      params = lp
      applyColor(darkText)
    } catch (_: Exception) {
      clearRefs()
    }
  }

  private fun applyColor(darkText: Boolean) {
    val color = if (darkText) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    textView?.let { view ->
      view.setTextColor(color)
      view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
      view.alpha = 1f
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        view.isForceDarkAllowed = false
      }
    }
  }

  fun setOffset(offsetDp: Int) {
    val v = textView ?: return
    val lp = params ?: return
    lp.x = dpToPx(offsetDp)
    try {
      windowManager.updateViewLayout(v, lp)
    } catch (_: Exception) {
    }
  }

  fun update(bytesPerSecond: Long) {
    textView?.text = SpeedFormatter.formatStatusBar(bytesPerSecond)
  }

  fun hide() {
    val v = textView ?: return
    try {
      windowManager.removeView(v)
    } catch (_: Exception) {
    }
    clearRefs()
  }

  private fun clearRefs() {
    textView = null
    params = null
  }

  private fun statusBarHeight(): Int {
    val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (id > 0) context.resources.getDimensionPixelSize(id) else dpToPx(24)
  }

  private fun dpToPx(dp: Int): Int =
    (dp * context.resources.displayMetrics.density).toInt()
}
