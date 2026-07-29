package com.deskpet.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.core.app.NotificationCompat
import okhttp3.MediaType.Companion.toMediaType
import com.deskpet.R
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.*
import java.io.IOException

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null

    companion object {
        var SUPABASE_URL = "https://rvnruqwusqaynrcphgod.supabase.co"
        var SUPABASE_KEY = "sb_publishable_1o7IA1_fUweDJ2mRKj_YNw_ClbpBSzq"
        var lastBubbleId: Long = 0L
        var lastStateId: Long = 0L
        var pollingHandler: Handler? = null
        private const val CHANNEL_ID = "deskpet_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val PET_SIZE_DP = 180
        private const val PET_HEIGHT_DP = 200
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("[Pet] 小爪子正在看着你"))
        setupOverlay()
        startPolling()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            dpToPx(PET_SIZE_DP),
            dpToPx(PET_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.apply {
                cacheMode = WebSettings.LOAD_DEFAULT
                databaseEnabled = true
                domStorageEnabled = true
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/pet/index.html")
            setOnTouchListener(createTouchListener())
        }

        windowManager?.addView(overlayView, params)
    }

    // Touch handling
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var touchStartTime = 0L
    private var hasMoved = false
    private var tapCount = 0
    private var firstTapTime = 0L

    private fun createTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        hasMoved = true
                        params?.x = initialX + dx
                        params?.y = initialY + dy
                        windowManager?.updateViewLayout(overlayView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasMoved) {
                        val now = System.currentTimeMillis()
                        val elapsed = now - touchStartTime
                        if (elapsed > 600) {
                            evaluateJS("window.petEngine && window.petEngine.onLongPress()")
                        } else if (now - lastTapTime < 350) {
                            evaluateJS("window.petEngine && window.petEngine.onDoubleTap()")
                            lastTapTime = 0L
                        } else {
                            lastTapTime = now
                            // Multi-tap counting
                            if (tapCount == 0) firstTapTime = now
                           tapCount++
                            if (now - firstTapTime < 2000) {
                                when (tapCount) {
                                    3 -> { evaluateJS("window.petEngine && window.petEngine.onTripleTap()"); tapCount = 0 }
                                    else -> evaluateJS("window.petEngine && window.petEngine.onTap()")
                                }
                            } else {
                                tapCount = 0
                                  evaluateJS("window.petEngine && window.petEngine.onTap()")
                            }
                        }
                    } else {
                        evaluateJS("window.petEngine && window.petEngine.onDragEnd()")
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun evaluateJS(js: String) {
        overlayView?.post {
            overlayView?.evaluateJavascript(js, null)
        }
    }

    fun setPetState(state: String) {
        evaluateJS("window.petEngine && window.petEngine.setState('$state')")
    }

    fun showBubble(text: String, style: String = "normal") {
        val escaped = text.replace("\\", "\\\\").replace("'", "\\'")
        evaluateJS("window.petEngine && window.petEngine.showBubble('$escaped', '$style')")
    }
    private fun startPolling() {
        pollingHandler = Handler(Looper.getMainLooper())
        pollingHandler?.post(object : Runnable {
            override fun run() {
                pollAiState()
                pollingHandler?.postDelayed(this, 5000)
            }
        })
    }

    private fun pollAiState() {
        val client = OkHttpClient()
        val gson = Gson()

        // 读取 clawd_state 表情状态
        val stateUrl = "${SUPABASE_URL}/rest/v1/clawd_state?select=id,emotion,intensity&order=id.desc&limit=1"
        val stateReq = Request.Builder()
            .url(stateUrl)
            .addHeader("apikey", SUPABASE_KEY)
            .addHeader("Authorization", "Bearer ${SUPABASE_KEY}")
            .build()
        client.newCall(stateReq).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (body != null && body != "[]" && body.isNotEmpty()) {
                    try {
                        val arr = JsonParser.parseString(body).asJsonArray
                        if (arr.size() > 0) {
                            val obj = arr.get(0).asJsonObject
                            val id = obj.get("id").asLong
                            if (id > lastStateId) {
                                lastStateId = id
                                val emotion = obj.get("emotion").asString
                                setPetState(emotion)
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
        })

        // 读取 bubble_content 气泡消息
        val bubbleUrl = "${SUPABASE_URL}/rest/v1/bubble_content?select=id,content,style&is_read=eq.false&order=id.asc&limit=5"
        val bubbleReq = Request.Builder()
            .url(bubbleUrl)
            .addHeader("apikey", SUPABASE_KEY)
            .addHeader("Authorization", "Bearer ${SUPABASE_KEY}")
            .build()
        client.newCall(bubbleReq).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (body != null && body != "[]" && body.isNotEmpty()) {
                    try {
                        val arr = JsonParser.parseString(body).asJsonArray
                        for (i in 0 until arr.size()) {
                            val obj = arr.get(i).asJsonObject
                            val id = obj.get("id").asLong
                            if (id > lastBubbleId) {
                                lastBubbleId = id
                                val content = obj.get("content").asString
                                val style = obj.get("style")?.asString ?: "normal"
                                showBubble(content, style)
                                markBubbleRead(id)
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
        })
    }

    private fun markBubbleRead(id: Long) {
        val json = """{"is_read":true}"""
        val reqBody = RequestBody.create("application/json".toMediaType(), json)
        val url = "${SUPABASE_URL}/rest/v1/bubble_content?id=eq.${id}"
        val req = Request.Builder()
            .url(url)
            .method("PATCH", reqBody)
            .addHeader("apikey", SUPABASE_KEY)
            .addHeader("Authorization", "Bearer ${SUPABASE_KEY}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .build()
        val client = OkHttpClient()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {}
        })
    }

    // Notification
    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DeskPet")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DeskPet",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        overlayView = null
        super.onDestroy()
    }
}
