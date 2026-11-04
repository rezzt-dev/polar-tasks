package app.polar.ui.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Space
import androidx.annotation.AttrRes
import app.polar.R
import app.polar.databinding.BottomSheetThemePickerBinding
import app.polar.databinding.ItemThemeCustomBinding
import app.polar.databinding.ItemThemeOptionBinding
import app.polar.databinding.ViewThemePreviewBinding
import app.polar.ui.widget.ThemePreviewColors
import app.polar.ui.widget.setRadioSemantics
import app.polar.util.CustomTheme
import app.polar.util.CustomThemePalette
import app.polar.util.ThemeManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import androidx.appcompat.R as AppCompatR

/**
 * Selector de tema en una hoja inferior. Cada tema se muestra con una miniatura pintada con sus
 * propios colores y se agrupa en personalizado, claros y oscuros; al tocar uno se cierra la hoja y
 * se aplica. El personalizado se crea o se edita en `CustomThemeActivity` ([onEditCustomTheme]).
 *
 * @param customTheme combinacion guardada del tema personalizado; `null` si aun no se ha creado.
 */
class ThemePickerDialog(
    private val context: Context,
    private val currentTheme: String,
    private val customTheme: CustomTheme?,
    private val onThemeSelected: (String) -> Unit,
    private val onEditCustomTheme: () -> Unit
) {

    fun show() {
        val dialog = BottomSheetDialog(context)
        val binding = BottomSheetThemePickerBinding.inflate(LayoutInflater.from(context))
        val (lightThemes, darkThemes) = ThemeManager.THEME_OPTIONS.partition { !it.isDark }

        val onPick: (String) -> Unit = { key ->
            dialog.dismiss()
            if (key != currentTheme) onThemeSelected(key)
        }
        val onEdit = {
            dialog.dismiss()
            onEditCustomTheme()
        }
        bindCustom(binding.themeCustom, onPick, onEdit)
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
        onPick: (String) -> Unit
    ) {
        val inflater = LayoutInflater.from(context)
        options.chunked(COLUMNS).forEach { rowOptions ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            rowOptions.forEach { option ->
                val item = ItemThemeOptionBinding.inflate(inflater, row, false)
                bindOption(item, option)
                item.root.setOnClickListener { onPick(option.key) }
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
        val selected = option.key == currentTheme
        val label = context.getString(option.labelRes)

        item.tvThemeName.text = label
        paintPreview(item.preview, ThemeManager.previewContext(context, option.key, null), selected)
        if (selected) item.tvThemeName.setTextColor(resolve(MaterialR.attr.colorOnSurface))

        item.root.contentDescription = context.getString(R.string.theme_option_description, label)
        item.root.setRadioSemantics(selected)
    }

    /**
     * El personalizado ya creado se elige como cualquier otro tema; si es el activo, tocarlo abre
     * el editor. Si aun no existe, toda la fila lleva al editor para crearlo.
     */
    private fun bindCustom(item: ItemThemeCustomBinding, onPick: (String) -> Unit, onEdit: () -> Unit) {
        val saved = customTheme
        val selected = saved != null && currentTheme == ThemeManager.THEME_CUSTOM

        item.btnCustomEdit.setOnClickListener { onEdit() }
        if (saved == null) {
            item.preview.root.visibility = View.GONE
            item.cardCustomPlaceholder.visibility = View.VISIBLE
            item.tvCustomSummary.setText(R.string.theme_custom_create_desc)
            item.btnCustomEdit.setText(R.string.theme_custom_create)
            item.btnCustomEdit.setIconResource(R.drawable.ic_add)
            item.root.setOnClickListener { onEdit() }
            item.root.contentDescription = context.getString(R.string.theme_custom_create_description)
            return
        }

        val summary = summary(saved)
        paintPreview(item.preview, ThemeManager.previewContext(context, ThemeManager.THEME_CUSTOM, saved), selected)
        item.tvCustomSummary.text = summary
        item.btnCustomEdit.setText(R.string.edit)
        item.btnCustomEdit.contentDescription = context.getString(R.string.theme_custom_edit_description)
        item.root.setOnClickListener { if (selected) onEdit() else onPick(ThemeManager.THEME_CUSTOM) }
        item.root.contentDescription = context.getString(
            R.string.theme_option_description,
            "${context.getString(R.string.theme_custom)}, $summary"
        )
        item.root.setRadioSemantics(selected)
    }

    /** Pinta la miniatura con los colores del tema; la seleccion usa los colores del tema activo. */
    private fun paintPreview(preview: ViewThemePreviewBinding, themed: Context, selected: Boolean) {
        val colors = ThemePreviewColors.resolve(themed)
        val density = context.resources.displayMetrics.density

        preview.themePreviewCanvas.setBackgroundColor(colors.background)
        preview.themePreviewTask.background = rounded(colors.surface, 6f * density, colors.outlineVariant, density)
        preview.themePreviewCheck.background = oval(Color.TRANSPARENT, colors.primary, 1.5f * density)
        preview.themePreviewTitle.background = rounded(colors.onSurface, 2f * density)
        preview.themePreviewSubtitle.background = rounded(colors.onSurfaceVariant, 2f * density)
        preview.themePreviewDotHigh.background = oval(colors.priorityHigh)
        preview.themePreviewDotMedium.background = oval(colors.priorityMedium)
        preview.themePreviewDotLow.background = oval(colors.priorityLow)
        preview.themePreviewFab.background = rounded(colors.primary, 6f * density)

        if (selected) {
            val activePrimary = resolve(AppCompatR.attr.colorPrimary)
            preview.cardThemePreview.strokeColor = activePrimary
            preview.cardThemePreview.strokeWidth = (2 * density).toInt()
            preview.ivThemeSelected.visibility = View.VISIBLE
            preview.ivThemeSelected.background = oval(activePrimary)
            preview.ivThemeSelected.setColorFilter(resolve(MaterialR.attr.colorOnPrimary))
        }
    }

    private fun summary(theme: CustomTheme): String = context.getString(
        R.string.theme_custom_summary,
        context.getString(CustomThemePalette.background(theme.background).labelRes),
        context.getString(CustomThemePalette.accent(theme.accent).labelRes)
    )

    private fun resolve(@AttrRes attr: Int): Int = MaterialColors.getColor(context, attr, Color.GRAY)

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

        /** Muestra de color de un tema para la fila de ajustes: su fondo con un anillo del acento. */
        fun swatch(themed: Context): GradientDrawable {
            val colors = ThemePreviewColors.resolve(themed)
            val density = themed.resources.displayMetrics.density
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colors.background)
                setStroke((3 * density).toInt(), colors.primary)
            }
        }
    }
}
