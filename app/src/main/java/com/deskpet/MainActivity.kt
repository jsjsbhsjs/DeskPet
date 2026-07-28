package com.deskpet

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.deskpet.service.OverlayService

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            requestPermissionsAndStart()
        }
    }

    private fun requestPermissionsAndStart() {
        val missing = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                missing.add("悬浮窗权限 (SYSTEM_ALERT_WINDOW)")
            }
        }

        if (missing.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("需要以下权限")
                .setMessage(missing.joinToString("\n"))
                .setPositiveButton("去设置") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            startService(Intent(this, OverlayService::class.java))
            finish()
        }
    }
}
