package app.polar.ui.dialog

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Space
import androidx.annotation.AttrRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import app.polar.R
import app.polar.databinding.BottomSheetThemePickerBinding
import app.polar.databinding.ItemThemeOptionBinding
import app.polar.util.ThemeManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import androidx.appcompat.R as AppCompatR

/**
 * Selector de tema en una hoja inferior. Cada tema se muestra con una miniatura pintada con sus
 * propios colores y se agrupa en claros y oscuros; al tocar uno se cierra la hoja y se aplica.
 */
class ThemePickerDialog(
    private val context: Context,
    private val currentTheme: String,
    private val onThemeSelected: (String) -> Unit
) {

    /** Colores de un tema que necesita su miniatura. */
    private class PreviewColors(
        val background: Int,
        val surface: Int,
        val outline: Int,
        val primary: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val priorityHigh: Int,
        val priorityMedium: Int,
        val priorityLow: Int
    )

    fun show() {
        val dialog = BottomSheetDialog(context)
        val binding = BottomSheetThemePickerBinding.inflate(LayoutInflater.from(context))
        val (lightThemes, darkThemes) = ThemeManager.THEME_OPTIONS.partition { !it.isDark }

        val onPick: (ThemeManager.ThemeOption) -> Unit = { option ->
            dialog.dismiss()
            if (option.key != currentTheme) onThemeSelected(option.key)
        }
        fillGroup(binding.themeGroupLight, lightThemes, onPick)
        fillGroup(binding.themeGroupDark, darkThemes, onPick)

        dialog.setContentView(binding.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }

    private fun fillGroup(
        container: LinearLayout,
        options: List<ThemeManager.ThemeOption>,
        onPick: (ThemeManager.ThemeOption) -> Unit
    ) {
        val inflater = LayoutInflater.from(context)
        options.chunked(COLUMNS).forEach { rowOptions ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            rowOptions.forEach { option ->
                val item = ItemThemeOptionBinding.inflate(inflater, row, false)
                bindOption(item, option)
                item.root.setOnClickListener { onPick(option) }
                row.addView(item.root)
            }
            // Huecos para que la ultima fila mantenga el ancho de columna de las demas.
            repeat(COLUMNS - rowOptions.size) {
                row.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
            }
            container.addView(row)
        }
    }

    private fun bindOption(item: ItemThemeOptionBinding, option: ThemeManager.ThemeOption) {
        val colors = resolveColors(option.previewStyleRes)
        val density = context.resources.displayMetrics.density
        val selected = option.key == currentTheme
        val label = context.getString(option.labelRes)

        item.tvThemeName.text = label
        item.themePreviewCanvas.setBackgroundColor(colors.background)
        item.themePreviewTask.background = rounded(colors.surface, 6f * density, colors.outline, density)
        item.themePreviewCheck.background = oval(android.graphics.Color.TRANSPARENT, colors.primary, 1.5f * density)
        item.themePreviewTitle.background = rounded(colors.onSurface, 2f * density)
        item.themePreviewSubtitle.background = rounded(colors.onSurfaceVariant, 2f * density)
        item.themePreviewDotHigh.background = oval(colors.priorityHigh)
        item.themePreviewDotMedium.background = oval(colors.priorityMedium)
        item.themePreviewDotLow.background = oval(colors.priorityLow)
        item.themePreviewFab.background = rounded(colors.primary, 6f * density)

        // El estado seleccionado usa los colores del tema activo, no los de la miniatura.
        val activePrimary = resolve(context, AppCompatR.attr.colorPrimary)
        if (selected) {
            item.cardThemePreview.strokeColor = activePrimary
            item.cardThemePreview.strokeWidth = (2 * density).toInt()
            item.ivThemeSelected.visibility = View.VISIBLE
            item.ivThemeSelected.background = oval(activePrimary)
            item.ivThemeSelected.setColorFilter(resolve(context, MaterialR.attr.colorOnPrimary))
            item.tvThemeName.setTextColor(resolve(context, MaterialR.attr.colorOnSurface))
        }

        item.root.contentDescription = context.getString(R.string.theme_option_description, label)
        ViewCompat.setAccessibilityDelegate(item.root, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.RadioButton::class.java.name
                info.isCheckable = true
                info.isChecked = selected
            }
        })
    }

    private fun resolveColors(styleRes: Int): PreviewColors {
        val themed = ContextThemeWrapper(context, styleRes)
        return PreviewColors(
            background = resolve(themed, android.R.attr.colorBackground),
            surface = resolve(themed, MaterialR.attr.colorSurface),
            outline = resolve(themed, MaterialR.attr.colorOutlineVariant),
            primary = resolve(themed, AppCompatR.attr.colorPrimary),
            onSurface = resolve(themed, MaterialR.attr.colorOnSurface),
            onSurfaceVariant = resolve(themed, MaterialR.attr.colorOnSurfaceVariant),
            priorityHigh = resolve(themed, R.attr.colorPriorityHigh),
            priorityMedium = resolve(themed, R.attr.colorPriorityMedium),
            priorityLow = resolve(themed, R.attr.colorPriorityLow)
        )
    }

    private fun resolve(themed: Context, @AttrRes attr: Int): Int =
        MaterialColors.getColor(themed, attr, android.graphics.Color.GRAY)

    private fun rounded(color: Int, radius: Float, strokeColor: Int? = null, density: Float = 0f) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            if (strokeColor != null) setStroke(density.toInt().coerceAtLeast(1), strokeColor)
        }

    private fun oval(color: Int, strokeColor: Int? = null, strokeWidth: Float = 0f) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (strokeColor != null) setStroke(strokeWidth.toInt().coerceAtLeast(1), strokeColor)
        }

    companion object {
        private const val COLUMNS = 3

        /** Muestra de color del tema para la fila de ajustes: su fondo con un anillo del acento. */
        fun swatch(context: Context, option: ThemeManager.ThemeOption): GradientDrawable {
            val themed = ContextThemeWrapper(context, option.previewStyleRes)
            val density = context.resources.displayMetrics.density
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(MaterialColors.getColor(themed, android.R.attr.colorBackground, android.graphics.Color.GRAY))
                setStroke(
                    (3 * density).toInt(),
                    MaterialColors.getColor(themed, AppCompatR.attr.colorPrimary, android.graphics.Color.GRAY)
                )
            }
        }
    }
}
