package com.networkspeed.speed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Restarts the speed monitor after reboot when the user enabled auto-start.
 */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    if (
      action != Intent.ACTION_BOOT_COMPLETED &&
      action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
      action != Intent.ACTION_MY_PACKAGE_REPLACED
    ) {
      return
    }
    if (!SpeedPrefs.isAutoStartEnabled(context)) return

    val service = Intent(context, NetworkSpeedService::class.java).apply {
      this.action = NetworkSpeedService.ACTION_START
    }
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(service)
      } else {
        context.startService(service)
      }
    } catch (_: Exception) {
    }
  }
}
