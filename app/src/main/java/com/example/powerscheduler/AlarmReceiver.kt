package com.example.powerscheduler

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Handles system broadcasts: boot + next-alarm changes. */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
                action == "android.intent.action.QUICKBOOT_POWERON"
        if (!isBoot && action != AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) return

        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                Log.d(TAG, "Received $action")
                // AlarmManager alarms are wiped on reboot, so restore the shutdown schedule.
                if (isBoot && Scheduler.isEnabled(app)) Scheduler.schedule(app)

                val (ok, msg) = Scheduler.armWake(app)
                Log.d(TAG, "armWake: $ok - $msg")
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Fires at the scheduled shutdown time. Not exported, so other apps cannot trigger it. */
class ShutdownReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ShutdownReceiver"
        private const val STALE_MS = 10 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Scheduler.ACTION_SHUTDOWN) return
        val app = context.applicationContext
        if (!Scheduler.isEnabled(app)) return

        val pending = goAsync()
        Thread {
            try {
                val planned = Scheduler.nextShutdownMs(app)
                val late = System.currentTimeMillis() - planned

                // Always queue tomorrow's shutdown first.
                Scheduler.schedule(app)

                if (planned != 0L && late > STALE_MS) {
                    Log.w(TAG, "Alarm delivered ${late / 1000}s late, skipping shutdown")
                    return@Thread
                }

                val (ok, msg) = Scheduler.armWake(app)
                Log.d(TAG, "armWake: $ok - $msg")
                RootUtils.shutdownDevice()
            } finally {
                pending.finish()
            }
        }.start()
    }
}