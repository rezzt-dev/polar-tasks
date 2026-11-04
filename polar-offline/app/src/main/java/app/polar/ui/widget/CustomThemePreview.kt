package app.polar.ui.widget

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import app.polar.R
import app.polar.databinding.ViewCustomThemePreviewBinding
import app.polar.databinding.ViewCustomThemePreviewTaskBinding

/**
 * Pinta la vista previa del editor de tema personalizado (`view_custom_theme_preview.xml`) con
 * unos [ThemePreviewColors] y anima el paso de unos colores a otros al cambiar la combinacion.
 * Los fondos se crean una sola vez y solo se recolorean, tambien en cada fotograma de la animacion.
 */
class CustomThemePreview(private val binding: ViewCustomThemePreviewBinding) {

    private val density = binding.root.resources.displayMetrics.density
    private val strokeWidth = density.toInt().coerceAtLeast(1)

    private val chipBackground = rounded(CHIP_RADIUS_DP)
    private val fabBackground = rounded(FAB_RADIUS_DP)
    private val tasks = listOf(binding.previewTaskHigh, binding.previewTaskMedium, binding.previewTaskLow)
    private val taskBackgrounds = tasks.map { rounded(TASK_RADIUS_DP) }
    private val taskChecks = tasks.map { GradientDrawable().apply { shape = GradientDrawable.OVAL } }

    /** Colores pintados ahora mismo (intermedios si hay una animacion en curso). */
    private var shown: ThemePreviewColors? = null
    private var animator: ValueAnimator? = null

    init {
        val context = binding.root.context
        binding.previewChip.background = chipBackground
        binding.previewFab.background = fabBackground
        val titles = listOf(
            R.string.custom_theme_preview_task_high,
            R.string.custom_theme_preview_task_medium,
            R.string.custom_theme_preview_task_low
        )
        tasks.forEachIndexed { index, task ->
            task.root.background = taskBackgrounds[index]
            task.previewTaskCheck.background = taskChecks[index]
            task.previewTaskTitle.text = context.getString(titles[index])
        }
    }

    /** Muestra [target]; con [animate] funde desde los colores actuales. */
    fun render(target: ThemePreviewColors, animate: Boolean) {
        animator?.cancel()
        val from = shown
        if (from == null || !animate || from == target) {
            paint(target)
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_MS
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { paint(from.blend(target, it.animatedFraction)) }
            start()
        }
    }

    /** Detiene la animacion en curso (al destruir la vista). */
    fun release() {
        animator?.cancel()
        animator = null
    }

    private fun paint(colors: ThemePreviewColors) {
        shown = colors
        binding.previewCard.setCardBackgroundColor(colors.background)
        binding.previewCard.strokeColor = colors.outlineVariant

        binding.previewHeading.setTextColor(colors.onSurface)
        chipBackground.setColor(colors.primaryContainer)
        binding.previewChip.setTextColor(colors.onPrimaryContainer)
        binding.previewProgressLabel.setTextColor(colors.onSurfaceVariant)
        binding.previewProgress.setIndicatorColor(colors.primary)
        binding.previewProgress.trackColor = colors.surfaceContainerHighest

        val priorities = listOf(colors.priorityHigh, colors.priorityMedium, colors.priorityLow)
        tasks.forEachIndexed { index, task ->
            paintTask(task, index, colors, priorities[index])
        }

        fabBackground.setColor(colors.primary)
        binding.previewFabIcon.imageTintList = ColorStateList.valueOf(colors.onPrimary)
    }

    private fun paintTask(task: ViewCustomThemePreviewTaskBinding, index: Int, colors: ThemePreviewColors, priority: Int) {
        taskBackgrounds[index].apply {
            setColor(colors.surface)
            setStroke(strokeWidth, colors.outlineVariant)
        }
        taskChecks[index].apply {
            setColor(Color.TRANSPARENT)
            setStroke((CHECK_STROKE_DP * density).toInt().coerceAtLeast(1), priority)
        }
        task.previewTaskStripe.backgroundTintList = ColorStateList.valueOf(priority)
        task.previewTaskTitle.setTextColor(colors.onSurface)
    }

    private fun rounded(radiusDp: Float) = GradientDrawable().apply { cornerRadius = radiusDp * density }

    private companion object {
        const val ANIMATION_MS = 220L
        const val CHIP_RADIUS_DP = 100f
        const val FAB_RADIUS_DP = 12f
        const val TASK_RADIUS_DP = 10f
        const val CHECK_STROKE_DP = 1.5f
    }
}
