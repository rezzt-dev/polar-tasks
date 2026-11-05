package app.polar.ui.activity

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.polar.R
import app.polar.databinding.ActivityCustomThemeBinding
import app.polar.databinding.ItemCustomThemePriorityBinding
import app.polar.ui.dialog.CustomColorSheet
import app.polar.ui.viewmodel.CustomThemeViewModel
import app.polar.ui.widget.ColorSwatch
import app.polar.ui.widget.CustomThemePreview
import app.polar.ui.widget.ThemePreviewColors
import app.polar.ui.widget.setRadioSemantics
import app.polar.util.CustomColors
import app.polar.util.CustomTheme
import app.polar.util.CustomThemePalette
import app.polar.util.CustomThemePalette.Priority
import app.polar.util.CustomThemePalette.Slot
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import com.google.android.material.R as MaterialR

/**
 * Editor del tema personalizado: el usuario elige fondo, color de acento y un color por prioridad
 * (de la paleta o a medida, con [CustomColorSheet]) y ve el resultado al momento en la vista
 * previa. Nada se guarda hasta pulsar "aplicar"; al volver con cambios sin aplicar se pide
 * confirmacion. Al aplicar guarda la combinacion, la deja como tema activo y se cierra con
 * `RESULT_OK`; ajustes detecta el cambio al volver (`ThemeManager.themeSignature()`) y recrea la
 * app con el tema nuevo.
 */
@AndroidEntryPoint
class CustomThemeActivity : BaseActivity() {

    private lateinit var binding: ActivityCustomThemeBinding
    private val viewModel: CustomThemeViewModel by viewModels()
    private lateinit var preview: CustomThemePreview

    private lateinit var swatchGroups: List<SwatchGroup>
    private lateinit var priorityRows: Map<Priority, ItemCustomThemePriorityBinding>

    private var renderedDraft: CustomTheme? = null
    private var discardDialog: androidx.appcompat.app.AlertDialog? = null

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = confirmDiscard()
    }

    /**
     * Muestras de un hueco: las claves de la paleta que contiene [group] ([color] da su tono en el
     * modo claro u oscuro del fondo) y, si lo tiene, la muestra [custom] para elegir un color a
     * medida. [rendered] evita repintar el grupo si su seleccion y el modo no han cambiado.
     */
    private class SwatchGroup(
        val slot: Slot,
        val group: ChipGroup,
        val keys: List<String>,
        val color: (key: String, dark: Boolean) -> Int,
        val alwaysOutlined: Boolean = false,
        var custom: ImageView? = null,
        var rendered: Pair<String, Boolean>? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomThemeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preview = CustomThemePreview(binding.preview)
        setupToolbar()
        setupSwatches(viewModel.uiState.value.customColorsAvailable)
        binding.btnApply.setOnClickListener { viewModel.apply() }
        onBackPressedDispatcher.addCallback(this, backCallback)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.applied.collect { applied ->
                        if (applied) {
                            setResult(RESULT_OK)
                            finish()
                        }
                    }
                }
                launch {
                    viewModel.errorMessage.filterNotNull().collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                            .setAnchorView(binding.btnApply)
                            .show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        discardDialog?.dismiss()
        if (::preview.isInitialized) preview.release()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------------------------
    // Construccion
    // ---------------------------------------------------------------------------------------------

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_reset_custom_theme) {
                resetWithUndo()
                true
            } else {
                false
            }
        }
    }

    private fun setupSwatches(customColors: Boolean) {
        val options = binding.options
        val (lightBackgrounds, darkBackgrounds) = CustomThemePalette.BACKGROUNDS.partition { !it.isDark }
        val backgroundColors = CustomThemePalette.BACKGROUNDS.associate {
            it.key to styleColor(it.styleRes, android.R.attr.colorBackground)
        }
        val backgroundColor: (String, Boolean) -> Int = { key, _ -> backgroundColors.getValue(key) }
        val accentColors = CustomThemePalette.ACCENTS.flatMap { accent ->
            listOf(false, true).map { dark ->
                (accent.key to dark) to styleColor(accent.overlay(dark), androidx.appcompat.R.attr.colorPrimary)
            }
        }.toMap()
        // El tono de una prioridad no depende de cual sea: basta con resolverlo en la alta.
        val priorityColors = CustomThemePalette.PRIORITY_COLORS.flatMap { color ->
            listOf(false, true).map { dark ->
                (color.key to dark) to styleColor(color.overlay(Priority.HIGH, dark), R.attr.colorPriorityHigh)
            }
        }.toMap()
        val priorityColor: (String, Boolean) -> Int = { key, dark -> priorityColors.getValue(key to dark) }

        priorityRows = mapOf(
            Priority.HIGH to options.priorityHigh,
            Priority.MEDIUM to options.priorityMedium,
            Priority.LOW to options.priorityLow
        )
        priorityRows.forEach { (priority, row) -> row.tvPriorityLabel.setText(priorityLabel(priority)) }

        val backgroundLight = SwatchGroup(Slot.BACKGROUND, options.groupBackgroundLight, lightBackgrounds.map { it.key }, backgroundColor, alwaysOutlined = true)
        val backgroundDark = SwatchGroup(Slot.BACKGROUND, options.groupBackgroundDark, darkBackgrounds.map { it.key }, backgroundColor, alwaysOutlined = true)
        val backgroundCustom = SwatchGroup(Slot.BACKGROUND, options.groupBackgroundCustom, emptyList(), backgroundColor)
        val accent = SwatchGroup(
            Slot.ACCENT, options.groupAccent, CustomThemePalette.ACCENTS.map { it.key },
            { key, dark -> accentColors.getValue(key to dark) }
        )
        val priorities = priorityRows.map { (priority, row) ->
            SwatchGroup(Slot.of(priority), row.groupPriorityColors, CustomThemePalette.PRIORITY_COLORS.map { it.key }, priorityColor)
        }
        swatchGroups = listOf(backgroundLight, backgroundDark, backgroundCustom, accent) + priorities

        addSwatches(backgroundLight, lightBackgrounds.map { it.labelRes })
        addSwatches(backgroundDark, darkBackgrounds.map { it.labelRes })
        addSwatches(accent, CustomThemePalette.ACCENTS.map { it.labelRes })
        priorities.forEach { addSwatches(it, CustomThemePalette.PRIORITY_COLORS.map { color -> color.labelRes }) }

        // Colores a medida: una muestra al final de cada paleta (y su propia fila para el fondo).
        if (customColors) {
            listOf(backgroundCustom, accent).plus(priorities).forEach(::addCustomSwatch)
        }
        options.tvBackgroundCustom.isVisible = customColors
        options.groupBackgroundCustom.isVisible = customColors
        options.tvCustomColorsUnsupported.isVisible = !customColors
    }

    private fun addSwatches(swatches: SwatchGroup, labels: List<Int>) {
        val size = resources.getDimensionPixelSize(R.dimen.custom_theme_swatch_size)
        swatches.keys.zip(labels).forEach { (key, labelRes) ->
            swatches.group.addView(View(this).apply {
                layoutParams = ChipGroup.LayoutParams(size, size)
                tag = key
                contentDescription = getString(labelRes)
                isClickable = true
                isFocusable = true
                setOnClickListener { viewModel.select(swatches.slot, key) }
            })
        }
    }

    private fun addCustomSwatch(swatches: SwatchGroup) {
        val size = resources.getDimensionPixelSize(R.dimen.custom_theme_swatch_size)
        val padding = resources.getDimensionPixelSize(R.dimen.custom_theme_swatch_icon_padding)
        swatches.custom = ImageView(this).apply {
            layoutParams = ChipGroup.LayoutParams(size, size)
            setImageResource(R.drawable.ic_colorize)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(padding, padding, padding, padding)
            isClickable = true
            isFocusable = true
            setOnClickListener { openCustomColor(swatches.slot) }
        }
        swatches.group.addView(swatches.custom)
    }

    // ---------------------------------------------------------------------------------------------
    // Estado
    // ---------------------------------------------------------------------------------------------

    private fun render(state: CustomThemeViewModel.UiState) {
        val draft = state.draft
        if (draft != renderedDraft) {
            val first = renderedDraft == null
            renderedDraft = draft
            val colors = ThemePreviewColors.forCustomTheme(this, draft)
            preview.render(colors, animate = !first)
            renderSelection(draft, colors)
        }

        binding.btnApply.isEnabled = state.canApply
        binding.btnApply.setText(if (state.isActive) R.string.custom_theme_save else R.string.custom_theme_apply)
        binding.toolbar.menu.findItem(R.id.action_reset_custom_theme)?.let { item ->
            item.isEnabled = state.canReset
            item.icon?.mutate()?.alpha = if (state.canReset) ALPHA_ENABLED else ALPHA_DISABLED
        }
        backCallback.isEnabled = state.hasChanges
    }

    private fun renderSelection(draft: CustomTheme, colors: ThemePreviewColors) {
        val dark = draft.isDark
        val ring = attrColor(MaterialR.attr.colorOnSurface)
        val gap = attrColor(android.R.attr.colorBackground)
        val outline = attrColor(MaterialR.attr.colorOutline)

        swatchGroups.forEach { swatches ->
            val selected = draft[swatches.slot]
            val signature = selected to dark
            if (swatches.rendered == signature) return@forEach
            swatches.rendered = signature

            for (i in 0 until swatches.group.childCount) {
                val view = swatches.group.getChildAt(i)
                val key = view.tag as? String ?: continue
                val color = swatches.color(key, dark)
                // Contorno fino si el color se confunde con el fondo de la pantalla.
                val needsBorder = swatches.alwaysOutlined || CustomColors.contrast(color, opaque(gap)) < MIN_SWATCH_CONTRAST
                view.background = ColorSwatch.drawable(this, color, key == selected, ring, gap, outline.takeIf { needsBorder })
                view.isSelected = key == selected
                view.setRadioSemantics(key == selected)
            }
            swatches.custom?.let { renderCustomSwatch(it, CustomThemePalette.customColor(selected), ring, gap, outline) }
        }

        val backgroundLabel = CustomThemePalette.label(this, Slot.BACKGROUND, draft.background)
        val accentLabel = CustomThemePalette.label(this, Slot.ACCENT, draft.accent)
        binding.options.tvBackgroundValue.text = backgroundLabel
        binding.options.tvAccentValue.text = accentLabel
        binding.preview.previewCard.contentDescription =
            getString(R.string.custom_theme_preview_description, backgroundLabel, accentLabel)

        priorityRows.forEach { (priority, row) ->
            val slot = Slot.of(priority)
            row.tvPriorityValue.text = CustomThemePalette.label(this, slot, draft[slot])
            row.viewPriorityDot.backgroundTintList = ColorStateList.valueOf(colors.colorOf(slot))
        }
    }

    /**
     * Muestra "a medida": con un color elegido se pinta de ese color, marcada y con la pipeta para
     * editarlo; si no, es un aro con todos los tonos que invita a elegir uno.
     */
    private fun renderCustomSwatch(view: ImageView, color: Int?, ring: Int, gap: Int, outline: Int) {
        if (color != null) {
            val needsBorder = CustomColors.contrast(color, opaque(gap)) < MIN_SWATCH_CONTRAST
            view.background = ColorSwatch.drawable(this, color, true, ring, gap, outline.takeIf { needsBorder })
            view.imageTintList = ColorStateList.valueOf(CustomColors.readableOn(color))
            view.contentDescription = getString(R.string.custom_color_selected, CustomColors.format(color))
        } else {
            view.background = ColorSwatch.custom(this, gap)
            view.imageTintList = ColorStateList.valueOf(attrColor(MaterialR.attr.colorOnSurfaceVariant))
            view.contentDescription = getString(R.string.custom_color_pick)
        }
        view.isSelected = color != null
        view.setRadioSemantics(color != null)
    }

    // ---------------------------------------------------------------------------------------------
    // Acciones
    // ---------------------------------------------------------------------------------------------

    private fun openCustomColor(slot: Slot) {
        if (supportFragmentManager.findFragmentByTag(CustomColorSheet.TAG) != null) return
        CustomColorSheet.newInstance(slot, viewModel.uiState.value.draft[slot])
            .show(supportFragmentManager, CustomColorSheet.TAG)
    }

    private fun resetWithUndo() {
        val previous = viewModel.reset()
        Snackbar.make(binding.root, R.string.custom_theme_reset_done, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { viewModel.restore(previous) }
            .setAnchorView(binding.btnApply)
            .show()
    }

    private fun confirmDiscard() {
        if (discardDialog?.isShowing == true) return
        discardDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.custom_theme_discard_title)
            .setMessage(R.string.custom_theme_discard_message)
            .setNegativeButton(R.string.custom_theme_keep_editing, null)
            .setPositiveButton(R.string.custom_theme_discard) { _, _ -> finish() }
            .show()
    }

    // ---------------------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------------------

    @StringRes
    private fun priorityLabel(priority: Priority): Int = when (priority) {
        Priority.HIGH -> R.string.priority_high
        Priority.MEDIUM -> R.string.priority_medium
        Priority.LOW -> R.string.priority_low
    }

    /** Lee un color directamente de un estilo (tema base u overlay) sin aplicarlo. */
    private fun styleColor(@StyleRes style: Int, @AttrRes attr: Int): Int {
        val values = obtainStyledAttributes(style, intArrayOf(attr))
        return try {
            values.getColor(0, Color.GRAY)
        } finally {
            values.recycle()
        }
    }

    private fun attrColor(@AttrRes attr: Int): Int = MaterialColors.getColor(this, attr, Color.GRAY)

    private fun opaque(color: Int): Int = color or Color.BLACK

    companion object {
        private const val ALPHA_ENABLED = 255
        private const val ALPHA_DISABLED = 97 // 38 %, como los iconos deshabilitados de material
        private const val MIN_SWATCH_CONTRAST = 1.6

        fun intent(context: Context): Intent = Intent(context, CustomThemeActivity::class.java)
    }
}
