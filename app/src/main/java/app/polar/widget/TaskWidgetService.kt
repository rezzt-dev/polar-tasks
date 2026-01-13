package app.polar.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import app.polar.R
import app.polar.data.AppDatabase
import app.polar.data.entity.Task
import kotlinx.coroutines.runBlocking

class TaskWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TaskRemoteViewsFactory(this.applicationContext)
    }
}

class TaskRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    
    private var tasks: List<Task> = emptyList()

    override fun onCreate() {
        // init
    }

    override fun onDataSetChanged() {
        // Load tasks for today strictly
        val db = AppDatabase.getDatabase(context)
        
        // Get today start and end
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        val todayStart = calendar.timeInMillis
        
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        val todayEnd = calendar.timeInMillis

        // Run blocking because this is a background service thread (Binder pool)
        tasks = runBlocking {
             db.taskDao().getTasksBetweenDates(todayStart, todayEnd)
        }.filter { !it.completed } // Show only pending tasks
    }

    override fun onDestroy() {
        tasks = emptyList()
    }

    override fun getCount(): Int {
        return tasks.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= tasks.size) return RemoteViews(context.packageName, R.layout.item_widget_task)
        
        val task = tasks[position]
        val views = RemoteViews(context.packageName, R.layout.item_widget_task)
        
        views.setTextViewText(R.id.widgetTaskTitle, task.title)
        
        // Fill intent to open details
        val fillInIntent = Intent()
        // fillInIntent.putExtra("task_id", task.id) // If we want to open specific task
        views.setOnClickFillInIntent(R.id.widgetTaskTitle, fillInIntent)
        
        return views
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return tasks.getOrNull(position)?.id ?: position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }
}
