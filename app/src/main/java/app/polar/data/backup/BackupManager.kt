package app.polar.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.polar.data.AppDatabase
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList
import app.polar.data.entity.Subtask
import app.polar.data.entity.Reminder
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter

data class BackupData(
    // 2: formato 100 % local, sin las columnas de sincronización de la versión 1 (v1.6).
    val version: Int = 2,
    val timestamp: Long = System.currentTimeMillis(),
    val taskLists: List<TaskList>,
    val tasks: List<Task>,
    val subtasks: List<Subtask>,
    val reminders: List<Reminder>
)

class BackupManager(private val context: Context) {

    private val gson = Gson()
    private val database = AppDatabase.getDatabase(context)

    suspend fun exportBackup(uri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val taskLists = database.taskListDao().getAllTaskListsSnapshot()
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
        }
    }

    // Acepta tanto copias actuales como las exportadas por la v1.6, que incluían listas y
    // subtareas borradas de forma lógica (`deletedAt`). Esas se descartan para no resucitarlas,
    // salvo las subtareas que se borraron junto a una tarea que sigue en la papelera. También se
    // descartan tareas y subtareas cuya lista o tarea no viene en la copia.
    private fun parseBackup(json: String): BackupData {
        val root = JsonParser.parseString(json).asJsonObject

        val trashedTaskDeletedAt = root.getAsJsonArray("tasks")
            ?.mapNotNull { element ->
                val task = element.asJsonObject
                val deletedAt = task.longOrNull("deletedAt")
                val id = task.longOrNull("id")
                if (task.booleanOrFalse("isDeleted") && deletedAt != null && id != null) id to deletedAt else null
            }
            ?.toMap()
            .orEmpty()

        root.filterArray("taskLists") { it.longOrNull("deletedAt") == null }
        root.filterArray("subtasks") { subtask ->
            val deletedAt = subtask.longOrNull("deletedAt") ?: return@filterArray true
            val parentDeletedAt = trashedTaskDeletedAt[subtask.longOrNull("taskId")]
            parentDeletedAt != null && deletedAt >= parentDeletedAt
        }

        val taskLists = root.listOf("taskLists", TaskList::class.java)
        val listIds = taskLists.map { it.id }.toSet()
        val tasks = root.listOf("tasks", Task::class.java).filter { it.listId in listIds }
        val taskIds = tasks.map { it.id }.toSet()
        val subtasks = root.listOf("subtasks", Subtask::class.java).filter { it.taskId in taskIds }
        val reminders = root.listOf("reminders", Reminder::class.java)

        return BackupData(taskLists = taskLists, tasks = tasks, subtasks = subtasks, reminders = reminders)
    }

    private fun <T> JsonObject.listOf(key: String, type: Class<T>): List<T> =
        getAsJsonArray(key)?.map { gson.fromJson(it, type) }.orEmpty()

    private fun JsonObject.filterArray(key: String, keep: (JsonObject) -> Boolean) {
        val array = getAsJsonArray(key) ?: return
        val kept = JsonArray()
        array.forEach { element -> if (keep(element.asJsonObject)) kept.add(element) }
        add(key, kept)
    }

    private fun JsonObject.longOrNull(key: String): Long? =
        get(key)?.takeUnless { it.isJsonNull }?.asLong

    private fun JsonObject.booleanOrFalse(key: String): Boolean =
        get(key)?.takeUnless { it.isJsonNull }?.asBoolean ?: false
}
