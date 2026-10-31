package com.example.powerscheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object Scheduler {
    private const val TAG = "Scheduler"
    private const val PREFS = "power_prefs"
    const val ACTION_SHUTDOWN = "com.example.powerscheduler.ACTION_SHUTDOWN"
    private const val REQ_SHUTDOWN = 1001
    private const val WAKE_BUFFER_SEC = 300L // power on 5 min before the system alarm

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun hour(c: Context) = prefs(c).getInt("hour", 23)
    fun minute(c: Context) = prefs(c).getInt("minute", 0)
    fun nextShutdownMs(c: Context) = prefs(c).getLong("next_shutdown_ms", 0L)

    fun save(c: Context, hour: Int, minute: Int, enabled: Boolean) {
        prefs(c).edit().putInt("hour", hour).putInt("minute", minute).putBoolean("enabled", enabled).apply()
    }

    private fun pendingIntent(c: Context): PendingIntent {
        val i = Intent(c, ShutdownReceiver::class.java).setAction(ACTION_SHUTDOWN)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(c, REQ_SHUTDOWN, i, flags)
    }

    private fun nextTriggerMs(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** Schedules the next shutdown (today or tomorrow). Safe to call repeatedly. */
    fun schedule(c: Context): Long {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val t = nextTriggerMs(hour(c), minute(c))
        val pi = pendingIntent(c)
        when {
            Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms() ->
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            Build.VERSION.SDK_INT >= 23 ->
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            else -> am.setExact(AlarmManager.RTC_WAKEUP, t, pi)
        }
        prefs(c).edit().putLong("next_shutdown_ms", t).apply()
        Log.d(TAG, "Shutdown scheduled for $t")
        return t
    }

    fun cancel(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(c))
        prefs(c).edit().putBoolean("enabled", false).putLong("next_shutdown_ms", 0L).apply()
    }

    /**
     * Arms the RTC power-on alarm for (next system alarm - 5 min), as a RELATIVE offset
     * from the current wall clock. Call this right before shutting down.
     */
    fun armWake(c: Context): Pair<Boolean, String> {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = am.nextAlarmClock ?: return false to "No system alarm set, nothing to wake for"
        val wakeSec = next.triggerTime / 1000 - WAKE_BUFFER_SEC
        val deltaSec = wakeSec - System.currentTimeMillis() / 1000
        return RootUtils.setRtcWakeIn(c, deltaSec)
    }
}