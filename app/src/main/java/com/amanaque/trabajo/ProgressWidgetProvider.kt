package com.amanaque.trabajo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ProgressWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, createViews(context))
        }
    }

    companion object {
        const val PREFERENCES_NAME = "progress_widget"
        const val ENTRIES_KEY = "entries"
        const val OBJECTIVES_KEY = "objectives"

        fun updateWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ProgressWidgetProvider::class.java)
            val widgetIds = manager.getAppWidgetIds(component)
            if (widgetIds.isNotEmpty()) {
                ProgressWidgetProvider().onUpdate(context, manager, widgetIds)
            }
        }

        private fun createViews(context: Context): RemoteViews {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val entries = JSONObject(preferences.getString(ENTRIES_KEY, "{}") ?: "{}")
            val objectives = JSONArray(preferences.getString(OBJECTIVES_KEY, "[]") ?: "[]")
            val now = Calendar.getInstance()
            val monthPrefix = SimpleDateFormat("yyyy-MM-", Locale.US).format(now.time)
            val yearPrefix = SimpleDateFormat("yyyy-", Locale.US).format(now.time)
            val currentMonth = SimpleDateFormat("MMMM", Locale("es")).format(now.time)
                .replaceFirstChar { it.titlecase(Locale("es")) }

            var monthEarnings = 0.0
            var workedDays = 0
            entries.keys().forEach { date ->
                if (date.startsWith(monthPrefix)) {
                    val entry = entries.optJSONObject(date) ?: JSONObject()
                    if (entry.optString("status") == "worked") {
                        monthEarnings += entry.optDouble("amount", 0.0)
                        workedDays += 1
                    }
                }
            }

            val incomeGoal = (0 until objectives.length())
                .mapNotNull { objectives.optJSONObject(it) }
                .filter { goal ->
                    val createdOn = goal.optString("createdOn")
                    goal.optString("type") == "income" &&
                        (goal.optString("period") == "yearly" && createdOn.startsWith(yearPrefix) ||
                            goal.optString("period") == "monthly" && createdOn.startsWith(monthPrefix))
                }
                .maxByOrNull { it.optString("createdOn") }

            val completedObjectives = (0 until objectives.length())
                .mapNotNull { objectives.optJSONObject(it) }
                .count { it.optString("type") != "income" && it.optBoolean("completed") }
            val personalObjectives = (0 until objectives.length())
                .mapNotNull { objectives.optJSONObject(it) }
                .count { it.optString("type") != "income" }

            val views = RemoteViews(context.packageName, R.layout.progress_widget)
            views.setTextViewText(R.id.widget_month, currentMonth)
            views.setTextViewText(
                R.id.widget_earnings,
                formatAmount(monthEarnings)
            )
            views.setTextViewText(R.id.widget_worked_days, workedDays.toString())

            val progressLabel: String
            val progress: Int
            if (incomeGoal != null) {
                val target = incomeGoal.optDouble("targetAmount", 0.0)
                val isYearly = incomeGoal.optString("period") == "yearly"
                val goalPrefix = if (isYearly) yearPrefix else monthPrefix
                val earned = if (isYearly) {
                    sumEarnings(entries, goalPrefix)
                } else {
                    monthEarnings
                }
                val title = incomeGoal.optString("title", "Meta de ganancias")
                progressLabel = "$title · ${formatAmount(earned)} de ${formatAmount(target)}"
                progress = if (target > 0) ((earned / target) * 100).toInt().coerceIn(0, 100) else 0
            } else if (personalObjectives > 0) {
                progressLabel = "Objetivos: $completedObjectives de $personalObjectives cumplidos"
                progress = (completedObjectives * 100 / personalObjectives).coerceIn(0, 100)
            } else {
                progressLabel = "Crea un objetivo para seguir tu avance"
                progress = 0
            }
            views.setTextViewText(R.id.widget_objective, progressLabel)
            views.setProgressBar(R.id.widget_progress, 100, progress, false)

            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            return views
        }

        private fun sumEarnings(entries: JSONObject, prefix: String): Double {
            var total = 0.0
            entries.keys().forEach { date ->
                if (date.startsWith(prefix)) {
                    val entry = entries.optJSONObject(date) ?: JSONObject()
                    if (entry.optString("status") == "worked") {
                        total += entry.optDouble("amount", 0.0)
                    }
                }
            }
            return total
        }

        private fun formatAmount(amount: Double): String =
            NumberFormat.getNumberInstance(Locale("es")).apply {
                maximumFractionDigits = 2
            }.format(amount)
    }
}
