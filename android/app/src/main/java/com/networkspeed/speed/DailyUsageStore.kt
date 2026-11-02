package com.networkspeed.speed

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DayUsage(
  val dateKey: String,
  val mobileBytes: Long,
  val wifiBytes: Long,
) {
  val totalBytes: Long
    get() = mobileBytes + wifiBytes
}

/**
 * Persists Mobile / Wi‑Fi usage per calendar day (local timezone).
 * "Today" resets automatically when the date key changes at midnight.
 */
class DailyUsageStore private constructor(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
  private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
    timeZone = TimeZone.getDefault()
  }
  private val displayFormat = SimpleDateFormat("MMM d, yyyy", Locale.US).apply {
    timeZone = TimeZone.getDefault()
  }

  private var cache: MutableMap<String, DayUsage> = loadAll().toMutableMap()
  private var writesSinceFlush = 0

  fun todayKey(): String = dayFormat.format(Date())

  fun addBytes(deltaBytes: Long, onWifi: Boolean) {
    if (deltaBytes <= 0L) return
    val key = todayKey()
    val current = cache[key] ?: DayUsage(key, 0L, 0L)
    val updated = if (onWifi) {
      current.copy(wifiBytes = current.wifiBytes + deltaBytes)
    } else {
      current.copy(mobileBytes = current.mobileBytes + deltaBytes)
    }
    cache[key] = updated
    writesSinceFlush++
    // Flush every few updates so reboots lose at most a couple of seconds of data.
    if (writesSinceFlush >= 5) {
      persist()
    }
  }

  fun flush() {
    persist()
  }

  fun clearAll() {
    cache.clear()
    val key = todayKey()
    cache[key] = DayUsage(key, 0L, 0L)
    persist()
  }

  fun today(): DayUsage {
    val key = todayKey()
    return cache[key] ?: DayUsage(key, 0L, 0L)
  }

  /** Newest-first history, capped, with today always first. */
  fun history(limit: Int = 45): List<DayUsage> {
    val key = todayKey()
    if (!cache.containsKey(key)) {
      cache[key] = DayUsage(key, 0L, 0L)
    }
    return cache.values
      .sortedByDescending { it.dateKey }
      .take(limit)
  }

  fun monthTotal(now: Calendar = Calendar.getInstance()): DayUsage {
    val year = now.get(Calendar.YEAR)
    val month = now.get(Calendar.MONTH) + 1
    val prefix = String.format(Locale.US, "%04d-%02d", year, month)
    var mobile = 0L
    var wifi = 0L
    cache.values.forEach { day ->
      if (day.dateKey.startsWith(prefix)) {
        mobile += day.mobileBytes
        wifi += day.wifiBytes
      }
    }
    return DayUsage("month", mobile, wifi)
  }

  fun formatDateLabel(dateKey: String, todayKey: String = todayKey()): String {
    if (dateKey == todayKey) return "Today"
    return try {
      val date = dayFormat.parse(dateKey) ?: return dateKey
      displayFormat.format(date)
    } catch (_: Exception) {
      dateKey
    }
  }

  private fun loadAll(): Map<String, DayUsage> {
    val raw = prefs.getString(KEY_DAYS, null) ?: return emptyMap()
    return try {
      val root = JSONObject(raw)
      val out = mutableMapOf<String, DayUsage>()
      val keys = root.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        val obj = root.getJSONObject(key)
        out[key] = DayUsage(
          dateKey = key,
          mobileBytes = obj.optLong("m", 0L),
          wifiBytes = obj.optLong("w", 0L),
        )
      }
      // Drop very old days to keep prefs small.
      out.values
        .sortedByDescending { it.dateKey }
        .take(MAX_DAYS)
        .associateBy { it.dateKey }
    } catch (_: Exception) {
      emptyMap()
    }
  }

  private fun persist() {
    writesSinceFlush = 0
    val root = JSONObject()
    cache.values
      .sortedByDescending { it.dateKey }
      .take(MAX_DAYS)
      .forEach { day ->
        root.put(
          day.dateKey,
          JSONObject()
            .put("m", day.mobileBytes)
            .put("w", day.wifiBytes),
        )
      }
    prefs.edit().putString(KEY_DAYS, root.toString()).apply()
  }

  companion object {
    private const val FILE = "daily_usage_store"
    private const val KEY_DAYS = "days"
    private const val MAX_DAYS = 62

    @Volatile
    private var instance: DailyUsageStore? = null

    fun get(context: Context): DailyUsageStore {
      return instance ?: synchronized(this) {
        instance ?: DailyUsageStore(context).also { instance = it }
      }
    }
  }
}
