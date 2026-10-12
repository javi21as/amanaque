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
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var webView: WebView

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1001
        private const val REQUEST_EXPORT = 2001
        private const val REQUEST_IMPORT = 2002
    }

    private var pendingExport: String? = null

    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(ReminderBridge(), "AndroidReminder")
            addJavascriptInterface(ProgressWidgetBridge(), "AndroidProgressWidget")
            addJavascriptInterface(BackupBridge(), "AndroidBackup")
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

    private inner class ProgressWidgetBridge {
        @JavascriptInterface
        fun updateProgress(entries: String, objectives: String) {
            getSharedPreferences(ProgressWidgetProvider.PREFERENCES_NAME, MODE_PRIVATE)
                .edit()
                .putString(ProgressWidgetProvider.ENTRIES_KEY, entries)
                .putString(ProgressWidgetProvider.OBJECTIVES_KEY, objectives)
                .apply()
            ProgressWidgetProvider.updateWidgets(this@MainActivity)
        }
    }

    private fun notifyPage(message: String, ok: Boolean) {
        webView.evaluateJavascript(
            "window.backupMessage?.(${JSONObject.quote(message)}, $ok)",
            null
        )
    }

    private inner class BackupBridge {
        @JavascriptInterface
        fun exportData(json: String) {
            runOnUiThread {
                pendingExport = json
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "mi-almanaque-$date.json")
                }
                startActivityForResult(intent, REQUEST_EXPORT)
            }
        }

        @JavascriptInterface
        fun importData() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, REQUEST_IMPORT)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            pendingExport = null
            return
        }
        when (requestCode) {
            REQUEST_EXPORT -> {
                val json = pendingExport ?: return
                pendingExport = null
                try {
                    contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("No se pudo abrir el archivo")
                    notifyPage("Copia guardada correctamente.", true)
                } catch (e: Exception) {
                    notifyPage("No se pudo guardar la copia.", false)
                }
            }
            REQUEST_IMPORT -> {
                try {
                    val text = contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException("No se pudo abrir el archivo")
                    webView.evaluateJavascript("window.applyBackup?.(${JSONObject.quote(text)})", null)
                } catch (e: Exception) {
                    notifyPage("No se pudo leer el archivo.", false)
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
