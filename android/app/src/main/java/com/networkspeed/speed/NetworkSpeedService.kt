package com.networkspeed.speed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.TrafficStats
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.networkspeed.MainActivity
import com.networkspeed.R

class NetworkSpeedService : Service() {
  companion object {
    // Pixel hides icons of "silent" (low importance) notifications from the status bar,
    // so the icon channel must be default importance with sound and vibration disabled.
    const val CHANNEL_ICON = "net_speed_icon_v2"
    const val CHANNEL_SILENT = "net_speed_silent_v2"
    private val LEGACY_CHANNELS = listOf(
      "network_speed_status",
      "net_speed_icon",
      "net_speed_silent",
    )

    const val NOTIFICATION_ID = 1001
    const val ACTION_START = "com.networkspeed.START"
    const val ACTION_STOP = "com.networkspeed.STOP"
    const val ACTION_REFRESH = "com.networkspeed.REFRESH"
    const val ACTION_SPEED_UPDATE = "com.networkspeed.SPEED_UPDATE"
    const val EXTRA_DOWNLOAD = "download"
    const val EXTRA_UPLOAD = "upload"
    const val EXTRA_TOTAL = "total"
    const val EXTRA_ON_WIFI = "onWifi"
    const val EXTRA_SIGNAL = "signal"

    @Volatile
    var isRunning: Boolean = false
      private set
  }

  private val handler = Handler(Looper.getMainLooper())
  private lateinit var overlay: StatusBarOverlay
  private lateinit var usageStore: DailyUsageStore
  private lateinit var statsHelper: NetworkStatsHelper
  private var lastRx: Long = TrafficStats.UNSUPPORTED.toLong()
  private var lastTx: Long = TrafficStats.UNSUPPORTED.toLong()
  private var lastTimestamp: Long = 0L
  private var useOverlay = false
  private var lastDetails: NetworkDetails? = null

  private val tickRunnable = object : Runnable {
    override fun run() {
      updateSpeed()
      handler.postDelayed(this, 1000L)
    }
  }

  override fun onCreate() {
    super.onCreate()
    overlay = StatusBarOverlay(this)
    usageStore = DailyUsageStore.get(this)
    statsHelper = NetworkStatsHelper(this, usageStore)
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopMonitor()
        return START_NOT_STICKY
      }
      ACTION_REFRESH -> {
        if (isRunning) applyDisplayMode() else stopSelf()
      }
      else -> startMonitor()
    }
    return START_STICKY
  }

  private fun startMonitor() {
    createNotificationChannels()
    if (!isRunning) {
      isRunning = true
      statsHelper.resetCounters()
      seedCounters()
    }
    applyDisplayMode()
    handler.removeCallbacks(tickRunnable)
    handler.post(tickRunnable)
  }

  private fun applyDisplayMode() {
    useOverlay = SpeedPrefs.isOverlayEnabled(this) && Settings.canDrawOverlays(this)
    if (useOverlay) {
      overlay.show(SpeedPrefs.overlayOffsetDp(this), SpeedPrefs.isDarkText(this))
    } else {
      overlay.hide()
    }
    val details = lastDetails ?: statsHelper.snapshot(0L, 0L)
    startForegroundCompat(buildNotification(details))
  }

  private fun startForegroundCompat(notification: Notification) {
    when {
      Build.VERSION.SDK_INT >= 34 -> startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
      )
      else -> startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun stopMonitor() {
    isRunning = false
    handler.removeCallbacks(tickRunnable)
    usageStore.flush()
    overlay.hide()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun seedCounters() {
    lastRx = TrafficStats.getTotalRxBytes()
    lastTx = TrafficStats.getTotalTxBytes()
    lastTimestamp = SystemClock.elapsedRealtime()
  }

  private fun updateSpeed() {
    val rx = TrafficStats.getTotalRxBytes()
    val tx = TrafficStats.getTotalTxBytes()
    val now = SystemClock.elapsedRealtime()

    if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
      val details = statsHelper.snapshot(0L, 0L)
      lastDetails = details
      pushNotification(details)
      return
    }

    val elapsedMs = (now - lastTimestamp).coerceAtLeast(1L)
    val downloadBps = ((rx - lastRx).coerceAtLeast(0L) * 1000L) / elapsedMs
    val uploadBps = ((tx - lastTx).coerceAtLeast(0L) * 1000L) / elapsedMs
    val totalBps = downloadBps + uploadBps

    lastRx = rx
    lastTx = tx
    lastTimestamp = now

    val details = statsHelper.snapshot(downloadBps, uploadBps)
    lastDetails = details

    if (useOverlay) {
      overlay.update(totalBps)
    }
    pushNotification(details)
    broadcastSpeed(details)
  }

  private fun broadcastSpeed(details: NetworkDetails) {
    val intent = Intent(ACTION_SPEED_UPDATE).apply {
      setPackage(packageName)
      putExtra(EXTRA_DOWNLOAD, details.downloadBps)
      putExtra(EXTRA_UPLOAD, details.uploadBps)
      putExtra(EXTRA_TOTAL, details.speedBps)
      putExtra(EXTRA_ON_WIFI, details.onWifi)
      putExtra(EXTRA_SIGNAL, details.signalPercent ?: -1)
    }
    sendBroadcast(intent)
  }

  private fun pushNotification(details: NetworkDetails) {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(NOTIFICATION_ID, buildNotification(details))
  }

  private fun buildNotification(details: NetworkDetails): Notification {
    val openApp = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val stopIntent = PendingIntent.getService(
      this,
      1,
      Intent(this, NetworkSpeedService::class.java).apply { action = ACTION_STOP },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val speedText = SpeedFormatter.format(details.speedBps)
    val downText = SpeedFormatter.format(details.downloadBps)
    val upText = SpeedFormatter.format(details.uploadBps)
    val signalText = details.signalPercent?.let { "$it%" } ?: "—"
    val mobileText = DataFormatter.formatBytes(details.mobileBytes)
    val wifiText = DataFormatter.formatBytes(details.wifiBytes)

    val night =
      (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    // Pure white on dark shade, pure black on light shade — no gray tint.
    val textColor = if (night) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

    val contentView = RemoteViews(packageName, R.layout.notification_speed_details).apply {
      setTextViewText(R.id.notif_title, "Net Speed")
      setTextViewText(R.id.notif_speed, "Speed: $speedText")
      setTextViewText(R.id.notif_signal, "Signal: $signalText")
      setTextViewText(R.id.notif_mobile, "Mobile today: $mobileText")
      setTextViewText(R.id.notif_wifi, "WiFi today: $wifiText")
      setTextColor(R.id.notif_title, textColor)
      setTextColor(R.id.notif_speed, textColor)
      setTextColor(R.id.notif_signal, textColor)
      setTextColor(R.id.notif_mobile, textColor)
      setTextColor(R.id.notif_wifi, textColor)
    }

    val builder = NotificationCompat.Builder(this, if (useOverlay) CHANNEL_SILENT else CHANNEL_ICON)
      .setContentTitle("↓ $downText  ↑ $upText")
      .setContentText("Speed $speedText · Signal $signalText")
      .setStyle(NotificationCompat.DecoratedCustomViewStyle())
      .setCustomContentView(contentView)
      .setCustomBigContentView(contentView)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setShowWhen(false)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .setContentIntent(openApp)
      .addAction(0, "Stop", stopIntent)
      .setColor(0xFF7CFFB2.toInt())
      .setColorized(false)

    if (useOverlay) {
      builder.setPriority(NotificationCompat.PRIORITY_MIN)
        .setSmallIcon(R.drawable.ic_speed_status)
    } else {
      builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
      val bitmap = createStatusBarIcon(details.speedBps)
      if (bitmap != null) {
        builder.setSmallIcon(IconCompat.createWithBitmap(bitmap))
      } else {
        builder.setSmallIcon(R.drawable.ic_speed_status)
      }
    }

    return builder.build()
  }

  private fun createStatusBarIcon(bytesPerSecond: Long): Bitmap? {
    return try {
      val label = SpeedFormatter.formatStatusBar(bytesPerSecond)
      val size = 96
      val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        textSize = when {
          label.length <= 5 -> 42f
          label.length <= 7 -> 34f
          else -> 28f
        }
      }
      val x = size / 2f
      val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
      canvas.drawText(label, x, y, paint)
      bitmap
    } catch (_: Exception) {
      null
    }
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    LEGACY_CHANNELS.forEach { manager.deleteNotificationChannel(it) }

    val icon = NotificationChannel(
      CHANNEL_ICON,
      "Speed in status bar",
      NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
      description = "Shows the speed as an icon in the status bar"
      setShowBadge(false)
      enableVibration(false)
      enableLights(false)
      setSound(null, null)
    }
    val silent = NotificationChannel(
      CHANNEL_SILENT,
      "Speed overlay service",
      NotificationManager.IMPORTANCE_MIN,
    ).apply {
      description = "Keeps the status bar overlay running"
      setShowBadge(false)
      enableVibration(false)
      setSound(null, null)
    }
    manager.createNotificationChannels(listOf(icon, silent))
  }

  override fun onDestroy() {
    isRunning = false
    handler.removeCallbacks(tickRunnable)
    if (::usageStore.isInitialized) {
      usageStore.flush()
    }
    overlay.hide()
    super.onDestroy()
  }
}
