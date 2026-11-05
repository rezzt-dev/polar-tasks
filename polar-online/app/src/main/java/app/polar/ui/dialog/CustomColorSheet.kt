package app.polar.ui.dialog

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.annotation.AttrRes
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.polar.R
import app.polar.databinding.BottomSheetCustomColorBinding
import app.polar.ui.viewmodel.CustomThemeViewModel
import app.polar.ui.widget.ColorSwatch
import app.polar.ui.widget.ThemePreviewColors
import app.polar.ui.widget.setRadioSemantics
import app.polar.util.CustomColors
import app.polar.util.CustomTheme
import app.polar.util.CustomThemePalette
import app.polar.util.CustomThemePalette.Slot
import app.polar.util.CustomThemeScheme
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch
import com.google.android.material.R as MaterialR

/**
 * Hoja para elegir un color a medida (`#RRGGBB`) para un hueco del tema personalizado: selector
 * hsv, codigo hex de 6 caracteres, contraste con el fondo y colores recientes. Cada cambio va al
 * borrador de [CustomThemeViewModel], asi que la vista previa del editor (visible encima) lo
 * muestra al momento. "cancelar" devuelve el hueco al valor que tenia al abrirse; "listo", atras o
 * deslizar la hoja conservan el color, que se guarda solo al aplicar el tema.
 */
class CustomColorSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCustomColorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CustomThemeViewModel by activityViewModels()

    private val slot: Slot by lazy { Slot.valueOf(requireArguments().getString(ARG_SLOT)!!) }
    private val original: String by lazy { requireArguments().getString(ARG_ORIGINAL)!! }

    /** Evita que escribir el codigo desde el selector se tome como una edicion del usuario. */
    private var syncingText = false
    private var renderedRecent: List<String>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetCustomColorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        binding.tvCustomColorTitle.text = title()
        val start = shownColor(viewModel.uiState.value.draft)
        binding.colorPicker.setColor(start)
        showHex(start)
        binding.colorPicker.onColorChanged = { color ->
            showHex(color)
            select(color)
        }
        setupHexField()

        binding.btnCustomColorCancel.setOnClickListener {
            viewModel.select(slot, original)
            dismiss()
        }
        binding.btnCustomColorDone.setOnClickListener {
            if (validateHex()) {
                rememberColor()
                dismiss()
            } else {
                binding.etHex.requestFocus()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it.draft) } }
                launch {
                    viewModel.recentColors.collect { recent ->
                        renderRecent(recent)
                        render(viewModel.uiState.value.draft)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.colorPicker.onColorChanged = null
        _binding = null
        super.onDestroyView()
    }

    // Atras, tocar fuera o deslizar la hoja: se queda el color elegido, como con "listo".
    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        rememberColor()
    }

    // ---------------------------------------------------------------------------------------------
    // Codigo hex
    // ---------------------------------------------------------------------------------------------

    private fun setupHexField() {
        binding.etHex.filters = arrayOf(HexDigitsFilter, InputFilter.LengthFilter(CustomColors.HEX_LENGTH))
        binding.etHex.doAfterTextChanged { text ->
            if (syncingText) return@doAfterTextChanged
            val color = CustomColors.parse(text?.toString()) ?: return@doAfterTextChanged
            binding.tilHex.error = null
            binding.colorPicker.setColor(color)
            select(color)
        }
        binding.etHex.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            if (validateHex()) hideKeyboard()
            true
        }
        binding.etHex.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) validateHex() }
    }

    /** Muestra el codigo de [color] sin moverlo si ya es el que hay escrito. */
    private fun showHex(color: Int) {
        val digits = CustomColors.format(color).drop(1)
        binding.tilHex.error = null
        if (binding.etHex.text?.toString() == digits) return
        syncingText = true
        binding.etHex.setText(digits)
        binding.etHex.setSelection(digits.length)
        syncingText = false
    }

    /** Marca el error si el codigo esta incompleto; `true` si es valido. */
    private fun validateHex(): Boolean {
        val valid = CustomColors.parse(binding.etHex.text?.toString()) != null
        binding.tilHex.error = if (valid) null else getString(R.string.custom_color_hex_error)
        return valid
    }

    private fun hideKeyboard() {
        binding.etHex.clearFocus()
        requireContext().getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(binding.etHex.windowToken, 0)
    }

    // ---------------------------------------------------------------------------------------------
    // Estado
    // ---------------------------------------------------------------------------------------------

    private fun select(color: Int) = viewModel.select(slot, CustomColors.format(color))

    /** Guarda el color en los recientes si el hueco acabo con un color a medida. */
    private fun rememberColor() {
        val value = viewModel.uiState.value.draft[slot]
        if (CustomThemePalette.customColor(value) != null) viewModel.rememberRecentColor(value)
    }

    /** Color del hueco: el codigo elegido o, si aun es de la paleta, el tono con el que se ve. */
    private fun shownColor(draft: CustomTheme): Int =
        CustomThemePalette.customColor(draft[slot])
            ?: ThemePreviewColors.forCustomTheme(requireContext(), draft).colorOf(slot)

    private fun render(draft: CustomTheme) {
        if (_binding == null) return
        val colors = ThemePreviewColors.forCustomTheme(requireContext(), draft)
        val color = colors.colorOf(slot)
        val hex = CustomColors.format(color)

        binding.viewColorPreview.background = ColorSwatch.drawable(
            requireContext(), color, selected = false,
            ringColor = attrColor(MaterialR.attr.colorOnSurface),
            gapColor = attrColor(MaterialR.attr.colorSurface),
            borderColor = attrColor(MaterialR.attr.colorOutline)
        )
        binding.viewColorPreview.contentDescription = getString(R.string.custom_color_preview_description, hex)

        renderContrast(colors, color)
        renderRecentSelection(CustomThemePalette.customColor(draft[slot])?.let(CustomColors::format))
    }

    /**
     * Contraste de lo que se esta eligiendo: el texto sobre un fondo, el acento sobre el fondo y la
     * marca de prioridad sobre las tarjetas, con los mismos minimos que la paleta.
     */
    private fun renderContrast(colors: ThemePreviewColors, color: Int) {
        val (ratio, minimum) = when (slot) {
            Slot.BACKGROUND -> CustomColors.contrast(colors.onSurface, colors.background) to CustomThemeScheme.MIN_TEXT
            Slot.ACCENT -> CustomColors.contrast(color, colors.background) to CustomThemeScheme.MIN_ACCENT
            else -> CustomColors.contrast(color, colors.surface) to CustomThemeScheme.MIN_PRIORITY
        }
        val good = ratio >= minimum
        val formatted = String.format(resources.configuration.locales[0], "%.1f", ratio)
        val message = when {
            slot == Slot.BACKGROUND && good -> R.string.custom_color_text_good
            slot == Slot.BACKGROUND -> R.string.custom_color_text_low
            good -> R.string.custom_color_contrast_good
            else -> R.string.custom_color_contrast_low
        }
        binding.tvContrast.text = getString(message, formatted)
        binding.tvContrast.setTextColor(attrColor(if (good) MaterialR.attr.colorOnSurfaceVariant else MaterialR.attr.colorOnSurface))
        binding.ivContrast.setImageResource(if (good) R.drawable.ic_contrast else R.drawable.ic_warning)
        binding.ivContrast.imageTintList = ColorStateList.valueOf(
            attrColor(if (good) MaterialR.attr.colorOnSurfaceVariant else androidx.appcompat.R.attr.colorError)
        )
    }

    private fun renderRecent(recent: List<String>) {
        if (recent == renderedRecent) return
        renderedRecent = recent
        binding.tvRecentColors.isVisible = recent.isNotEmpty()
        binding.groupRecentColors.isVisible = recent.isNotEmpty()
        binding.groupRecentColors.removeAllViews()
        val size = resources.getDimensionPixelSize(R.dimen.custom_color_recent_size)
        recent.forEach { hex ->
            val color = CustomColors.parse(hex) ?: return@forEach
            binding.groupRecentColors.addView(View(requireContext()).apply {
                layoutParams = ChipGroup.LayoutParams(size, size)
                tag = hex
                contentDescription = getString(R.string.color_option, hex)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    binding.colorPicker.setColor(color)
                    showHex(color)
                    select(color)
                }
            })
        }
    }

    private fun renderRecentSelection(selected: String?) {
        val ring = attrColor(MaterialR.attr.colorOnSurface)
        val gap = attrColor(MaterialR.attr.colorSurface)
        val outline = attrColor(MaterialR.attr.colorOutlineVariant)
        for (i in 0 until binding.groupRecentColors.childCount) {
            val view = binding.groupRecentColors.getChildAt(i)
            val hex = view.tag as? String ?: continue
            val color = CustomColors.parse(hex) ?: continue
            val isSelected = hex == selected
            view.background = ColorSwatch.drawable(requireContext(), color, isSelected, ring, gap, outline)
            view.isSelected = isSelected
            view.setRadioSemantics(isSelected)
        }
    }

    private fun title(): String = when (slot) {
        Slot.BACKGROUND -> getString(R.string.custom_color_title_background)
        Slot.ACCENT -> getString(R.string.custom_theme_accent)
        Slot.PRIORITY_HIGH -> getString(R.string.custom_color_title_priority, getString(R.string.priority_high))
        Slot.PRIORITY_MEDIUM -> getString(R.string.custom_color_title_priority, getString(R.string.priority_medium))
        Slot.PRIORITY_LOW -> getString(R.string.custom_color_title_priority, getString(R.string.priority_low))
    }

    private fun attrColor(@AttrRes attr: Int): Int = MaterialColors.getColor(requireContext(), attr, Color.GRAY)

    /** Solo deja escribir digitos hex y los pasa a mayusculas (tambien al pegar `#3a7bd5`). */
    private object HexDigitsFilter : InputFilter {
        override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
            val typed = source.subSequence(start, end).toString()
            val filtered = typed.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.uppercase()
            return if (filtered == typed) null else filtered
        }
    }

    companion object {
        const val TAG = "CustomColorSheet"
        private const val ARG_SLOT = "slot"
        private const val ARG_ORIGINAL = "original"

        /** @param original valor del hueco al abrir la hoja, al que vuelve "cancelar". */
        fun newInstance(slot: Slot, original: String) = CustomColorSheet().apply {
            arguments = bundleOf(ARG_SLOT to slot.name, ARG_ORIGINAL to original)
        }
    }
}
