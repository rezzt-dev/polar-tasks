package app.polar.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.polar.MainActivity
import app.polar.R
import app.polar.databinding.FragmentSettingsBinding
import app.polar.util.ThemeManager
import androidx.lifecycle.lifecycleScope
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

    setupThemeSwitch()
    setupFontSelection()
    setupFontScale()
    setupNotificationSettings()
    setupBackupSettings()
  }

  private fun setupNotificationSettings() {
    binding.btnNotificationSettings.setOnClickListener {
      val context = requireContext()
      val intent = android.content.Intent()

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
        val fallbackIntent =
          android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
        startActivity(fallbackIntent)
      }
    }

    binding.btnViewTutorial.setOnClickListener {
      val intent = android.content.Intent(requireContext(), app.polar.ui.activity.TutorialActivity::class.java)
      intent.putExtra(app.polar.ui.activity.TutorialActivity.EXTRA_FROM_SETTINGS, true)
      startActivity(intent)
    }
  }

  private fun <T> createNonFilteringAdapter(items: List<T>): android.widget.ArrayAdapter<T> {
    return object : android.widget.ArrayAdapter<T>(
      requireContext(),
      android.R.layout.simple_dropdown_item_1line,
      items
    ) {
      private val noFilter = object : android.widget.Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
          return FilterResults().apply {
            values = items
            count = items.size
          }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
          notifyDataSetChanged()
        }
      }

      override fun getFilter(): android.widget.Filter = noFilter
    }
  }

  private fun setupThemeSwitch() {
    val themeEntries = linkedMapOf(
      getString(R.string.theme_light) to ThemeManager.THEME_LIGHT,
      getString(R.string.theme_dark) to ThemeManager.THEME_DARK,
      getString(R.string.theme_multicolor_light) to ThemeManager.THEME_MULTICOLOR_LIGHT,
      getString(R.string.theme_multicolor_dark) to ThemeManager.THEME_MULTICOLOR_DARK,
      getString(R.string.theme_pastel) to ThemeManager.THEME_PASTEL,
      getString(R.string.theme_neon) to ThemeManager.THEME_NEON
    )

    val themeLabels = themeEntries.keys.toList()
    binding.actvTheme.setAdapter(createNonFilteringAdapter(themeLabels))

    val currentTheme = themeManager.loadTheme()
    val currentLabel =
      themeEntries.entries.find { it.value == currentTheme }?.key ?: themeLabels[1]
    binding.actvTheme.setText(currentLabel, false)

    binding.actvTheme.setOnItemClickListener { _, _, position, _ ->
      val selectedTheme = themeEntries.values.toList()[position]
      if (themeManager.loadTheme() != selectedTheme) {
        themeManager.saveTheme(selectedTheme)
        activity?.recreate()
      }
    }
  }

  private fun setupFontScale() {
    val currentScale = themeManager.loadFontScale()
    // Convert scale back to sp (assuming base 14sp = 1.0f)
    val currentSp = (currentScale * 14.0f)

    binding.sliderFontSize.value = currentSp.coerceIn(8.0f, 20.0f)
    binding.tvFontSizeLabel.text = "${currentSp.toInt()} sp"

    binding.sliderFontSize.addOnChangeListener { _, value, _ ->
      binding.tvFontSizeLabel.text = "${value.toInt()} sp"
    }

    binding.sliderFontSize.addOnSliderTouchListener(object :
      com.google.android.material.slider.Slider.OnSliderTouchListener {
      override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {
        // No op
      }

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

  private fun setupFontSelection() {
    val fontEntries = linkedMapOf(
      getString(R.string.font_poppins) to ThemeManager.FONT_POPPINS,
      getString(R.string.font_comfortaa) to ThemeManager.FONT_COMFORTAA,
      getString(R.string.font_figtree) to ThemeManager.FONT_FIGTREE,
      getString(R.string.font_jetbrains_mono) to ThemeManager.FONT_JETBRAINS_MONO,
      getString(R.string.font_arial) to ThemeManager.FONT_ARIAL,
      getString(R.string.font_system) to ThemeManager.FONT_SYSTEM
    )

    val fontLabels = fontEntries.keys.toList()
    binding.actvFont.setAdapter(createNonFilteringAdapter(fontLabels))

    val currentFont = themeManager.loadFont()
    val currentLabel =
      fontEntries.entries.find { it.value == currentFont }?.key ?: fontLabels[0]
    binding.actvFont.setText(currentLabel, false)

    binding.actvFont.setOnItemClickListener { _, _, position, _ ->
      val selectedFont = fontEntries.values.toList()[position]
      if (themeManager.loadFont() != selectedFont) {
        themeManager.saveFont(selectedFont)
        requireActivity().recreate()
      }
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
  }

  private val createBackupLauncher =
    registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == android.app.Activity.RESULT_OK) {
        result.data?.data?.let { uri ->
          viewLifecycleOwner.lifecycleScope.launch {
            val backupManager = app.polar.data.backup.BackupManager(requireContext())
            val result = backupManager.exportBackup(uri)
            if (result.isSuccess) {
              com.google.android.material.snackbar.Snackbar.make(
                binding.root,
                getString(R.string.backup_saved),
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
              ).show()
            } else {
              com.google.android.material.snackbar.Snackbar.make(
                binding.root,
                getString(R.string.error_saving_backup),
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
              ).show()
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
            val result = backupManager.importBackup(uri)
            if (result.isSuccess) {
              com.google.android.material.snackbar.Snackbar.make(
                binding.root,
                getString(R.string.backup_restored),
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
              ).show()
              kotlinx.coroutines.delay(1000)
              requireActivity().recreate()
              // Or restart app fully if needed, but recreate should trigger loaders again
            } else {
              com.google.android.material.snackbar.Snackbar.make(
                binding.root,
                getString(R.string.error_restoring_backup),
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
              ).show()
            }
          }
        }
      }
    }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
