package com.nuvio.app.features.luxo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nuvio.app.R

/** «تابع المشاهدة» home-screen widget: the latest titles in progress; tapping opens Luxo. */
class LuxoContinueWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
    }

    companion object {
        private const val PREFS = "luxo_customization"
        private const val KEY = "widget_continue_lines"

        fun buildViews(context: Context): RemoteViews {
            val lines = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null)
                ?.split('\n')
                ?.filter { it.isNotBlank() }
                .orEmpty()
            val views = RemoteViews(context.packageName, R.layout.luxo_widget_continue)
            views.setTextViewText(R.id.luxo_widget_line1, lines.getOrNull(0) ?: "افتح موج وابدأ مشاهدة")
            views.setTextViewText(R.id.luxo_widget_line2, lines.getOrNull(1) ?: "")
            views.setTextViewText(R.id.luxo_widget_line3, lines.getOrNull(2) ?: "")
            val next = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("widget_next_line", null)
            views.setTextViewText(R.id.luxo_widget_next, next ?: "")
            views.setViewVisibility(R.id.luxo_widget_next, if (next.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE)
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent().setClassName(context.packageName, "com.nuvio.app.MainActivity")
            launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val pending = PendingIntent.getActivity(
                context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.luxo_widget_root, pending)
            return views
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, LuxoContinueWidget::class.java))
            if (ids.isEmpty()) return
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
        }
    }
}

internal actual fun luxoPublishContinueWatching(lines: List<String>) {
    val context = LuxoPrefsStorage.appContext ?: return
    LuxoPrefsStorage.save("widget_continue_lines", lines.take(3).joinToString("\n"))
    runCatching { LuxoContinueWidget.refresh(context) }
}

internal actual fun luxoPublishWidgetNext(line: String?) {
    val context = LuxoPrefsStorage.appContext ?: return
    if (LuxoPrefsStorage.load("widget_next_line").orEmpty() == line.orEmpty()) return
    LuxoPrefsStorage.save("widget_next_line", line.orEmpty())
    runCatching { LuxoContinueWidget.refresh(context) }
}
