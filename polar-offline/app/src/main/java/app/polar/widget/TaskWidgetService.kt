package app.polar.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import app.polar.R
import app.polar.data.AppDatabase
import app.polar.data.entity.Task
import app.polar.ui.activity.TaskDetailActivity
import app.polar.util.RecurrenceUtils
import kotlinx.coroutines.runBlocking
import java.util.Calendar

class TaskWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TaskRemoteViewsFactory(this.applicationContext)
    }
}

/** Tareas pendientes de hoy, ordenadas por hora de vencimiento. */
class TaskRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<Task> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val todayStart = RecurrenceUtils.startOfDay(System.currentTimeMillis())
        val todayEnd = Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis - 1

        // Se ejecuta en un hilo del binder, nunca en el principal: bloquear aquí es seguro.
        tasks = try {
            runBlocking { AppDatabase.getDatabase(context).taskDao().getTasksBetweenDates(todayStart, todayEnd) }
                .filter { !it.completed }
                .sortedBy { it.dueDate }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun onDestroy() {
        tasks = emptyList()
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.item_widget_task)
        val task = tasks.getOrNull(position) ?: return views

        views.setTextViewText(R.id.widgetTaskTitle, task.title)
        val fillInIntent = Intent().putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.id)
        views.setOnClickFillInIntent(R.id.widgetItemRoot, fillInIntent)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = tasks.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
