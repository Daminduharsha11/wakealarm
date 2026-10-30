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

    private var shutdownHour = 23
    private var shutdownMinute = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvRootStatus = findViewById<TextView>(R.id.tvRootStatus)
        val tvNextAlarm = findViewById<TextView>(R.id.tvNextAlarm)
        val tvRtcStatus = findViewById<TextView>(R.id.tvRtcStatus)
        val btnSetShutdownTime = findViewById<Button>(R.id.btnSetShutdownTime)
        val btnTestRtc = findViewById<Button>(R.id.btnTestRtc)
        val btnManualShutdown = findViewById<Button>(R.id.btnManualShutdown)

        // 1. Verify Root & RTC on Startup and show friendly dialog
        val (rootOk, rootMsg) = RootUtils.checkRootAndRtc()
        if (rootOk) {
            tvRootStatus.text = "Root Status: Granted ✓"
            showStatusDialog("Root Access Confirmed", rootMsg)
        } else {
            tvRootStatus.text = "Root Status: Failed ✗"
            showStatusDialog("Root Warning", rootMsg)
        }

        // 2. Load system alarm status
        updateNextAlarmDisplay(tvNextAlarm, tvRtcStatus)

        // 3. Time Picker for Shutdown
        btnSetShutdownTime.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(shutdownHour)
                .setMinute(shutdownMinute)
                .setTitleText("Select Auto-Shutdown Time")
                .build()

            picker.addOnPositiveButtonClickListener {
                shutdownHour = picker.hour
                shutdownMinute = picker.minute
                val timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", shutdownHour, shutdownMinute)
                btnSetShutdownTime.text = "Shutdown Time: $timeFormatted"
                Toast.makeText(this, "Shutdown scheduled for $timeFormatted", Toast.LENGTH_SHORT).show()
            }

            picker.show(supportFragmentManager, "SHUTDOWN_PICKER")
        }

        // 4. Manual RTC Test
        btnTestRtc.setOnClickListener {
            val testEpochSec = (System.currentTimeMillis() / 1000) + 3600
            val (success, message) = RootUtils.setRtcWakeAlarm(this, testEpochSec)
            Toast.makeText(this, if (success) "RTC Test Passed" else "RTC Test Failed", Toast.LENGTH_SHORT).show()
            tvRtcStatus.text = "Last Test: $message"
        }

        // 5. Manual Shutdown
        btnManualShutdown.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Confirm Shutdown")
                .setMessage("Are you sure you want to shut down the device now?")
                .setPositiveButton("Shutdown") { _, _ -> RootUtils.shutdownDevice() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showStatusDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateNextAlarmDisplay(tvNextAlarm: TextView, tvRtcStatus: TextView) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextAlarm = alarmManager.nextAlarmClock

        if (nextAlarm != null) {
            val sdf = SimpleDateFormat("EEE, dd MMM HH:mm", Locale.getDefault())
            val dateStr = sdf.format(Date(nextAlarm.triggerTime))
            tvNextAlarm.text = "Next System Alarm:\n$dateStr"
            
            val rtcEpochSec = (nextAlarm.triggerTime / 1000) - 300
            tvRtcStatus.text = "RTC Auto-Wake armed for:\n${sdf.format(Date(rtcEpochSec * 1000))}"
        } else {
            tvNextAlarm.text = "Next System Alarm: None set"
            tvRtcStatus.text = "RTC Auto-Wake: Inactive"
        }
    }
}