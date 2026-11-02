package com.networkspeed.speed

import java.util.Locale
import kotlin.math.abs

object SpeedFormatter {
  fun format(bytesPerSecond: Long): String {
    val speed = abs(bytesPerSecond).toDouble()
    return when {
      // Never show B/s — floor at KB/s.
      speed < 1024 * 1024 -> String.format(Locale.US, "%.0f KB/s", speed / 1024.0)
      speed < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", speed / (1024.0 * 1024.0))
      else -> String.format(Locale.US, "%.2f GB/s", speed / (1024.0 * 1024.0 * 1024.0))
    }
  }

  /**
   * Single-line status-bar label.
   * Never uses B/s. KB: whole number, capital — e.g. "11KB/s".
   * MB: keep decimals — e.g. "1.2MB/s".
   */
  fun formatStatusBar(bytesPerSecond: Long): String {
    val speed = abs(bytesPerSecond).toDouble()
    return when {
      speed < 1024 * 1024 -> String.format(Locale.US, "%.0fKB/s", speed / 1024.0)
      speed < 1024 * 1024 * 1024 -> {
        val mb = speed / (1024.0 * 1024.0)
        when {
          mb < 10 -> String.format(Locale.US, "%.2fMB/s", mb)
          mb < 100 -> String.format(Locale.US, "%.1fMB/s", mb)
          else -> String.format(Locale.US, "%.0fMB/s", mb)
        }
      }
      else -> {
        val gb = speed / (1024.0 * 1024.0 * 1024.0)
        if (gb < 10) String.format(Locale.US, "%.2fGB/s", gb)
        else String.format(Locale.US, "%.1fGB/s", gb)
      }
    }
  }

  /** @deprecated Prefer [formatStatusBar]; kept for any leftover call sites. */
  fun formatStacked(bytesPerSecond: Long): Pair<String, String> {
    val label = formatStatusBar(bytesPerSecond)
    return Pair(label, "")
  }

  /** Absolute data volume, e.g. "4.1 MB". */
  fun formatData(bytes: Long): String {
    val amount = abs(bytes).toDouble()
    return when {
      amount < 1024 -> String.format(Locale.US, "%.0f B", amount)
      amount < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", amount / 1024.0)
      amount < 1024 * 1024 * 1024 ->
        String.format(Locale.US, "%.1f MB", amount / (1024.0 * 1024.0))
      else -> String.format(Locale.US, "%.2f GB", amount / (1024.0 * 1024.0 * 1024.0))
    }
  }

  /** Compact label for status-bar icon, e.g. "1.2M" or "340K". */
  fun formatCompact(bytesPerSecond: Long): String {
    val speed = abs(bytesPerSecond).toDouble()
    return when {
      speed < 1024 -> String.format(Locale.US, "%.0fB", speed)
      speed < 1024 * 1024 -> {
        val kb = speed / 1024.0
        if (kb < 10) String.format(Locale.US, "%.1fK", kb)
        else String.format(Locale.US, "%.0fK", kb)
      }
      speed < 1024 * 1024 * 1024 -> {
        val mb = speed / (1024.0 * 1024.0)
        if (mb < 10) String.format(Locale.US, "%.1fM", mb)
        else String.format(Locale.US, "%.0fM", mb)
      }
      else -> String.format(Locale.US, "%.1fG", speed / (1024.0 * 1024.0 * 1024.0))
    }
  }
}
