package com.example.powerscheduler

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.CompoundButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var switchShutdown: MaterialSwitch
    private lateinit var tvShutdownTime: TextView
    private lateinit var tvShutdownNext: TextView
    private lateinit var tvWakeTime: TextView
    private lateinit var tvWakeSub: TextView
    private lateinit var tvLastResult: TextView
    private lateinit var chipRoot: Chip
    private lateinit var chipRtc: Chip

    private val clockFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dayFmt = SimpleDateFormat("EEE, dd MMM HH:mm", Locale.getDefault())

    private val switchListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        if (checked) {
            Scheduler.save(this, Scheduler.hour(this), Scheduler.minute(this), true)
            Scheduler.schedule(this)
        } else {
            Scheduler.cancel(this)
        }
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this) // Material You on Android 12+
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchShutdown = findViewById(R.id.switchShutdown)
        tvShutdownTime = findViewById(R.id.tvShutdownTime)
        tvShutdownNext = findViewById(R.id.tvShutdownNext)
        tvWakeTime = findViewById(R.id.tvWakeTime)
        tvWakeSub = findViewById(R.id.tvWakeSub)
        tvLastResult = findViewById(R.id.tvLastResult)
        chipRoot = findViewById(R.id.chipRoot)
        chipRtc = findViewById(R.id.chipRtc)
        val btnSetShutdownTime = findViewById<MaterialButton>(R.id.btnSetShutdownTime)
        val btnOpenAlarms = findViewById<MaterialButton>(R.id.btnOpenAlarms)
        val btnTestRtc = findViewById<MaterialButton>(R.id.btnTestRtc)
        val btnManualShutdown = findViewById<MaterialButton>(R.id.btnManualShutdown)

        // Root + RTC check off the main thread (su can block)
        val app = applicationContext
        Thread {
            RootUtils.setupRoot(app)
            val (rootOk, _) = RootUtils.su("id")
            val (rtcOk, _) = RootUtils.checkRootAndRtc()
            runOnUiThread {
                setChip(chipRoot, "Root", rootOk)
                setChip(chipRtc, "RTC", rtcOk)
            }
        }.start()

        // Keep the saved schedule alive
        if (Scheduler.isEnabled(this)) Scheduler.schedule(this)

        // Time picker: tapping the big time or the button
        val openPicker = {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(Scheduler.hour(this))
                .setMinute(Scheduler.minute(this))
                .setTitleText("Shutdown time")
                .build()
            picker.addOnPositiveButtonClickListener {
                Scheduler.save(this, picker.hour, picker.minute, true)
                Scheduler.schedule(this)
                refresh()
                snack(String.format(Locale.getDefault(), "Daily shutdown set for %02d:%02d", picker.hour, picker.minute))
            }
            picker.show(supportFragmentManager, "SHUTDOWN_PICKER")
        }
        btnSetShutdownTime.setOnClickListener { openPicker() }
        tvShutdownTime.setOnClickListener { openPicker() }

        // Open the system clock app to set the alarm that drives power-on
        btnOpenAlarms.setOnClickListener {
            try {
                startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            } catch (e: ActivityNotFoundException) {
                snack("No clock app found")
            }
        }

        // RTC test: arms a wake alarm 120s from RTC-now
        btnTestRtc.setOnClickListener {
            tvLastResult.text = "Testing RTC..."
            Thread {
                val (success, message) = RootUtils.setRtcWakeIn(app, 120)
                runOnUiThread {
                    tvLastResult.text = message
                    snack(if (success) "RTC test passed (alarm in 2 min)" else "RTC test failed")
                }
            }.start()
        }

        // Manual shutdown, arms the wake alarm first
        btnManualShutdown.setOnClickListener {
            val next = (getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock
            val msg = if (next != null)
                "Phone will power on again at ${clockFmt.format(Date(next.triggerTime - 300_000))}."
            else "No system alarm is set, so the phone will stay off until you power it on."

            MaterialAlertDialogBuilder(this)
                .setTitle("Shut down now?")
                .setMessage(msg)
                .setPositiveButton("Shut down") { _, _ ->
                    Thread {
                        Scheduler.armWake(app)
                        RootUtils.shutdownDevice()
                    }.start()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        // --- Shutdown card ---
        val enabled = Scheduler.isEnabled(this)
        switchShutdown.setOnCheckedChangeListener(null)
        switchShutdown.isChecked = enabled
        switchShutdown.setOnCheckedChangeListener(switchListener)

        tvShutdownTime.text = String.format(Locale.getDefault(), "%02d:%02d", Scheduler.hour(this), Scheduler.minute(this))
        tvShutdownTime.alpha = if (enabled) 1f else 0.4f
        tvShutdownNext.text = if (enabled) {
            val at = Scheduler.nextShutdownMs(this)
            "Next: ${dayFmt.format(Date(at))} (${untilText(at - System.currentTimeMillis())})"
        } else "Off"

        // --- Power-on card ---
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = alarmManager.nextAlarmClock
        if (next != null) {
            tvWakeTime.text = clockFmt.format(Date(next.triggerTime - 300_000))
            tvWakeTime.alpha = 1f
            tvWakeSub.text = "5 min before your ${dayFmt.format(Date(next.triggerTime))} alarm"
        } else {
            tvWakeTime.text = "--:--"
            tvWakeTime.alpha = 0.4f
            tvWakeSub.text = "Set a system alarm and the phone will power on before it"
        }
    }

    private fun untilText(ms: Long): String {
        val totalMin = (ms / 60_000).coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "in ${h}h ${m}m" else "in ${m}m"
    }

    private fun setChip(chip: Chip, label: String, ok: Boolean) {
        chip.text = if (ok) "$label ✓" else "$label ✗"
        val attr = if (ok) com.google.android.material.R.attr.colorSecondaryContainer
        else com.google.android.material.R.attr.colorErrorContainer
        chip.chipBackgroundColor = ColorStateList.valueOf(MaterialColors.getColor(chip, attr))
    }

    private fun snack(text: String) {
        Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_SHORT).show()
    }
}