package com.example.powerscheduler

import android.content.Context
import android.util.Log
import java.io.File

object RootUtils {
    private const val TAG = "RootUtils"
    private const val RTC_SYSFS = "/sys/class/rtc/rtc0/wakealarm"

    /** Run one command as root. Returns (exitCode == 0, combined output). Never call on main thread. */
    fun su(cmd: String): Pair<Boolean, String> {
        return try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            val code = p.waitFor()
            Log.d(TAG, "su [$cmd] -> exit $code: $out")
            (code == 0) to out
        } catch (e: Exception) {
            Log.e(TAG, "su failed: $cmd", e)
            false to "su exception: ${e.localizedMessage}"
        }
    }

    /** One-time root setup: allow exact alarms and exempt the app from Doze/battery kill. */
    fun setupRoot(context: Context) {
        val pkg = context.packageName
        su("cmd appops set $pkg SCHEDULE_EXACT_ALARM allow")
        su("dumpsys deviceidle whitelist +$pkg")
    }

    fun checkRootAndRtc(): Pair<Boolean, String> {
        val (ok, out) = su("ls -l /dev/rtc0")
        return if (ok && out.isNotEmpty()) true to "Root granted & RTC ready ($out)"
        else false to "Root denied or /dev/rtc0 not accessible: $out"
    }

    /** Copies the rtc_setter binary out of assets and makes it executable. */
    private fun getExecutableBinary(context: Context): File? {
        val binFile = File(context.filesDir, "rtc_setter")
        return try {
            context.assets.open("rtc_setter").use { input ->
                binFile.outputStream().use { output -> input.copyTo(output) }
            }
            binFile.setReadable(true, false)
            binFile.setExecutable(true, false)
            binFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract rtc_setter from assets", e)
            null
        }
    }

    /** Reads /proc/driver/rtc. Returns (alarm_IRQ == yes, short summary). */
    fun rtcState(): Pair<Boolean, String> {
        val (ok, out) = su("cat /proc/driver/rtc")
        if (!ok) return false to "cannot read /proc/driver/rtc"
        val armed = Regex("alarm_IRQ\\s*:\\s*yes").containsMatchIn(out)
        val time = Regex("alrm_time\\s*:\\s*(\\S+)").find(out)?.groupValues?.get(1)
        val date = Regex("alrm_date\\s*:\\s*(\\S+)").find(out)?.groupValues?.get(1)
        return armed to "alarm $date $time"
    }

    /**
     * Arms the RTC wake alarm [deltaSec] seconds from the RTC's own current time
     * (relative, so the RTC's wrong year does not matter).
     * Primary: bundled rtc_setter binary. Fallback: sysfs wakealarm.
     */
    fun setRtcWakeIn(context: Context, deltaSec: Long): Pair<Boolean, String> {
        if (deltaSec < 30) return false to "Wake time is too close or in the past (${deltaSec}s)"

        val bin = getExecutableBinary(context)
        var binMsg = "binary missing from assets"
        if (bin != null) {
            val (ok, out) = su("${bin.absolutePath} $deltaSec")
            if (ok && out.contains("SUCCESS")) {
                return true to "RTC wake set (+${deltaSec}s): $out"
            }
            binMsg = "rtc_setter failed: $out"
        }

        // Fallback: sysfs. Clear first, because "+N" ADDS to an already-enabled alarm.
        su("echo 0 > $RTC_SYSFS")
        su("echo +$deltaSec > $RTC_SYSFS")
        val (armed, info) = rtcState()
        return if (armed) true to "RTC wake set via sysfs for +${deltaSec}s ($info) [binary: $binMsg]"
        else false to "RTC alarm NOT armed. $binMsg | sysfs: $info"
    }

    fun shutdownDevice(): Pair<Boolean, String> {
        return su("sync; reboot -p || svc power shutdown")
    }
}