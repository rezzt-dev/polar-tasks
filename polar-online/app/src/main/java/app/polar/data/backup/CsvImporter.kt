package app.polar.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.polar.data.AppDatabase
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Importa tareas desde un CSV (`titulo,descripcion` por fila, con cabecera opcional) a una lista
 * nueva. Todo se inserta en una única transacción: un fallo a mitad no deja una lista a medias.
 */
class CsvImporter(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)

    /** Devuelve cuántas tareas se han importado; con 0 no se crea ninguna lista. */
    suspend fun import(uri: Uri, listTitle: String): Int = withContext(Dispatchers.IO) {
        val content = context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
            ?: throw java.io.IOException("could not read $uri")

        val rows = parseRows(content)
        if (rows.isEmpty()) return@withContext 0

        database.withTransaction {
            val listId = database.taskListDao().insert(
                TaskList(
                    title = listTitle,
                    orderIndex = database.taskListDao().getNextOrderIndex(),
                    homeOrderIndex = database.taskListDao().getNextHomeOrderIndex()
                )
            )
            database.taskDao().insertAll(
                rows.mapIndexed { index, row ->
                    Task(listId = listId, title = row.title, description = row.description, orderIndex = index)
                }
            )
        }
        rows.size
    }

    data class Row(val title: String, val description: String)

    companion object {

        /** Filas con título, sin la cabecera (si la primera fila nombra las columnas). */
        fun parseRows(content: String): List<Row> {
            val records = parseCsv(content.removePrefix("\uFEFF"))
            val first = records.firstOrNull()
            val hasHeader = first != null && first.any { cell ->
                cell.contains("title", ignoreCase = true) ||
                    cell.contains("titulo", ignoreCase = true) ||
                    cell.contains("desc", ignoreCase = true)
            }
            return records
                .drop(if (hasHeader) 1 else 0)
                .mapNotNull { cells ->
                    val title = cells.getOrNull(0)?.trim().orEmpty()
                    if (title.isEmpty()) null else Row(title, cells.getOrNull(1)?.trim().orEmpty())
                }
        }

        // CSV según RFC 4180: comas y saltos de línea dentro de comillas, y "" como comilla literal.
        internal fun parseCsv(content: String): List<List<String>> {
            val records = mutableListOf<List<String>>()
            var record = mutableListOf<String>()
            val cell = StringBuilder()
            var inQuotes = false
            var i = 0

            fun endRecord() {
                record.add(cell.toString())
                cell.clear()
                if (record.any { it.isNotBlank() }) records.add(record)
                record = mutableListOf()
            }

            while (i < content.length) {
                val c = content[i]
                when {
                    inQuotes && c == '"' && content.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                    c == '"' -> inQuotes = !inQuotes
                    !inQuotes && c == ',' -> { record.add(cell.toString()); cell.clear() }
                    !inQuotes && (c == '\n' || c == '\r') -> {
                        if (c == '\r' && content.getOrNull(i + 1) == '\n') i++
                        endRecord()
                    }
                    else -> cell.append(c)
                }
                i++
            }
            if (cell.isNotEmpty() || record.isNotEmpty()) endRecord()
            return records
        }
    }
}
