package com.example.powerscheduler

import android.app.AlarmManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.status_text)
        val btnTestRoot = findViewById<Button>(R.id.btn_test_root)
        val btnTestAlarm = findViewById<Button>(R.id.btn_test_alarm)

        btnTestRoot.setOnClickListener {
            val (success, message) = RootUtils.checkRoot()
            statusText.text = "Root Test Result:\n$message"
            Toast.makeText(this, if (success) "Root Access OK!" else "Root Failed!", Toast.LENGTH_SHORT).show()
        }

        btnTestAlarm.setOnClickListener {
            try {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val nextAlarm = alarmManager.nextAlarmClock

                if (nextAlarm != null) {
                    val triggerTimeMs = nextAlarm.triggerTime
                    val triggerTimeSec = triggerTimeMs / 1000
                    val wakeTimeSec = triggerTimeSec - 300 // 5 minutes prior
                    val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(triggerTimeMs))

                    val (rootSuccess, rootMessage) = RootUtils.setRtcWakeAlarm(this, wakeTimeSec)

                    statusText.text = "Detected System Alarm:\n$formattedTime\n\nKernel Sync Result:\n$rootMessage"
                    Toast.makeText(this, if (rootSuccess) "Alarm synced to RTC!" else "RTC sync failed root check", Toast.LENGTH_LONG).show()
                } else {
                    RootUtils.setRtcWakeAlarm(this, 0)
                    statusText.text = "No active system alarms found via AlarmManager.\nSet an alarm in your Clock app first!"
                    Toast.makeText(this, "No alarms detected", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                statusText.text = "Alarm Test Error:\n${e.message}"
            }
        }
    }
}