package app.polar.ui.activity

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.polar.R
import app.polar.databinding.ActivityCustomThemeBinding
import app.polar.databinding.ItemCustomThemePriorityBinding
import app.polar.ui.viewmodel.CustomThemeViewModel
import app.polar.ui.widget.ColorSwatch
import app.polar.ui.widget.CustomThemePreview
import app.polar.ui.widget.ThemePreviewColors
import app.polar.ui.widget.setRadioSemantics
import app.polar.util.CustomTheme
import app.polar.util.CustomThemePalette
import app.polar.util.CustomThemePalette.Priority
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
 * y ve el resultado al momento en la vista previa. Nada se guarda hasta pulsar "aplicar"; al
 * volver con cambios sin aplicar se pide confirmacion. Al aplicar guarda la combinacion, la deja
 * como tema activo y se cierra con `RESULT_OK`; ajustes detecta el cambio al volver
 * (`ThemeManager.themeSignature()`) y recrea la app con el tema nuevo.
 */
@AndroidEntryPoint
class CustomThemeActivity : BaseActivity() {

    private lateinit var binding: ActivityCustomThemeBinding
    private val viewModel: CustomThemeViewModel by viewModels()
    private lateinit var preview: CustomThemePreview

    /** Grupos de muestras y la clave elegida en cada uno para la combinacion en edicion. */
    private lateinit var swatchGroups: List<SwatchGroup>
    private lateinit var priorityRows: Map<Priority, ItemCustomThemePriorityBinding>
    private lateinit var priorityColor: (key: String, dark: Boolean) -> Int

    private var renderedDraft: CustomTheme? = null
    private var discardDialog: androidx.appcompat.app.AlertDialog? = null

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = confirmDiscard()
    }

    /**
     * Muestras de una paleta. [color] da el color de cada opcion en el modo (claro u oscuro) del
     * fondo elegido y [selectedKey] la opcion marcada en la combinacion.
     */
    private class SwatchGroup(
        val group: ChipGroup,
        val keys: List<String>,
        val color: (key: String, dark: Boolean) -> Int,
        val selectedKey: (CustomTheme) -> String,
        val alwaysOutlined: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomThemeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preview = CustomThemePreview(binding.preview)
        setupToolbar()
        setupSwatches()
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

    private fun setupSwatches() {
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
        priorityColor = { key, dark -> priorityColors.getValue(key to dark) }

        priorityRows = mapOf(
            Priority.HIGH to binding.options.priorityHigh,
            Priority.MEDIUM to binding.options.priorityMedium,
            Priority.LOW to binding.options.priorityLow
        )
        priorityRows.forEach { (priority, row) -> row.tvPriorityLabel.setText(priorityLabel(priority)) }

        swatchGroups = listOf(
            SwatchGroup(binding.options.groupBackgroundLight, lightBackgrounds.map { it.key }, backgroundColor, { it.background }, alwaysOutlined = true),
            SwatchGroup(binding.options.groupBackgroundDark, darkBackgrounds.map { it.key }, backgroundColor, { it.background }, alwaysOutlined = true),
            SwatchGroup(binding.options.groupAccent, CustomThemePalette.ACCENTS.map { it.key }, { key, dark -> accentColors.getValue(key to dark) }, { it.accent })
        ) + priorityRows.map { (priority, row) ->
            SwatchGroup(
                row.groupPriorityColors,
                CustomThemePalette.PRIORITY_COLORS.map { it.key },
                priorityColor,
                { it.priorityColor(priority) }
            )
        }

        addSwatches(binding.options.groupBackgroundLight, lightBackgrounds.map { it.key to it.labelRes }, viewModel::selectBackground)
        addSwatches(binding.options.groupBackgroundDark, darkBackgrounds.map { it.key to it.labelRes }, viewModel::selectBackground)
        addSwatches(binding.options.groupAccent, CustomThemePalette.ACCENTS.map { it.key to it.labelRes }, viewModel::selectAccent)
        priorityRows.forEach { (priority, row) ->
            addSwatches(row.groupPriorityColors, CustomThemePalette.PRIORITY_COLORS.map { it.key to it.labelRes }) { key ->
                viewModel.selectPriorityColor(priority, key)
            }
        }
    }

    private fun addSwatches(group: ChipGroup, options: List<Pair<String, Int>>, onSelect: (String) -> Unit) {
        val size = resources.getDimensionPixelSize(R.dimen.custom_theme_swatch_size)
        options.forEach { (key, labelRes) ->
            group.addView(View(this).apply {
                layoutParams = ChipGroup.LayoutParams(size, size)
                tag = key
                contentDescription = getString(labelRes)
                isClickable = true
                isFocusable = true
                setOnClickListener { onSelect(key) }
            })
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Estado
    // ---------------------------------------------------------------------------------------------

    private fun render(state: CustomThemeViewModel.UiState) {
        val draft = state.draft
        if (draft != renderedDraft) {
            val first = renderedDraft == null
            renderedDraft = draft
            preview.render(ThemePreviewColors.resolve(CustomThemePalette.themedContext(this, draft)), animate = !first)
            renderSelection(draft)
        }

        binding.btnApply.isEnabled = state.canApply
        binding.btnApply.setText(if (state.isActive) R.string.custom_theme_save else R.string.custom_theme_apply)
        binding.toolbar.menu.findItem(R.id.action_reset_custom_theme)?.let { item ->
            item.isEnabled = state.canReset
            item.icon?.mutate()?.alpha = if (state.canReset) ALPHA_ENABLED else ALPHA_DISABLED
        }
        backCallback.isEnabled = state.hasChanges
    }

    private fun renderSelection(draft: CustomTheme) {
        val dark = draft.isDark
        val ring = attrColor(MaterialR.attr.colorOnSurface)
        val gap = attrColor(android.R.attr.colorBackground)
        val outline = attrColor(MaterialR.attr.colorOutline)

        swatchGroups.forEach { swatches ->
            val selected = swatches.selectedKey(draft)
            for (i in 0 until swatches.group.childCount) {
                val view = swatches.group.getChildAt(i)
                val key = view.tag as? String ?: continue
                val color = swatches.color(key, dark)
                // Contorno fino si el color se confunde con el fondo de la pantalla.
                val needsBorder = swatches.alwaysOutlined || ColorUtils.calculateContrast(color, opaque(gap)) < MIN_SWATCH_CONTRAST
                view.background = ColorSwatch.drawable(this, color, key == selected, ring, gap, outline.takeIf { needsBorder })
                view.isSelected = key == selected
                view.setRadioSemantics(key == selected)
            }
        }

        val backgroundLabel = getString(CustomThemePalette.background(draft.background).labelRes)
        val accentLabel = getString(CustomThemePalette.accent(draft.accent).labelRes)
        binding.options.tvBackgroundValue.text = backgroundLabel
        binding.options.tvAccentValue.text = accentLabel
        binding.preview.previewCard.contentDescription =
            getString(R.string.custom_theme_preview_description, backgroundLabel, accentLabel)

        priorityRows.forEach { (priority, row) ->
            val key = draft.priorityColor(priority)
            row.tvPriorityValue.text = getString(CustomThemePalette.priorityColor(key).labelRes)
            row.viewPriorityDot.backgroundTintList = ColorStateList.valueOf(priorityColor(key, dark))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Acciones
    // ---------------------------------------------------------------------------------------------

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

    private fun opaque(color: Int): Int = ColorUtils.setAlphaComponent(color, 255)

    companion object {
        private const val ALPHA_ENABLED = 255
        private const val ALPHA_DISABLED = 97 // 38 %, como los iconos deshabilitados de material
        private const val MIN_SWATCH_CONTRAST = 1.6

        fun intent(context: Context): Intent = Intent(context, CustomThemeActivity::class.java)
    }
}
