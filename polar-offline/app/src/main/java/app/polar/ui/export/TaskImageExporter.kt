package app.polar.ui.export

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.widget.TextViewCompat
import app.polar.R
import app.polar.data.entity.Subtask
import app.polar.data.entity.Task
import app.polar.util.DateUtils
import java.io.File
import java.io.FileOutputStream

/**
 * Renderiza una tarea en la tarjeta `layout_task_export_card` y la comparte como imagen.
 * Lo usan la lista de tareas y el detalle, que antes duplicaban todo este código.
 */
object TaskImageExporter {

    private const val CARD_WIDTH_DP = 380f
    private const val MAX_SUBTASKS = 8

    fun share(context: Context, task: Task, subtasks: List<Subtask>) {
        val card = render(context, task, subtasks)
        try {
            val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val file = File(dir, "task_export_${task.id}.png")
            FileOutputStream(file).use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }

            val uri = FileProvider.getUriForFile(context, "app.polar.fileprovider", file) ?: return
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(uri, context.contentResolver.getType(uri))
                putExtra(Intent.EXTRA_STREAM, uri)
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_task_as_image)))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun render(context: Context, task: Task, subtasks: List<Subtask>): Bitmap {
        val card = LayoutInflater.from(context).inflate(R.layout.layout_task_export_card, null)

        card.findViewById<TextView>(R.id.tvExportTaskTitle).text = task.title

        card.findViewById<TextView>(R.id.tvExportTaskDesc).apply {
            text = task.description
            visibility = if (task.description.isNotBlank()) View.VISIBLE else View.GONE
        }

        // Prioridad: punto de color y etiqueta; sin prioridad no se muestra nada.
        card.findViewById<TextView>(R.id.tvPriorityBadge).apply {
            val (label, colorAttr) = when (task.priority) {
                1 -> R.string.priority_label_low to R.attr.colorPriorityLow
                2 -> R.string.priority_label_medium to R.attr.colorPriorityMedium
                3 -> R.string.priority_label_high to R.attr.colorPriorityHigh
                else -> null to null
            }
            if (label != null && colorAttr != null) {
                text = context.getString(label)
                TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(resolve(context, colorAttr)))
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

        val list = card.findViewById<LinearLayout>(R.id.llSubtasksList)
        card.findViewById<View>(R.id.containerExportSubtasks).visibility =
            if (subtasks.isEmpty()) View.GONE else View.VISIBLE
        subtasks.take(MAX_SUBTASKS).forEach { list.addView(subtaskRow(context, it)) }

        card.findViewById<View>(R.id.llExportDueDate).visibility =
            if (task.dueDate != null) View.VISIBLE else View.GONE
        task.dueDate?.let { card.findViewById<TextView>(R.id.tvExportDueDate).text = DateUtils.formatSmartDate(it) }

        val tags = task.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        card.findViewById<TextView>(R.id.tvExportTags).apply {
            text = tags.joinToString(" ") { "#$it" }
            visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
        }

        val width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, CARD_WIDTH_DP, context.resources.displayMetrics).toInt()
        card.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        card.layout(0, 0, card.measuredWidth, card.measuredHeight)

        return Bitmap.createBitmap(card.measuredWidth, card.measuredHeight, Bitmap.Config.ARGB_8888).also {
            card.draw(Canvas(it))
        }
    }

    // Fila de subtarea con la misma casilla que usa la app y el título tachado si está hecha.
    private fun subtaskRow(context: Context, subtask: Subtask): View {
        val density = context.resources.displayMetrics.density
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
        }
        val icon = ImageView(context).apply {
            setImageResource(
                if (subtask.completed) R.drawable.ic_checkbox_square_checked else R.drawable.ic_checkbox_square_unchecked
            )
            imageTintList = ColorStateList.valueOf(resolve(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams((16 * density).toInt(), (16 * density).toInt())
        }
        val title = TextView(context).apply {
            text = subtask.title
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Polar_BodyMedium)
            setTextColor(resolve(context, com.google.android.material.R.attr.colorOnSurface))
            if (subtask.completed) {
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                alpha = 0.6f
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (10 * density).toInt()
            }
        }
        row.addView(icon)
        row.addView(title)
        return row
    }

    private fun resolve(context: Context, attr: Int): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(attr, value, true)
        return value.data
    }
}
