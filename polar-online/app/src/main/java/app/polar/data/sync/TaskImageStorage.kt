package app.polar.data.sync

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import java.io.File
import javax.inject.Inject

// Uploads/downloads task images against the `task-images` Supabase Storage bucket, per
// agent-docs/supabase-sync/03-esquema-supabase.md (path convention `{user_id}/{task_uuid}.jpg`)
// and 06-plan-implementacion-android.md punto 8. Task.imageUri (the local content:// URI) never
// leaves the device; this class only ever reads/writes Task.imagePath, the portable Storage path.
class TaskImageStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseClient: SupabaseClient
) {
    private val bucket get() = supabaseClient.storage.from(BUCKET)

    suspend fun upload(taskUuid: String, localImageUri: Uri): String? {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return null
        val bytes = try {
            context.contentResolver.openInputStream(localImageUri)?.use { it.readBytes() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return null

        val path = "$userId/$taskUuid.jpg"
        return try {
            bucket.upload(path, bytes) { upsert = true }
            path
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun downloadToCache(imagePath: String): Uri? {
        return try {
            val bytes = bucket.downloadAuthenticated(imagePath)
            val file = File(context.cacheDir, "task_images/${imagePath.replace('/', '_')}")
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        private const val BUCKET = "task-images"
    }
}
