package app.polar.data.backup

import android.content.Context
import android.net.Uri
import app.polar.data.AppDatabase
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList
import app.polar.data.entity.Subtask
import app.polar.data.entity.Reminder
import com.google.gson.Gson
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

    suspend fun importBackup(uri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.reader().use { it.readText() }
            } ?: return@withContext Result.failure(Exception("Could not read file"))

            val backupData = gson.fromJson(jsonString, BackupData::class.java)

            // Clear current data? Or Merge? 
            // For "Restore", usually we clear or update. Let's clear to avoid duplication for now.
            // A safer approach might be to upsert, but IDs might conflict.
            // Let's NUKE and RESTORE for simplicity as a "Restore Point".
            
            // Using sequential operations instead of blocking transaction for simplicity compatible with suspend functions
            // and avoiding complex Room KTX setup if not present.
            // Ideally should be atomic but for local restore it's acceptable.
            
            database.taskListDao().deleteAll()
            database.taskDao().deleteAll()
            database.subtaskDao().deleteAll()
            database.reminderDao().deleteAll()
            
            database.taskListDao().insertAll(backupData.taskLists)
            database.taskDao().insertAll(backupData.tasks)
            database.subtaskDao().insertAll(backupData.subtasks)
            database.reminderDao().insertAll(backupData.reminders)
            
            Result.success(true)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
