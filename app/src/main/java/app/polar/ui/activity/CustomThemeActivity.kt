package app.polar.ui.activity

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.SeekBar
import androidx.recyclerview.widget.GridLayoutManager
import app.polar.R
import app.polar.databinding.ActivityCustomThemeBinding
import app.polar.databinding.DialogColorPickerBinding
import app.polar.ui.adapter.GeneratedColorAdapter
import app.polar.util.CustomThemeColors
import app.polar.util.CustomThemeGenerator
import app.polar.util.ThemeManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * Pantalla para crear un tema personalizado a partir de un color de fondo base.
 * El usuario puede alternar entre modo claro y oscuro; todos los acentos se
 * derivan del matiz del fondo para garantizar una paleta coherente y profesional.
 */
class CustomThemeActivity : BaseActivity() {

  private lateinit var binding: ActivityCustomThemeBinding
  private lateinit var activityThemeManager: ThemeManager
  private val adapter = GeneratedColorAdapter()

  private var isForegroundManuallyEdited = false
  private var lastAutoForeground: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityCustomThemeBinding.inflate(layoutInflater)
    setContentView(binding.root)

    activityThemeManager = ThemeManager(this)

    setupToolbar()
    setupRecyclerView()
    setupModeSwitch()
    setupInputs()
    setupPickers()
    setupButtons()

    // Cargar valores guardados o los por defecto
    val background = activityThemeManager.loadCustomBackground()
    val foreground = activityThemeManager.loadCustomForeground()
    val isDarkMode = activityThemeManager.loadCustomIsDarkMode()

    binding.etBackgroundHex.setText(background)
    binding.etForegroundHex.setText(foreground)
    binding.switchDarkMode.isChecked = isDarkMode
    binding.switchDarkMode.text = getString(
      if (isDarkMode) R.string.custom_theme_dark_mode else R.string.custom_theme_light_mode
    )

    // Si el foreground guardado no coincide con el auto-calculado, asumimos que
    // el usuario lo personalizó previamente.
    val bgColor = CustomThemeGenerator.parseHex(background) ?: Color.parseColor("#1A1A1A")
    lastAutoForeground = CustomThemeGenerator.toHex(CustomThemeGenerator.autoForeground(bgColor))
    isForegroundManuallyEdited = foreground.lowercase() != lastAutoForeground?.lowercase()

    refreshPreview()
  }

  private fun setupToolbar() {
    setSupportActionBar(binding.toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.setDisplayShowHomeEnabled(true)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return if (item.itemId == android.R.id.home) {
      finish()
      true
    } else {
      super.onOptionsItemSelected(item)
    }
  }

  private fun setupRecyclerView() {
    binding.rvGeneratedColors.layoutManager = GridLayoutManager(this, 2)
    binding.rvGeneratedColors.adapter = adapter
  }

  private fun setupModeSwitch() {
    binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
      binding.switchDarkMode.text = getString(
        if (isChecked) R.string.custom_theme_dark_mode else R.string.custom_theme_light_mode
      )
      updateAutoForeground()
      refreshPreview()
    }
  }

  private fun setupInputs() {
    binding.etBackgroundHex.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
      override fun afterTextChanged(s: Editable?) {
        updateAutoForeground()
        refreshPreview()
      }
    })

    binding.etForegroundHex.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
      override fun afterTextChanged(s: Editable?) {
        val current = s?.toString()?.lowercase() ?: ""
        if (current != lastAutoForeground?.lowercase()) {
          isForegroundManuallyEdited = true
        }
        refreshPreview()
      }
    })
  }

  private fun updateAutoForeground() {
    if (isForegroundManuallyEdited) return
    val backgroundHex = normalizeHex(binding.etBackgroundHex.text?.toString())
    val bgColor = CustomThemeGenerator.parseHex(backgroundHex) ?: Color.parseColor("#1A1A1A")
    val autoFg = CustomThemeGenerator.toHex(CustomThemeGenerator.autoForeground(bgColor))
    lastAutoForeground = autoFg
    binding.etForegroundHex.setText(autoFg)
  }

  private fun setupPickers() {
    binding.tilBackgroundHex.setEndIconOnClickListener {
      showColorPicker(binding.etBackgroundHex.text?.toString() ?: "#000000") { color ->
        binding.etBackgroundHex.setText(CustomThemeGenerator.toHex(color))
      }
    }
    binding.tilForegroundHex.setEndIconOnClickListener {
      showColorPicker(binding.etForegroundHex.text?.toString() ?: "#FFFFFF") { color ->
        binding.etForegroundHex.setText(CustomThemeGenerator.toHex(color))
      }
    }

    binding.previewBackground.setOnClickListener {
      showColorPicker(binding.etBackgroundHex.text?.toString() ?: "#000000") { color ->
        binding.etBackgroundHex.setText(CustomThemeGenerator.toHex(color))
      }
    }
    binding.previewForeground.setOnClickListener {
      showColorPicker(binding.etForegroundHex.text?.toString() ?: "#FFFFFF") { color ->
        binding.etForegroundHex.setText(CustomThemeGenerator.toHex(color))
      }
    }
  }

  private fun setupButtons() {
    binding.btnApplyTheme.setOnClickListener {
      val backgroundHex = normalizeHex(binding.etBackgroundHex.text?.toString())
      val foregroundHex = normalizeHex(binding.etForegroundHex.text?.toString())
      val isDarkMode = binding.switchDarkMode.isChecked

      if (CustomThemeGenerator.parseHex(backgroundHex) == null ||
        CustomThemeGenerator.parseHex(foregroundHex) == null
      ) {
        Snackbar.make(binding.root, R.string.custom_theme_invalid_color, Snackbar.LENGTH_SHORT)
          .show()
        return@setOnClickListener
      }

      activityThemeManager.saveTheme(ThemeManager.THEME_CUSTOM)
      activityThemeManager.saveCustomThemeColors(backgroundHex, foregroundHex, isDarkMode)
      activityThemeManager.applyTheme(ThemeManager.THEME_CUSTOM)
      Snackbar.make(binding.root, R.string.custom_theme_applied, Snackbar.LENGTH_SHORT).show()
      recreate()
    }

    binding.btnResetTheme.setOnClickListener {
      val defaultBg = CustomThemeColors.DEFAULT_BACKGROUND
      val defaultFg = CustomThemeColors.DEFAULT_FOREGROUND
      binding.etBackgroundHex.setText(defaultBg)
      binding.etForegroundHex.setText(defaultFg)
      binding.switchDarkMode.isChecked = true
      binding.switchDarkMode.text = getString(R.string.custom_theme_dark_mode)
      isForegroundManuallyEdited = false
      lastAutoForeground = defaultFg
      refreshPreview()
    }
  }

  private fun refreshPreview() {
    val backgroundHex = normalizeHex(binding.etBackgroundHex.text?.toString())
    val foregroundHex = normalizeHex(binding.etForegroundHex.text?.toString())
    val isDarkMode = binding.switchDarkMode.isChecked

    val bgColor = CustomThemeGenerator.parseHex(backgroundHex) ?: Color.parseColor("#1A1A1A")
    val fgColor = CustomThemeGenerator.parseHex(foregroundHex) ?: Color.parseColor("#FFFFFF")

    binding.previewBackground.backgroundTintList = ColorStateList.valueOf(bgColor)
    binding.previewForeground.backgroundTintList = ColorStateList.valueOf(fgColor)

    val colors = CustomThemeGenerator.generate(backgroundHex, foregroundHex, isDarkMode)

    // Actualizar la tarjeta de preview del tema
    binding.cardThemePreview.setCardBackgroundColor(colors.surface)
    binding.cardThemePreview.strokeColor = colors.outline

    // Lista de colores generados
    val items = listOf(
      GeneratedColorAdapter.GeneratedColorItem(getString(R.string.color_background), colors.background, CustomThemeGenerator.toHex(colors.background)),
      GeneratedColorAdapter.GeneratedColorItem(getString(R.string.color_surface), colors.surface, CustomThemeGenerator.toHex(colors.surface)),
      GeneratedColorAdapter.GeneratedColorItem(getString(R.string.color_surface_variant), colors.surfaceVariant, CustomThemeGenerator.toHex(colors.surfaceVariant)),
      GeneratedColorAdapter.GeneratedColorItem(getString(R.string.color_surface_container), colors.surfaceContainer, CustomThemeGenerator.toHex(colors.surfaceContainer)),
      GeneratedColorAdapter.GeneratedColorItem(getString(R.string.color_primary), colors.primary, CustomThemeGenerator.toHex(colors.primary)),
      GeneratedColorAdapter.GeneratedColorItem(getString(R.string.color_primary_container), colors.primaryContainer, CustomThemeGenerator.toHex(colors.primaryContainer)),
      GeneratedColorAdapter.GeneratedColorItem(getString(R.string.color_secondary), colors.secondary, CustomThemeGenerator.toHex(colors.secondary)),
      GeneratedColorAdapter.GeneratedColorItem(getString(R.string.color_secondary_container), colors.secondaryContainer, CustomThemeGenerator.toHex(colors.secondaryContainer)),
      GeneratedColorAdapter.GeneratedColorItem(getString(R.string.color_foreground), colors.foreground, CustomThemeGenerator.toHex(colors.foreground)),
      GeneratedColorAdapter.GeneratedColorItem(getString(R.string.color_outline), colors.outline, CustomThemeGenerator.toHex(colors.outline)),
      GeneratedColorAdapter.GeneratedColorItem(getString(R.string.color_error), colors.error, CustomThemeGenerator.toHex(colors.error)),
      GeneratedColorAdapter.GeneratedColorItem(getString(R.string.color_success), colors.success, CustomThemeGenerator.toHex(colors.success))
    )
    adapter.submitList(items)

    // Force the screen's own action buttons and the preview widgets to use the
    // generated palette, so the editor shows the real custom theme instead of
    // falling back to the previously active theme.
    binding.btnApplyTheme.backgroundTintList = ColorStateList.valueOf(colors.primary)
    binding.btnApplyTheme.setTextColor(colors.onPrimary)
    binding.btnResetTheme.backgroundTintList = ColorStateList.valueOf(colors.secondaryContainer)
    binding.btnResetTheme.setTextColor(colors.onSecondaryContainer)

    binding.previewCheckbox.buttonTintList = ColorStateList.valueOf(colors.primary)
    binding.previewButton.backgroundTintList = ColorStateList.valueOf(colors.primary)
    binding.previewButton.setTextColor(colors.onPrimary)
    binding.previewFab.backgroundTintList = ColorStateList.valueOf(colors.secondaryContainer)
    binding.previewFab.imageTintList = ColorStateList.valueOf(colors.onSecondaryContainer)
    binding.previewChip.chipBackgroundColor = ColorStateList.valueOf(colors.primaryContainer)
    binding.previewChip.setTextColor(colors.onPrimaryContainer)
    binding.previewChip.chipIconTint = ColorStateList.valueOf(colors.onPrimaryContainer)
  }

  private fun showColorPicker(currentHex: String, onColorSelected: (Int) -> Unit) {
    val currentColor = CustomThemeGenerator.parseHex(currentHex) ?: Color.BLACK

    val dialogBinding = DialogColorPickerBinding.inflate(LayoutInflater.from(this))
    val hsl = FloatArray(3)
    CustomThemeGenerator.colorToHsl(currentColor, hsl)

    dialogBinding.seekBarHue.max = 360
    dialogBinding.seekBarSaturation.max = 100
    dialogBinding.seekBarLightness.max = 100

    dialogBinding.seekBarHue.progress = hsl[0].toInt()
    dialogBinding.seekBarSaturation.progress = (hsl[1] * 100).toInt()
    dialogBinding.seekBarLightness.progress = (hsl[2] * 100).toInt()

    val updatePreview = {
      val h = dialogBinding.seekBarHue.progress.toFloat()
      val s = dialogBinding.seekBarSaturation.progress / 100f
      val l = dialogBinding.seekBarLightness.progress / 100f
      val color = CustomThemeGenerator.hslToColor(floatArrayOf(h, s, l))
      dialogBinding.previewPickerColor.setBackgroundColor(color)
      dialogBinding.tvPickerHex.text = CustomThemeGenerator.toHex(color)
    }

    val listener = object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        updatePreview()
      }
      override fun onStartTrackingTouch(seekBar: SeekBar?) {}
      override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    dialogBinding.seekBarHue.setOnSeekBarChangeListener(listener)
    dialogBinding.seekBarSaturation.setOnSeekBarChangeListener(listener)
    dialogBinding.seekBarLightness.setOnSeekBarChangeListener(listener)
    updatePreview()

    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.custom_theme_pick_color)
      .setView(dialogBinding.root)
      .setPositiveButton(R.string.save) { _, _ ->
        val h = dialogBinding.seekBarHue.progress.toFloat()
        val s = dialogBinding.seekBarSaturation.progress / 100f
        val l = dialogBinding.seekBarLightness.progress / 100f
        onColorSelected(CustomThemeGenerator.hslToColor(floatArrayOf(h, s, l)))
      }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }

  private fun normalizeHex(hex: String?): String {
    if (hex.isNullOrBlank()) return "#000000"
    val cleaned = hex.trim()
    return if (cleaned.startsWith("#")) cleaned else "#$cleaned"
  }
}
