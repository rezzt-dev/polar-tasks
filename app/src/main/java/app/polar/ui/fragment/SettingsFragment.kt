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
                // Fallback to generic settings if specific fails
                val fallbackIntent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                startActivity(fallbackIntent)
            }
        }
    }

    private fun setupThemeSwitch() {
        // Set initial state based on current actual theme logic
        // We use !isLightTheme() because switch ON = Dark Mode
        binding.switchDarkMode.isChecked = !themeManager.isLightTheme()
        
        binding.switchDarkMode.setOnCheckedChangeListener { buttonView, isChecked ->
            // Prevent triggering if the state hasn't actually changed logically
            // (though UI click implies intent to change)
            
            val newTheme = if (isChecked) ThemeManager.THEME_DARK else ThemeManager.THEME_LIGHT
            
            // Only save and recreate if different from current persisted/loaded theme
            // to avoid potential loops if listener fires unexpectedly
            if (themeManager.loadTheme() != newTheme) {
                themeManager.saveTheme(newTheme)
                // Post recreate to allow switch animation to finish or state to settle
                buttonView.postDelayed({
                    activity?.recreate()
                }, 200)
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
        
        binding.sliderFontSize.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
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
        val currentFont = themeManager.loadFont()
        
        when (currentFont) {
            ThemeManager.FONT_POPPINS -> binding.rbPoppins.isChecked = true
            ThemeManager.FONT_COMFORTAA -> binding.rbComfortaa.isChecked = true
            ThemeManager.FONT_FIGTREE -> binding.rbFigtree.isChecked = true
            ThemeManager.FONT_JETBRAINS_MONO -> binding.rbJetBrainsMono.isChecked = true
            ThemeManager.FONT_ARIAL -> binding.rbArial.isChecked = true
            ThemeManager.FONT_SYSTEM -> binding.rbSystem.isChecked = true
        }

        binding.rgFonts.setOnCheckedChangeListener { _, checkedId ->
            val selectedFont = when (checkedId) {
                R.id.rbPoppins -> ThemeManager.FONT_POPPINS
                R.id.rbComfortaa -> ThemeManager.FONT_COMFORTAA
                R.id.rbFigtree -> ThemeManager.FONT_FIGTREE
                R.id.rbJetBrainsMono -> ThemeManager.FONT_JETBRAINS_MONO
                R.id.rbArial -> ThemeManager.FONT_ARIAL
                R.id.rbSystem -> ThemeManager.FONT_SYSTEM
                else -> ThemeManager.FONT_POPPINS
            }
            
            if (selectedFont != currentFont) {
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

    private val createBackupLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val backupManager = app.polar.data.backup.BackupManager(requireContext())
                    val result = backupManager.exportBackup(uri)
                    if (result.isSuccess) {
                        com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.backup_saved), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                    } else {
                        com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.error_saving_backup), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val restoreBackupLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val backupManager = app.polar.data.backup.BackupManager(requireContext())
                    val result = backupManager.importBackup(uri)
                    if (result.isSuccess) {
                        com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.backup_restored), com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
                        kotlinx.coroutines.delay(1000)
                        requireActivity().recreate()
                        // Or restart app fully if needed, but recreate should trigger loaders again
                    } else {
                        com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.error_restoring_backup), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
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
