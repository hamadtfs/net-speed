package com.networkspeed.speed

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellSignalStrength
import android.telephony.TelephonyManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class NetworkDetails(
  val speedBps: Long,
  val downloadBps: Long,
  val uploadBps: Long,
  val signalPercent: Int?,
  val mobileBytes: Long,
  val wifiBytes: Long,
  val onWifi: Boolean,
)

/**
 * Reads live speed/signal and records daily Mobile / Wi‑Fi usage that resets at midnight.
 */
class NetworkStatsHelper(
  private val context: Context,
  private val usageStore: DailyUsageStore,
) {
  private var lastTotalBytes = readTotalBytes()

  fun resetCounters() {
    lastTotalBytes = readTotalBytes()
  }

  fun snapshot(downloadBps: Long, uploadBps: Long): NetworkDetails {
    val totalNow = readTotalBytes()
    val delta = if (lastTotalBytes > 0L && totalNow >= lastTotalBytes) {
      totalNow - lastTotalBytes
    } else {
      0L
    }
    lastTotalBytes = totalNow

    val onWifi = isOnWifi()
    if (delta > 0L) {
      usageStore.addBytes(delta, onWifi)
    }

    val today = usageStore.today()
    return NetworkDetails(
      speedBps = downloadBps + uploadBps,
      downloadBps = downloadBps,
      uploadBps = uploadBps,
      signalPercent = readSignalPercent(onWifi),
      mobileBytes = today.mobileBytes,
      wifiBytes = today.wifiBytes,
      onWifi = onWifi,
    )
  }

  private fun readTotalBytes(): Long {
    val rx = TrafficStats.getTotalRxBytes()
    val tx = TrafficStats.getTotalTxBytes()
    if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
      return 0L
    }
    return rx + tx
  }

  private fun isOnWifi(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
  }

  private fun readSignalPercent(onWifi: Boolean): Int? {
    return if (onWifi) readWifiSignalPercent() else readCellularSignalPercent()
  }

  @Suppress("DEPRECATION")
  private fun readWifiSignalPercent(): Int? {
    return try {
      val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
      val info = wifi.connectionInfo ?: return null
      val rssi = info.rssi
      if (rssi == -127 || rssi == 0) return null
      min(100, max(0, (rssi + 100) * 2))
    } catch (_: Exception) {
      null
    }
  }

  private fun readCellularSignalPercent(): Int? {
    return try {
      val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val strength = tm.signalStrength ?: return null
        val level = strength.level
        when (level) {
          CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN -> null
          else -> min(100, max(0, (level * 100) / 4))
        }
      } else {
        null
      }
    } catch (_: SecurityException) {
      null
    } catch (_: Exception) {
      null
    }
  }
}

object DataFormatter {
  fun formatBytes(bytes: Long): String {
    val value = abs(bytes).toDouble()
    return when {
      value < 1024 -> String.format(java.util.Locale.US, "%.0f B", value)
      value < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", value / 1024.0)
      value < 1024 * 1024 * 1024 ->
        String.format(java.util.Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
      else -> String.format(java.util.Locale.US, "%.2f GB", value / (1024.0 * 1024.0 * 1024.0))
    }
  }
}
