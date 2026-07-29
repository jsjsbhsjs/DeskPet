package com.deskpet

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.deskpet.service.OverlayService

class MainActivity : AppCompatActivity() {

    companion object {
        const val SUPABASE_URL = "https://rvnruqwusqaynrcphgod.supabase.co"
        const val SUPABASE_KEY = "sb_publishable_1o7IA1_fUweDJ2mRKj_YNw_ClbpBSzq"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        OverlayService.SUPABASE_URL = SUPABASE_URL
        OverlayService.SUPABASE_KEY = SUPABASE_KEY

        val btnStart = findViewById<Button>(R.id.btn_start)
        val btnStop = findViewById<Button>(R.id.btn_stop)

        btnStart.setOnClickListener {
            val intent = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "🐾 桌宠已启动！", Toast.LENGTH_SHORT).show()
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "桌宠已关闭", Toast.LENGTH_SHORT).show()
        }
    }
}
