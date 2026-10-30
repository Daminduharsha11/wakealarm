package com.example.powerscheduler

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "Received broadcast action: $action. Re-evaluating next alarm...")

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nextAlarmClock = alarmManager.nextAlarmClock

            if (nextAlarmClock != null) {
                val triggerTimeEpochMs = nextAlarmClock.triggerTime
                val triggerTimeEpochSec = triggerTimeEpochMs / 1000
                // Subtract a small buffer (e.g., 5 mins = 300s) so it wakes up slightly before the alarm rings
                val wakeEpochSec = triggerTimeEpochSec - 300

                Log.d(TAG, "Next alarm at $triggerTimeEpochSec. Setting RTC wake alarm for epoch $wakeEpochSec")

                val success = RootUtils.setRtcWakeAlarm(context, wakeEpochSec)
                Log.d(TAG, "RTC write success: $success")
            } else {
                Log.d(TAG, "No active next alarm found.")
            }
        }
    }
}