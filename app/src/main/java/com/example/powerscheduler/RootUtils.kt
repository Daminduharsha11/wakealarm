package com.example.powerscheduler

import android.content.Context
import android.util.Log
import java.io.File
import java.io.DataOutputStream

object RootUtils {
    private const val TAG = "RootUtils"

    fun checkRoot(): Pair<Boolean, String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                true to "Root access verified successfully (su working)!"
            } else {
                false to "Root permission denied or failed (Exit code: $exitCode)"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Root check exception", e)
            false to "Root exception: ${e.localizedMessage}"
        }
    }

    private fun getExecutableBinary(context: Context): File? {
        val binFile = File(context.filesDir, "rtc_setter")
        try {
            // Always copy/refresh the binary from assets to ensure the latest compiled version is used
            context.assets.open("rtc_setter").use { input ->
                binFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            // Ensure execute permissions on the private file
            val chmodProcess = Runtime.getRuntime().exec(arrayOf("chmod", "755", binFile.absolutePath))
            chmodProcess.waitFor()
            return binFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract binary from assets", e)
            return null
        }
    }

    fun setRtcWakeAlarm(context: Context, epochSeconds: Long): Pair<Boolean, String> {
        return try {
            val binFile = getExecutableBinary(context) 
                ?: return false to "Failed to extract rtc_setter binary from assets"
            
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            
            // Execute the compiled binary with root privileges
            val cmd = "${binFile.absolutePath} $epochSeconds\n"
            outputStream.writeBytes(cmd)
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            
            val reader = process.inputStream.bufferedReader()
            val output = reader.readText()
            val errReader = process.errorStream.bufferedReader()
            val errorOutput = errReader.readText()
            
            process.waitFor()
            val exitCode = process.exitValue()
            
            Log.d(TAG, "Binary exec exit: $exitCode, out: $output, err: $errorOutput")
            
            if (exitCode == 0 && output.contains("SUCCESS")) {
                true to "RTC alarm set successfully via bundled binary for epoch $epochSeconds"
            } else {
                false to "Execution failed (Exit: $exitCode). Out: ${output.trim()} Err: ${errorOutput.trim()}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute root command", e)
            false to "Exception: ${e.localizedMessage}"
        }
    }
}