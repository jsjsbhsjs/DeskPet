package com.deskpet.backend

import okhttp3.*
import okhttp3.sse.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType

class SupabaseClient(private val url: String, private val anonKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var eventListener: ((JSONObject) -> Unit)? = null
    private var sse: EventSource? = null

    fun connect(realtimeTopic: String = "clawd_state", onEvent: (JSONObject) -> Unit) {
        this.eventListener = onEvent
        // REST polling fallback (5s interval)
        startPolling(realtimeTopic)
    }

    private fun startPolling(topic: String) {
        val request = Request.Builder()
            .url("$url/rest/v1/$topic?select=*&order=created_at.desc&limit=1")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (body != null && body != "[]") {
                    try {
                        val arr = org.json.JSONArray(body)
                        if (arr.length() > 0) {
                            eventListener?.invoke(arr.getJSONObject(0))
                        }
                    } catch (e: Exception) {}
                }
            }
        })
    }

    fun sendState(state: JSONObject) {
        val request = Request.Builder()
            .url("$url/rest/v1/clawd_state")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .post(RequestBody.create("application/json".toMediaType(), state.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {}
            override fun onResponse(call: Call, response: Response) {}
        })
    }

    fun disconnect() {
        sse?.cancel()
        client.dispatcher.executorService.shutdown()
    }
}
