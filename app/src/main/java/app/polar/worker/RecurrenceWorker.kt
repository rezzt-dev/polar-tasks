package app.polar.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.polar.data.AppDatabase
import app.polar.data.entity.Task
import app.polar.receiver.AlarmReceiver
import android.content.Intent
import android.app.PendingIntent
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.Calendar
import dagger.hilt.android.EntryPointAccessors
import app.polar.di.AlarmHelperEntryPoint

class RecurrenceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(applicationContext)
            val taskDao = database.taskDao()
            val subtaskDao = database.subtaskDao()
            
            // Get AlarmHelper
            val hiltEntryPoint = EntryPointAccessors.fromApplication(applicationContext, AlarmHelperEntryPoint::class.java)
            val alarmHelper = hiltEntryPoint.getAlarmManagerHelper()
            
            // We need to fetch tasks that are effectively "done" but waiting to recur.
            // Our new logic keeps them as "completed".
            // We need to find completed tasks with recurrence != NONE and dueDate <= Now (or close to match typical recurrence checks).
            // Actually, we want to reset them when their "next occurrence" starts.
            // But wait, if I mark it done today (Jan 14), and it repeats daily...
            // It should reappear on Jan 15 at 00:00.
            // So if I run this worker periodically (e.g. every hour or just after midnight),
            // I should look for tasks where: completed = true AND recurrence != NONE.
            // AND the dueDate for the *next* occurrence corresponds to "today" or "past".
            
            // Wait, if I kept the OLD dueDate when marking complete:
            // e.g. Due Jan 14. Completed Jan 14.
            // On Jan 15, I want to say "Hey, this is recurring. Uncheck it. Set due date to Jan 15."
            // So logic:
            // Find all completed recurring tasks.
            // Calculate their *next* due date based on current due date + interval.
            // If *next* due date <= Now, then RESET.
            
            val tasks = taskDao.getAllTasksSnapshot() // Use snapshot or a specific query
            val recurringCompletedTasks = tasks.filter { it.completed && it.recurrence != "NONE" && it.dueDate != null }
            
            val now = System.currentTimeMillis()
            
            recurringCompletedTasks.forEach { task ->
                val nextDueDate = calculateNextDueDate(task.dueDate!!, task.recurrence)
                
                // If the next occurrence has arrived (or we are present in it)
                if (nextDueDate <= now) {
                    // Reset the task
                    val updatedTask = task.copy(
                        completed = false,
                        dueDate = nextDueDate
                        // Keep creation date or update? Keep original creation date logic usually.
                    )
                    taskDao.update(updatedTask)
                    
                    // Reset subtasks
                    // We need to get subtasks for this task
                    // We don't have a direct snapshot method for subtasks of a task in DAO that is synchronous easily available without opening another accessed method?
                    // Let's assume we can query.
                    // Actually TaskViewModel uses `repository.getSubtasksForTask(taskId)` which returns LiveData.
                    // We need a suspend function in DAO: `getSubtasksForTaskSnapshot`.
                    // We'll add it to SubtaskDao if missing.
                    // If not simple, we can't reset subtasks easily without DAO update.
                    // Let's assume we update DAO or just skip if too complex for now, BUT user asked for it.
                    // "la tarea se desmarque por completo, tanto ella como las subtareas"
                    
                    // I will execute raw query via Room or add method? adding method is cleaner.
                    subtaskDao.resetSubtasksForTask(task.id)
                    
                    // Schedule alarm for the NEW due date
                    alarmHelper.scheduleTaskAlarm(task.id, nextDueDate)
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
    
    private fun calculateNextDueDate(currentDueDate: Long, recurrence: String): Long {
         val calendar = Calendar.getInstance()
         calendar.timeInMillis = currentDueDate
         
         // If the currentDueDate is way in the past (e.g. user matched a task from 3 days ago),
         // we probably want to jump to *today* or just add 1 interval?
         // User said "si estamos a dia 14 y tenemos una tarea que es diaria y vamos a pasar a las 00:01 del dia 15"
         // This implies standard interval addition.
         // But what if I missed 5 days? Should it appear for "Today" immediately?
         // Standard repetition usually just adds 1 interval from previous due date.
         
         when (recurrence) {
             "DAILY" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
             "WEEKLY" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
             "MONTHLY" -> calendar.add(Calendar.MONTH, 1)
         }
         return calendar.timeInMillis
    }


}
