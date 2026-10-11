package com.amanaque.trabajo

import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var webView: WebView

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1001
    }

    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(ReminderBridge(), "AndroidReminder")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    updateReminderStatus()
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(webView)
        ReminderScheduler.scheduleNext(this)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            ReminderScheduler.scheduleNext(this)
            updateReminderStatus()
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                requestExactAlarmAccess()
            } else {
                updateReminderStatus()
            }
        }
    }

    private fun requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                return
            }
        }
        ReminderScheduler.scheduleNext(this)
        updateReminderStatus()
    }

    private fun updateReminderStatus() {
        val notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val exactAlarmAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        webView.evaluateJavascript(
            "window.setReminderStatus?.($notificationsAllowed, $exactAlarmAllowed)",
            null
        )
    }

    private inner class ReminderBridge {
        @JavascriptInterface
        fun enableReminders() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_NOTIFICATIONS
                    )
                } else {
                    requestExactAlarmAccess()
                }
            }
        }
    }

    @Deprecated("Deprecated in Android 13, but needed for older supported versions")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
