package com.networkspeed.speed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule

class NetworkSpeedModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

  private var receiverRegistered = false

  private val speedReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action != NetworkSpeedService.ACTION_SPEED_UPDATE) return
      val signal = intent.getIntExtra(NetworkSpeedService.EXTRA_SIGNAL, -1)
      val map = Arguments.createMap().apply {
        putDouble("download", intent.getLongExtra(NetworkSpeedService.EXTRA_DOWNLOAD, 0L).toDouble())
        putDouble("upload", intent.getLongExtra(NetworkSpeedService.EXTRA_UPLOAD, 0L).toDouble())
        putDouble("total", intent.getLongExtra(NetworkSpeedService.EXTRA_TOTAL, 0L).toDouble())
        putBoolean("onWifi", intent.getBooleanExtra(NetworkSpeedService.EXTRA_ON_WIFI, false))
        if (signal >= 0) putInt("signal", signal) else putNull("signal")
      }
      if (reactContext.hasActiveReactInstance()) {
        reactContext
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
          .emit("NetworkSpeedUpdate", map)
      }
    }
  }

  init {
    reactContext.addLifecycleEventListener(this)
  }

  override fun getName(): String = "NetworkSpeedMonitor"

  @ReactMethod
  fun start(promise: Promise) {
    try {
      ensureReceiver()
      val intent = Intent(reactContext, NetworkSpeedService::class.java).apply {
        action = NetworkSpeedService.ACTION_START
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        reactContext.startForegroundService(intent)
      } else {
        reactContext.startService(intent)
      }
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("START_FAILED", e.message, e)
    }
  }

  @ReactMethod
  fun stop(promise: Promise) {
    try {
      val intent = Intent(reactContext, NetworkSpeedService::class.java).apply {
        action = NetworkSpeedService.ACTION_STOP
      }
      reactContext.startService(intent)
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("STOP_FAILED", e.message, e)
    }
  }

  @ReactMethod
  fun isMonitoring(promise: Promise) {
    promise.resolve(NetworkSpeedService.isRunning)
  }

  @ReactMethod
  fun isIgnoringBatteryOptimizations(promise: Promise) {
    try {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        promise.resolve(true)
        return
      }
      val pm = reactContext.getSystemService(Context.POWER_SERVICE) as PowerManager
      promise.resolve(pm.isIgnoringBatteryOptimizations(reactContext.packageName))
    } catch (e: Exception) {
      promise.reject("BATTERY_CHECK_FAILED", e.message, e)
    }
  }

  @ReactMethod
  fun openBatteryOptimizationSettings(promise: Promise) {
    try {
      val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${reactContext.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      reactContext.startActivity(intent)
      promise.resolve(true)
    } catch (_: Exception) {
      try {
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        reactContext.startActivity(fallback)
        promise.resolve(true)
      } catch (e: Exception) {
        promise.reject("BATTERY_SETTINGS_FAILED", e.message, e)
      }
    }
  }

  @ReactMethod
  fun getOverlaySettings(promise: Promise) {
    val map = Arguments.createMap().apply {
      putBoolean("enabled", SpeedPrefs.isOverlayEnabled(reactContext))
      putBoolean("permitted", Settings.canDrawOverlays(reactContext))
      putInt("offset", SpeedPrefs.overlayOffsetDp(reactContext))
      putBoolean("darkText", SpeedPrefs.isDarkText(reactContext))
    }
    promise.resolve(map)
  }

  @ReactMethod
  fun setOverlayDarkText(dark: Boolean, promise: Promise) {
    SpeedPrefs.setDarkText(reactContext, dark)
    refreshService()
    promise.resolve(true)
  }

  @ReactMethod
  fun setOverlayEnabled(enabled: Boolean, promise: Promise) {
    SpeedPrefs.setOverlayEnabled(reactContext, enabled)
    refreshService()
    promise.resolve(true)
  }

  @ReactMethod
  fun setOverlayOffset(offsetDp: Int, promise: Promise) {
    SpeedPrefs.setOverlayOffsetDp(reactContext, offsetDp)
    refreshService()
    promise.resolve(SpeedPrefs.overlayOffsetDp(reactContext))
  }

  @ReactMethod
  fun isAutoStartEnabled(promise: Promise) {
    promise.resolve(SpeedPrefs.isAutoStartEnabled(reactContext))
  }

  @ReactMethod
  fun setAutoStartEnabled(enabled: Boolean, promise: Promise) {
    SpeedPrefs.setAutoStartEnabled(reactContext, enabled)
    promise.resolve(true)
  }

  @ReactMethod
  fun clearDailyUsage(promise: Promise) {
    try {
      DailyUsageStore.get(reactContext).clearAll()
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("CLEAR_FAILED", e.message, e)
    }
  }

  @ReactMethod
  fun getDailyUsage(promise: Promise) {
    try {
      val store = DailyUsageStore.get(reactContext)
      val todayKey = store.todayKey()
      val days = Arguments.createArray()
      store.history().forEach { day ->
        days.pushMap(
          Arguments.createMap().apply {
            putString("dateKey", day.dateKey)
            putString("label", store.formatDateLabel(day.dateKey, todayKey))
            putBoolean("isToday", day.dateKey == todayKey)
            putDouble("mobile", day.mobileBytes.toDouble())
            putDouble("wifi", day.wifiBytes.toDouble())
            putDouble("total", day.totalBytes.toDouble())
          },
        )
      }
      val month = store.monthTotal()
      val result = Arguments.createMap().apply {
        putArray("days", days)
        putMap(
          "month",
          Arguments.createMap().apply {
            putString("label", "This Month")
            putDouble("mobile", month.mobileBytes.toDouble())
            putDouble("wifi", month.wifiBytes.toDouble())
            putDouble("total", month.totalBytes.toDouble())
          },
        )
      }
      promise.resolve(result)
    } catch (e: Exception) {
      promise.reject("USAGE_FAILED", e.message, e)
    }
  }

  @ReactMethod
  fun openOverlayPermissionSettings(promise: Promise) {
    try {
      val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${reactContext.packageName}"),
      ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
      reactContext.startActivity(intent)
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("OVERLAY_SETTINGS_FAILED", e.message, e)
    }
  }

  private fun refreshService() {
    if (!NetworkSpeedService.isRunning) return
    val intent = Intent(reactContext, NetworkSpeedService::class.java).apply {
      action = NetworkSpeedService.ACTION_REFRESH
    }
    try {
      reactContext.startService(intent)
    } catch (_: Exception) {
    }
  }

  @ReactMethod
  fun addListener(eventName: String) {
    // Required for NativeEventEmitter
  }

  @ReactMethod
  fun removeListeners(count: Int) {
    // Required for NativeEventEmitter
  }

  override fun onHostResume() {
    ensureReceiver()
    // Picks up an overlay permission granted while the user was in system settings.
    refreshService()
  }

  override fun onHostPause() {}

  override fun onHostDestroy() {
    unregisterReceiverSafely()
  }

  private fun ensureReceiver() {
    if (receiverRegistered) return
    val filter = IntentFilter(NetworkSpeedService.ACTION_SPEED_UPDATE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      reactContext.registerReceiver(speedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      reactContext.registerReceiver(speedReceiver, filter)
    }
    receiverRegistered = true
  }

  private fun unregisterReceiverSafely() {
    if (!receiverRegistered) return
    try {
      reactContext.unregisterReceiver(speedReceiver)
    } catch (_: Exception) {
    }
    receiverRegistered = false
  }

  override fun invalidate() {
    unregisterReceiverSafely()
    reactContext.removeLifecycleEventListener(this)
    super.invalidate()
  }
}
