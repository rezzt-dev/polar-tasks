package app.polar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import app.polar.MainActivity
import app.polar.R
import app.polar.ui.activity.TaskDetailActivity
import app.polar.util.ThemeManager

// setRemoteAdapter y notifyAppWidgetViewDataChanged están obsoletas desde API 35 a favor de
// RemoteCollectionItems, que no existe para minSdk 24 sin añadir otra dependencia.
@Suppress("DEPRECATION")
class TaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widgetListView)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) notifyDataChanged(context)
    }

    companion object {
        private const val ACTION_REFRESH = "app.polar.widget.ACTION_REFRESH"

        /** Recarga la lista de todos los widgets colocados; no hace nada si no hay ninguno. */
        fun notifyDataChanged(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
            if (appWidgetIds.isNotEmpty()) {
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widgetListView)
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val localized = ThemeManager.localizedContext(context)
            val views = RemoteViews(context.packageName, R.layout.widget_task_list)

            views.setTextViewText(R.id.widgetTitle, localized.getString(R.string.widget_title))
            views.setTextViewText(R.id.empty_view, localized.getString(R.string.widget_empty))
            views.setContentDescription(R.id.btnRefresh, localized.getString(R.string.widget_refresh))

            // Title intent (open app)
            val titlePendingIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetTitle, titlePendingIntent)

            // List adapter intent
            views.setRemoteAdapter(R.id.widgetListView, Intent(context, TaskWidgetService::class.java))
            views.setEmptyView(R.id.widgetListView, R.id.empty_view)

            // Cada fila completa esta plantilla con el id de su tarea (fillInIntent), así que en
            // Android 12+ tiene que ser mutable.
            val detailIntent = Intent(context, TaskDetailActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val detailTemplate = PendingIntent.getActivity(
                context, 1, detailIntent, PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
            )
            views.setPendingIntentTemplate(R.id.widgetListView, detailTemplate)

            // Refresh button
            val refreshIntent = Intent(context, TaskWidgetProvider::class.java).setAction(ACTION_REFRESH)
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, 0, refreshIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.btnRefresh, refreshPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
