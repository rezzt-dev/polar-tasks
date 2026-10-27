package app.polar.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.polar.data.AppDatabase
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList
import app.polar.data.entity.Subtask
import app.polar.data.entity.Reminder
import app.polar.util.AlarmManagerHelper
import app.polar.util.AlarmRescheduler
import app.polar.util.GeofenceHelper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter

data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val taskLists: List<TaskList>,
    val tasks: List<Task>,
    val subtasks: List<Subtask>,
    val reminders: List<Reminder>
)

class BackupManager(private val context: Context) {

    private val gson = Gson()
    private val database = AppDatabase.getDatabase(context)
    private val alarmRescheduler = AlarmRescheduler(database, AlarmManagerHelper(context), GeofenceHelper(context))

    suspend fun exportBackup(uri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Se incluyen las listas borradas (tombstones): sus tareas siguen en la tabla, y sin
            // ellas la copia no se podría restaurar por la clave foránea tasks.listId.
            val taskLists = database.taskListDao().getAllTaskListsIncludingDeletedSnapshot()
            val tasks = database.taskDao().getAllTasksSnapshot()
            val subtasks = database.subtaskDao().getAllSubtasksSnapshot()
            val reminders = database.reminderDao().getAllRemindersSnapshot()

            val backupData = BackupData(
                taskLists = taskLists,
                tasks = tasks,
                subtasks = subtasks,
                reminders = reminders
            )

            val jsonString = gson.toJson(backupData)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(jsonString)
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // Restaurar sustituye todos los datos por los de la copia. Se hace en una única transacción:
    // si la copia no se puede insertar, no se pierde nada de lo que había en el dispositivo.
    suspend fun importBackup(uri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.reader().use { it.readText() }
            } ?: return@withContext Result.failure(Exception("Could not read file"))

            val backupData = parseBackup(jsonString)

            // Las alarmas apuntan a ids concretos: tras restaurar, esos ids pasan a ser de otros
            // elementos (o de ninguno). Se cancelan todas y se reconstruyen desde la copia.
            alarmRescheduler.cancelAll()
            database.withTransaction {
                database.subtaskDao().deleteAll()
                database.taskDao().deleteAll()
                database.taskListDao().deleteAll()
                database.reminderDao().deleteAll()

                database.taskListDao().insertAll(backupData.taskLists)
                database.taskDao().insertAll(backupData.tasks)
                database.subtaskDao().insertAll(backupData.subtasks)
                database.reminderDao().insertAll(backupData.reminders)
            }

            Result.success(true)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            // Si la restauración falla, la transacción deja los datos anteriores: se reprograman
            // igualmente sus alarmas, canceladas antes de empezar.
            runCatching { alarmRescheduler.rescheduleAll() }.onFailure { it.printStackTrace() }
        }
    }

    // Acepta copias de esta versión y de la versión 100 % local (sin columnas de sincronización).
    // Se descartan tareas y subtareas cuya lista o tarea no viene en la copia (copias exportadas
    // antes de incluir las listas borradas), que harían fallar la clave foránea.
    private fun parseBackup(json: String): BackupData {
        val root = JsonParser.parseString(json).asJsonObject

        val taskLists = root.listOf("taskLists", TaskList::class.java) { TaskList(title = "") }
        val listIds = taskLists.map { it.id }.toSet()
        val tasks = root.listOf("tasks", Task::class.java) { Task(listId = 0, title = "") }
            .filter { it.listId in listIds }
        val taskIds = tasks.map { it.id }.toSet()
        val subtasks = root.listOf("subtasks", Subtask::class.java) { Subtask(taskId = 0, title = "") }
            .filter { it.taskId in taskIds }
        val reminders = root.listOf("reminders", Reminder::class.java) { Reminder(title = "", dateTime = 0) }

        return BackupData(taskLists = taskLists, tasks = tasks, subtasks = subtasks, reminders = reminders)
    }

    // Gson no usa los valores por defecto de Kotlin: un campo que no venga en la copia (p. ej. el
    // `uuid` en una copia de la versión local) quedaría a null aunque el tipo no lo admita, y la
    // inserción fallaría. Cada elemento se superpone sobre una instancia por defecto nueva, así que
    // los uuid que falten se generan distintos para cada fila.
    private fun <T : Any> JsonObject.listOf(key: String, type: Class<T>, defaults: () -> T): List<T> =
        getAsJsonArray(key)?.map { element ->
            val merged = gson.toJsonTree(defaults()).asJsonObject
            element.asJsonObject.entrySet().forEach { (field, value) ->
                if (!value.isJsonNull) merged.add(field, value)
            }
            gson.fromJson(merged, type)
        }.orEmpty()
}
