package com.example.powerscheduler

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
        private const val WAKE_OFFSET_SECONDS = 300L // 5 minutes prior

        fun updateWakeAlarm(context: Context): String {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nextAlarm = alarmManager.nextAlarmClock

            return if (nextAlarm != null) {
                val triggerTimeMs = nextAlarm.triggerTime
                val triggerTimeSec = triggerTimeMs / 1000
                val wakeTimeSec = triggerTimeSec - WAKE_OFFSET_SECONDS

                val (success, msg) = RootUtils.setRtcWakeAlarm(context, wakeTimeSec)
                Log.d(TAG, "Next alarm at $triggerTimeSec. RTC write success: $success ($msg)")
                "Alarm detected! Target wake epoch: $wakeTimeSec\nKernel status: $msg"
            } else {
                RootUtils.setRtcWakeAlarm(context, 0)
                Log.d(TAG, "No active system alarm clocks found.")
                "No active system alarm clocks found via AlarmManager."
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received background broadcast action: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.app.action.NEXT_ALARM_CLOCK_CHANGED") {
            updateWakeAlarm(context)
        }
    }
}