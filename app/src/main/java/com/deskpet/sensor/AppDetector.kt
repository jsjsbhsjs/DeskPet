package com.deskpet.sensor

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper

class AppDetector(private val context: Context, private val onAppChange: (String) -> Unit) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastApp = ""
    private var isRunning = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            detect()
            handler.postDelayed(this, 3000)
        }
    }

    fun start() {
        isRunning = true
        handler.post(pollRunnable)
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacks(pollRunnable)
    }

    private fun detect() {
        try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 5000
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime
            )
            if (usageStats != null) {
                var topApp = ""
                var topTime = 0L
                for (stats in usageStats) {
                    if (stats.lastTimeUsed > topTime) {
                        topTime = stats.lastTimeUsed
                        topApp = stats.packageName
                    }
                }
                if (topApp.isNotEmpty() && topApp != lastApp) {
                    lastApp = topApp
                    onAppChange(topApp)
                }
            }
        } catch (e: Exception) {
            // UsageStats permission not granted
        }
    }
}
