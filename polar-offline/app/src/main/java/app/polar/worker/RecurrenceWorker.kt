package app.polar.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.polar.data.AppDatabase
import app.polar.di.AlarmHelperEntryPoint
import app.polar.util.RecurrenceUtils
import app.polar.widget.TaskWidgetProvider
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reabre las tareas recurrentes completadas cuya siguiente ocurrencia ya ha empezado: las marca
 * como pendientes, les pone la nueva fecha de vencimiento y de creación, desmarca sus subtareas y
 * reprograma la alarma. La lógica de fechas vive en [RecurrenceUtils].
 */
class RecurrenceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(applicationContext)
            val taskDao = database.taskDao()
            val subtaskDao = database.subtaskDao()
            val alarmHelper = EntryPointAccessors
                .fromApplication(applicationContext, AlarmHelperEntryPoint::class.java)
                .getAlarmManagerHelper()

            val now = System.currentTimeMillis()
            var failed = false

            // Las tareas en la papelera se ignoran (startNextOccurrence): reabrirlas programaría
            // alarmas de algo que el usuario ya borró. Recurren al restaurarlas.
            taskDao.getCompletedRecurringTasks().forEach { task ->
                val reopened = RecurrenceUtils.startNextOccurrence(task, now) ?: return@forEach
                // Un fallo en una tarea no debe impedir que se procesen las demás.
                try {
                    taskDao.update(reopened)
                    subtaskDao.resetSubtasksForTask(task.id)
                    alarmHelper.scheduleTaskAlarm(task.id, reopened.dueDate!!)
                } catch (e: Exception) {
                    e.printStackTrace()
                    failed = true
                }
            }

            // Al cambiar de día cambia también qué tareas son "de hoy".
            TaskWidgetProvider.notifyDataChanged(applicationContext)

            if (failed) Result.retry() else Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
