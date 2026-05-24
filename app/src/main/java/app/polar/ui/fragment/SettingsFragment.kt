package app.polar.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.polar.R
import app.polar.databinding.FragmentSettingsBinding
import app.polar.util.ThemeManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
  private var _binding: FragmentSettingsBinding? = null
  private val binding get() = _binding!!
  private lateinit var themeManager: ThemeManager

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentSettingsBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    themeManager = ThemeManager(requireContext())

    setupThemeSelection()
    setupFontSelection()
    setupCheckboxStyle()
    setupFontScale()
    setupNotificationSettings()
    setupBackupSettings()
    setupLanguageSelection()
  }

  private fun setupThemeSelection() {
    val themeEntries = linkedMapOf(
      getString(R.string.theme_light) to ThemeManager.THEME_LIGHT,
      getString(R.string.theme_dark) to ThemeManager.THEME_DARK,
      getString(R.string.theme_multicolor_light) to ThemeManager.THEME_MULTICOLOR_LIGHT,
      getString(R.string.theme_multicolor_dark) to ThemeManager.THEME_MULTICOLOR_DARK,
      getString(R.string.theme_pastel) to ThemeManager.THEME_PASTEL,
      getString(R.string.theme_neon) to ThemeManager.THEME_NEON
    )
    val themeLabels = themeEntries.keys.toTypedArray()
    val themeValues = themeEntries.values.toList()

    fun updateLabel() {
        val currentTheme = themeManager.loadTheme()
        val currentLabel = themeEntries.entries.find { it.value == currentTheme }?.key ?: themeLabels[1]
        binding.tvThemeValue.text = currentLabel
    }
    updateLabel()

    binding.btnThemeSettings.setOnClickListener {
      val currentTheme = themeManager.loadTheme()
      val checkedItem = themeValues.indexOf(currentTheme).takeIf { it >= 0 } ?: 1

      MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.dialog_select_theme))
        .setSingleChoiceItems(themeLabels, checkedItem) { dialog, which ->
            val selectedTheme = themeValues[which]
            if (currentTheme != selectedTheme) {
                themeManager.saveTheme(selectedTheme)
                requireActivity().recreate()
            }
            dialog.dismiss()
        }
        .setNegativeButton(getString(R.string.cancel), null)
        .show()
    }
  }

  private fun setupFontSelection() {
    val fontEntries = linkedMapOf(
      getString(R.string.font_poppins) to ThemeManager.FONT_POPPINS,
      getString(R.string.font_comfortaa) to ThemeManager.FONT_COMFORTAA,
      getString(R.string.font_figtree) to ThemeManager.FONT_FIGTREE,
      getString(R.string.font_jetbrains_mono) to ThemeManager.FONT_JETBRAINS_MONO,
      getString(R.string.font_arial) to ThemeManager.FONT_ARIAL,
      getString(R.string.font_system) to ThemeManager.FONT_SYSTEM
    )
    val fontLabels = fontEntries.keys.toTypedArray()
    val fontValues = fontEntries.values.toList()

    fun updateLabel() {
        val currentFont = themeManager.loadFont()
        val currentLabel = fontEntries.entries.find { it.value == currentFont }?.key ?: fontLabels[0]
        binding.tvFontValue.text = currentLabel
    }
    updateLabel()

    binding.btnFontSettings.setOnClickListener {
      val currentFont = themeManager.loadFont()
      val checkedItem = fontValues.indexOf(currentFont).takeIf { it >= 0 } ?: 0

      MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.dialog_select_font))
        .setSingleChoiceItems(fontLabels, checkedItem) { dialog, which ->
            val selectedFont = fontValues[which]
            if (currentFont != selectedFont) {
                themeManager.saveFont(selectedFont)
                requireActivity().recreate()
            }
            dialog.dismiss()
        }
        .setNegativeButton(getString(R.string.cancel), null)
        .show()
    }
  }

  private fun setupCheckboxStyle() {
    val styleEntries = linkedMapOf(
      getString(R.string.checkbox_square) to ThemeManager.CHECKBOX_SQUARE,
      getString(R.string.checkbox_circular) to ThemeManager.CHECKBOX_CIRCULAR
    )
    val styleLabels = styleEntries.keys.toTypedArray()
    val styleValues = styleEntries.values.toList()

    fun updateLabel() {
        val currentStyle = themeManager.loadCheckboxStyle()
        val currentLabel = styleEntries.entries.find { it.value == currentStyle }?.key ?: styleLabels[0]
        binding.tvCheckboxValue.text = currentLabel
        
        // Actualizar el icono de previsualización en el menú
        if (currentStyle == ThemeManager.CHECKBOX_CIRCULAR) {
            binding.ivCheckboxIconPreview.setImageResource(R.drawable.ic_check_circle)
        } else {
            binding.ivCheckboxIconPreview.setImageResource(R.drawable.ic_check_box)
        }
    }
    updateLabel()

    binding.btnCheckboxStyleSettings.setOnClickListener {
      val currentStyle = themeManager.loadCheckboxStyle()
      val checkedItem = styleValues.indexOf(currentStyle).takeIf { it >= 0 } ?: 0

      MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.dialog_select_checkbox_style))
        .setSingleChoiceItems(styleLabels, checkedItem) { dialog, which ->
            val selectedStyle = styleValues[which]
            if (currentStyle != selectedStyle) {
                themeManager.saveCheckboxStyle(selectedStyle)
                requireActivity().recreate()
            }
            dialog.dismiss()
        }
        .setNegativeButton(getString(R.string.cancel), null)
        .show()
    }
  }

  private fun setupFontScale() {
    val currentScale = themeManager.loadFontScale()
    val currentSp = (currentScale * 14.0f)

    binding.sliderFontSize.value = currentSp.coerceIn(8.0f, 20.0f)
    binding.tvFontSizeLabel.text = "${currentSp.toInt()} sp"

    binding.sliderFontSize.addOnChangeListener { _, value, _ ->
      binding.tvFontSizeLabel.text = "${value.toInt()} sp"
    }

    binding.sliderFontSize.addOnSliderTouchListener(object :
      com.google.android.material.slider.Slider.OnSliderTouchListener {
      override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
      override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
        val newSp = slider.value
        val newScale = newSp / 14.0f
        if (newScale != themeManager.loadFontScale()) {
          themeManager.saveFontScale(newScale)
          requireActivity().recreate()
        }
      }
    })
  }

  private fun setupNotificationSettings() {
    val sharedPrefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    
    fun updateTimeLabel() {
        val defaultHour = sharedPrefs.getInt("default_notification_hour", 7)
        val defaultMinute = sharedPrefs.getInt("default_notification_minute", 30)
        binding.tvDailyNotificationTime.text = getString(R.string.daily_at, String.format("%02d:%02d", defaultHour, defaultMinute))
    }
    updateTimeLabel()

    binding.btnDailyNotificationTime.setOnClickListener {
        val picker = android.app.TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                sharedPrefs.edit().apply {
                    putInt("default_notification_hour", selectedHour)
                    putInt("default_notification_minute", selectedMinute)
                    apply()
                }
                updateTimeLabel()
            },
            sharedPrefs.getInt("default_notification_hour", 7),
            sharedPrefs.getInt("default_notification_minute", 30),
            true
        )
        picker.show()
    }

    binding.btnNotificationSettings.setOnClickListener {
      val context = requireContext()
      val intent = Intent()

      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        intent.action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
        intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
      } else {
        intent.action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = android.net.Uri.fromParts("package", context.packageName, null)
      }

      try {
        startActivity(intent)
      } catch (e: Exception) {
        val fallbackIntent = Intent(android.provider.Settings.ACTION_SETTINGS)
        startActivity(fallbackIntent)
      }
    }

    binding.btnViewTutorial.setOnClickListener {
      val intent = Intent(requireContext(), app.polar.ui.activity.TutorialActivity::class.java)
      intent.putExtra(app.polar.ui.activity.TutorialActivity.EXTRA_FROM_SETTINGS, true)
      startActivity(intent)
    }
  }

  private fun setupBackupSettings() {
    binding.btnBackupExport.setOnClickListener {
      val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/json"
        putExtra(Intent.EXTRA_TITLE, "polar_backup_${System.currentTimeMillis()}.json")
      }
      createBackupLauncher.launch(intent)
    }

    binding.btnBackupImport.setOnClickListener {
      val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/json"
      }
      restoreBackupLauncher.launch(intent)
    }

    binding.btnImportCsv.setOnClickListener {
      val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
      }
      importCsvLauncher.launch(intent)
    }
  }

  private val createBackupLauncher =
    registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == android.app.Activity.RESULT_OK) {
        result.data?.data?.let { uri ->
          viewLifecycleOwner.lifecycleScope.launch {
            val backupManager = app.polar.data.backup.BackupManager(requireContext())
            val opResult = backupManager.exportBackup(uri)
            if (opResult.isSuccess) {
              Snackbar.make(binding.root, getString(R.string.backup_saved), Snackbar.LENGTH_SHORT).show()
            } else {
              Snackbar.make(binding.root, getString(R.string.error_saving_backup), Snackbar.LENGTH_SHORT).show()
            }
          }
        }
      }
    }

  private val restoreBackupLauncher =
    registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == android.app.Activity.RESULT_OK) {
        result.data?.data?.let { uri ->
          viewLifecycleOwner.lifecycleScope.launch {
            val backupManager = app.polar.data.backup.BackupManager(requireContext())
            val opResult = backupManager.importBackup(uri)
            if (opResult.isSuccess) {
              Snackbar.make(binding.root, getString(R.string.backup_restored), Snackbar.LENGTH_LONG).show()
              delay(1000)
              requireActivity().recreate()
            } else {
              Snackbar.make(binding.root, getString(R.string.error_restoring_backup), Snackbar.LENGTH_SHORT).show()
            }
          }
        }
      }
    }

  private val importCsvLauncher =
    registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == android.app.Activity.RESULT_OK) {
        result.data?.data?.let { uri ->
          viewLifecycleOwner.lifecycleScope.launch {
            try {
              val contentResolver = requireContext().contentResolver
              contentResolver.openInputStream(uri)?.use { inputStream ->
                java.io.BufferedReader(java.io.InputStreamReader(inputStream)).use { reader ->
                  var line: String? = reader.readLine()
                  val lines = mutableListOf<String>()
                  while (line != null) {
                    if (line.isNotBlank()) lines.add(line)
                    line = reader.readLine()
                  }
                  
                  val db = app.polar.data.AppDatabase.getDatabase(requireContext())
                  val taskListId = db.taskListDao().insert(app.polar.data.entity.TaskList(title = getString(R.string.imported_tasks_list_name)))
                  
                  for ((index, row) in lines.withIndex()) {
                    if (index == 0 && (row.contains("title", ignoreCase = true) || row.contains("desc", ignoreCase = true))) {
                      continue
                    }
                    val parts = row.split(",")
                    val title = parts.getOrNull(0)?.trim()?.removeSurrounding("\"")
                    val desc = parts.getOrNull(1)?.trim()?.removeSurrounding("\"") ?: ""
                    if (!title.isNullOrBlank()) {
                      db.taskDao().insert(app.polar.data.entity.Task(
                        listId = taskListId,
                        title = title,
                        description = desc,
                        priority = 0
                      ))
                    }
                  }
                }
              }
              Snackbar.make(binding.root, getString(R.string.tasks_imported_ok), Snackbar.LENGTH_LONG).show()
            } catch (e: Exception) {
              Snackbar.make(binding.root, getString(R.string.error_importing_csv), Snackbar.LENGTH_SHORT).show()
            }
          }
        }
      }
    }

  private fun setupLanguageSelection() {
    val sharedPrefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    val currentLocale = sharedPrefs.getString("app_locale", "es") ?: "es"

    val langEntries = linkedMapOf(
      "castellano" to "es",
      "english (uk)" to "en-rGB",
      "english (us)" to "en-rUS",
      "deutsch" to "de",
      "francais" to "fr"
    )
    val langLabels = langEntries.keys.toTypedArray()
    val langValues = langEntries.values.toList()

    fun updateLabel() {
        val locale = sharedPrefs.getString("app_locale", "es") ?: "es"
        val label = langEntries.entries.find { it.value == locale }?.key ?: "espanol"
        binding.tvLanguageValue.text = label
    }
    updateLabel()

    binding.btnLanguageSettings.setOnClickListener {
      val checkedItem = langValues.indexOf(currentLocale).takeIf { it >= 0 } ?: 0

      MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.dialog_select_language))
        .setSingleChoiceItems(langLabels, checkedItem) { dialog, which ->
            val selectedLocale = langValues[which]
            if (currentLocale != selectedLocale) {
                sharedPrefs.edit().putString("app_locale", selectedLocale).apply()
                requireActivity().recreate()
            }
            dialog.dismiss()
        }
        .setNegativeButton(getString(R.string.cancel), null)
        .show()
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
