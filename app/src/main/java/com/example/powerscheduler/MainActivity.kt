package com.example.powerscheduler

import android.app.AlarmManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvNextAlarm: TextView
    private lateinit var tvRtcStatus: TextView
    private lateinit var btnSetShutdownTime: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvRootStatus = findViewById<TextView>(R.id.tvRootStatus)
        tvNextAlarm = findViewById(R.id.tvNextAlarm)
        tvRtcStatus = findViewById(R.id.tvRtcStatus)
        btnSetShutdownTime = findViewById(R.id.btnSetShutdownTime)
        val btnTestRtc = findViewById<Button>(R.id.btnTestRtc)
        val btnManualShutdown = findViewById<Button>(R.id.btnManualShutdown)

        // 1. Root check off the main thread (su can block)
        val app = applicationContext
        Thread {
            RootUtils.setupRoot(app)
            val (ok, msg) = RootUtils.checkRootAndRtc()
            runOnUiThread {
                tvRootStatus.text = if (ok) "Root Status: Granted ✓" else "Root Status: Failed ✗"
                if (!ok) showStatusDialog("Root Warning", msg)
            }
        }.start()

        // 2. Re-apply saved schedule (cheap, keeps the alarm alive if it was lost)
        if (Scheduler.isEnabled(this)) Scheduler.schedule(this)
        refresh()

        // 3. Shutdown time picker: tap = set + enable, long-press = disable
        btnSetShutdownTime.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(Scheduler.hour(this))
                .setMinute(Scheduler.minute(this))
                .setTitleText("Select Auto-Shutdown Time")
                .build()

            picker.addOnPositiveButtonClickListener {
                Scheduler.save(this, picker.hour, picker.minute, true)
                Scheduler.schedule(this)
                Toast.makeText(
                    this,
                    String.format(Locale.getDefault(), "Shutdown scheduled daily at %02d:%02d", picker.hour, picker.minute),
                    Toast.LENGTH_SHORT
                ).show()
                refresh()
            }
            picker.show(supportFragmentManager, "SHUTDOWN_PICKER")
        }
        btnSetShutdownTime.setOnLongClickListener {
            Scheduler.cancel(this)
            Toast.makeText(this, "Auto-shutdown disabled", Toast.LENGTH_SHORT).show()
            refresh()
            true
        }

        // 4. Manual RTC test: arms a wake alarm 120s from RTC-now (relative)
        btnTestRtc.setOnClickListener {
            tvRtcStatus.text = "Testing RTC..."
            Thread {
                val (success, message) = RootUtils.setRtcWakeIn(app, 120)
                runOnUiThread {
                    Toast.makeText(this, if (success) "RTC Test Passed" else "RTC Test Failed", Toast.LENGTH_SHORT).show()
                    tvRtcStatus.text = "Last Test: $message"
                }
            }.start()
        }

        // 5. Manual shutdown (also arms the wake alarm first)
        btnManualShutdown.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Confirm Shutdown")
                .setMessage("Shut down now? The power-on alarm will be armed first if a system alarm exists.")
                .setPositiveButton("Shutdown") { _, _ ->
                    Thread {
                        Scheduler.armWake(app)
                        RootUtils.shutdownDevice()
                    }.start()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val sdf = SimpleDateFormat("EEE, dd MMM HH:mm", Locale.getDefault())

        btnSetShutdownTime.text = if (Scheduler.isEnabled(this))
            String.format(Locale.getDefault(), "Shutdown Time: %02d:%02d", Scheduler.hour(this), Scheduler.minute(this))
        else "Set Shutdown Time (off)"

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = alarmManager.nextAlarmClock
        tvNextAlarm.text = if (next != null)
            "Next System Alarm:\n${sdf.format(Date(next.triggerTime))}"
        else "Next System Alarm: None set"

        val sb = StringBuilder()
        sb.append(
            if (Scheduler.isEnabled(this)) "Auto-shutdown: ${sdf.format(Date(Scheduler.nextShutdownMs(this)))}"
            else "Auto-shutdown: off"
        )
        sb.append(
            if (next != null) "\nPower-on (RTC): ${sdf.format(Date(next.triggerTime - 300_000))}"
            else "\nPower-on: none (set a system alarm)"
        )
        tvRtcStatus.text = sb.toString()
    }

    private fun showStatusDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}