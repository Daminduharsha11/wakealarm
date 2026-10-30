package com.example.powerscheduler

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val testButton = findViewById<Button>(R.id.testButton)

        try {
            statusText.text = "Status: Ready (Listening for alarms & boot)"
        } catch (e: Exception) {
            statusText.text = "Status Error: ${e.localizedMessage}"
        }

        testButton.setOnClickListener {
            // Test setting an RTC wake alarm 1 hour from now
            val testEpochSec = (System.currentTimeMillis() / 1000) + 3600
            val (success, message) = RootUtils.setRtcWakeAlarm(this, testEpochSec)
            
            if (success) {
                Toast.makeText(this, "RTC Alarm set successfully!", Toast.LENGTH_SHORT).show()
                statusText.text = "Last Test: SUCCESS\n$message"
            } else {
                Toast.makeText(this, "Failed to write RTC. See status.", Toast.LENGTH_LONG).show()
                statusText.text = "Last Test: FAILED\n$message"
            }
        }
    }
}