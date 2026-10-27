package app.polar.ui.dialog

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.isVisible
import app.polar.R
import app.polar.data.entity.Reminder
import app.polar.databinding.DialogReminderBinding
import app.polar.util.GeofenceHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Hoja de crear / editar recordatorio: título, descripción, fecha y hora y, opcionalmente, un lugar
 * con aviso al llegar. [onSave] recibe [reminder] (o uno nuevo, con id 0) con los campos editados.
 */
class ReminderDialog(
    private val reminder: Reminder? = null,
    private val onSave: ((Reminder) -> Unit)? = null
) : BottomSheetDialogFragment() {

    private var _binding: DialogReminderBinding? = null
    private val binding get() = _binding!!

    private var selectedDate: Long = System.currentTimeMillis()

    // Ubicación "mientras se usa la app": imprescindible para registrar el aviso.
    private val foregroundLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (_binding == null) return@registerForActivityResult
        if (granted.values.any { it }) {
            requestBackgroundLocationIfNeeded()
        } else {
            binding.switchArrival.isChecked = false
            Toast.makeText(requireContext(), R.string.location_permission_denied, Toast.LENGTH_LONG).show()
        }
        updateArrivalStatus()
    }

    // Ubicación "todo el tiempo" (Android 10+): sin ella el aviso solo llega con la app abierta.
    // Desde Android 11 la petición abre directamente los ajustes del permiso.
    private val backgroundLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateArrivalStatus() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogReminderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvDialogTitle.text = if (reminder == null) getString(R.string.new_reminder) else getString(R.string.edit_reminder)

        if (reminder != null) {
            binding.etTitle.setText(reminder.title)
            binding.etDescription.setText(reminder.description)
            selectedDate = reminder.dateTime
            binding.etLocationName.setText(reminder.locationName ?: "")
            binding.etLatitude.setText(reminder.latitude?.toString() ?: "")
            binding.etLongitude.setText(reminder.longitude?.toString() ?: "")

            val hasLocation = reminder.latitude != null || reminder.longitude != null || !reminder.locationName.isNullOrBlank()
            binding.switchLocation.isChecked = hasLocation
            binding.layoutLocationSection.isVisible = hasLocation
            binding.switchArrival.isChecked = reminder.notifyOnArrival
        }
        updateDateDisplay()
        setupArrival()

        binding.switchLocation.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutLocationSection.isVisible = isChecked
            if (!isChecked) {
                binding.etMapsLink.text?.clear()
                binding.etLocationName.text?.clear()
                binding.etLatitude.text?.clear()
                binding.etLongitude.text?.clear()
                binding.switchArrival.isChecked = false
            }
        }

        binding.btnExtract.setOnClickListener {
            extractCoordinatesFromLink()
        }

        binding.btnViewOnMap.setOnClickListener {
            openLocationInMaps()
        }

        binding.btnSave.setOnClickListener { save() }

        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnDate.setOnClickListener {
            showDateTimePicker()
        }
    }

    override fun onResume() {
        super.onResume()
        // El permiso o la ubicación del dispositivo pueden haber cambiado en los ajustes del sistema.
        updateArrivalStatus()
    }

    private fun save() {
        val title = binding.etTitle.text.toString()
        if (title.isBlank()) {
            binding.etTitle.error = getString(R.string.reminder_title_required)
            return
        }
        val description = binding.etDescription.text.toString()

        val withLocation = binding.switchLocation.isChecked
        val lat = binding.etLatitude.text.toString().trim().toDoubleOrNull().takeIf { withLocation }
        val lng = binding.etLongitude.text.toString().trim().toDoubleOrNull().takeIf { withLocation }
        val hasCoordinates = lat != null && lng != null
        val notifyOnArrival = withLocation && binding.switchArrival.isChecked
        if (notifyOnArrival && !hasCoordinates) {
            Toast.makeText(requireContext(), R.string.reminder_arrival_needs_coordinates, Toast.LENGTH_LONG).show()
            return
        }
        val radius = when {
            !hasCoordinates -> null
            notifyOnArrival -> selectedRadius()
            else -> reminder?.radius ?: LOCATION_ONLY_RADIUS
        }
        val locName = binding.etLocationName.text.toString().trim().takeIf { withLocation && it.isNotEmpty() }

        val base = reminder ?: Reminder(title = title, dateTime = selectedDate)
        onSave?.invoke(
            base.copy(
                title = title,
                description = description,
                dateTime = selectedDate,
                latitude = lat,
                longitude = lng,
                radius = radius,
                locationName = locName,
                notifyOnArrival = notifyOnArrival
            )
        )
        dismiss()
    }

    // ---------------------------------------------------------------------------------------------
    // Aviso al llegar
    // ---------------------------------------------------------------------------------------------

    private fun setupArrival() {
        val selected = reminder?.radius?.takeIf { reminder.notifyOnArrival && it in GeofenceHelper.RADIUS_OPTIONS }
            ?: GeofenceHelper.DEFAULT_RADIUS_METERS
        GeofenceHelper.RADIUS_OPTIONS.forEach { meters ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                tag = meters
                text = if (meters >= 1000f) getString(R.string.distance_kilometers, (meters / 1000f).toInt())
                else getString(R.string.distance_meters, meters.toInt())
                isCheckable = true
                isChecked = meters == selected
            }
            binding.chipGroupRadius.addView(chip)
        }

        binding.layoutArrivalOptions.isVisible = binding.switchArrival.isChecked
        binding.switchArrival.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutArrivalOptions.isVisible = isChecked
            if (isChecked) requestLocationIfNeeded()
            updateArrivalStatus()
        }
        binding.tvArrivalStatus.setOnClickListener { fixArrivalProblem() }
    }

    private fun selectedRadius(): Float =
        binding.chipGroupRadius.findViewById<Chip>(binding.chipGroupRadius.checkedChipId)?.tag as? Float
            ?: GeofenceHelper.DEFAULT_RADIUS_METERS

    private fun requestLocationIfNeeded() {
        if (!GeofenceHelper.hasLocationPermission(requireContext())) {
            foregroundLocationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            requestBackgroundLocationIfNeeded()
        }
    }

    // El permiso "todo el tiempo" se pide aparte y después de explicar para qué sirve.
    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || GeofenceHelper.hasBackgroundPermission(requireContext())) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.location_permission_title)
            .setMessage(R.string.location_permission_message)
            .setNegativeButton(R.string.not_now, null)
            .setPositiveButton(R.string.location_permission_allow) { _, _ ->
                backgroundLocationPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .show()
    }

    /** Explica, debajo del radio, por qué el aviso al llegar no podría funcionar ahora. */
    private fun updateArrivalStatus() {
        val binding = _binding ?: return
        val context = requireContext()
        val problem = when {
            !binding.switchArrival.isChecked -> null
            !GeofenceHelper.canNotifyOnArrival(context) -> R.string.reminder_arrival_permission_missing
            !GeofenceHelper.isLocationEnabled(context) -> R.string.reminder_arrival_location_off
            else -> null
        }
        binding.tvArrivalStatus.isVisible = problem != null
        problem?.let(binding.tvArrivalStatus::setText)
    }

    private fun fixArrivalProblem() {
        val context = requireContext()
        when {
            !GeofenceHelper.hasLocationPermission(context) -> requestLocationIfNeeded()
            !GeofenceHelper.hasBackgroundPermission(context) -> requestBackgroundLocationIfNeeded()
            else -> openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().packageName, null))
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Ubicación
    // ---------------------------------------------------------------------------------------------

    private fun extractCoordinatesFromLink() {
        val link = binding.etMapsLink.text.toString().trim()
        if (link.isBlank()) return

        if (link.contains("maps.app.goo.gl") || link.contains("goo.gl/maps")) {
            Toast.makeText(
                requireContext(),
                getString(R.string.short_link_not_supported),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val coords = parseGoogleMapsUrl(link)
        if (coords != null) {
            binding.etLatitude.setText(coords.first.toString())
            binding.etLongitude.setText(coords.second.toString())

            // Try to extract location name from URL path if empty
            if (binding.etLocationName.text.isNullOrBlank()) {
                val name = extractLocationNameFromUrl(link)
                if (name != null) {
                    binding.etLocationName.setText(name)
                }
            }
        } else {
            Toast.makeText(
                requireContext(),
                getString(R.string.invalid_maps_link),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openLocationInMaps() {
        val lat = binding.etLatitude.text.toString().trim().toDoubleOrNull()
        val lng = binding.etLongitude.text.toString().trim().toDoubleOrNull()
        val name = binding.etLocationName.text.toString().trim()

        if (lat == null || lng == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.invalid_coordinates),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val uri = if (name.isNotEmpty()) {
            "geo:$lat,$lng?q=$lat,$lng(${Uri.encode(name)})".toUri()
        } else {
            "geo:$lat,$lng?q=$lat,$lng".toUri()
        }

        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.reminder_no_maps, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDateTimePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_date))
            .setSelection(selectedDate)
            .build()

        datePicker.addOnPositiveButtonClickListener { dateSelection ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = dateSelection

            val currentCalendar = Calendar.getInstance().apply { timeInMillis = selectedDate }

            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(currentCalendar.get(Calendar.HOUR_OF_DAY))
                .setMinute(currentCalendar.get(Calendar.MINUTE))
                .setTitleText(getString(R.string.select_time))
                .build()

            timePicker.addOnPositiveButtonClickListener {
                calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                calendar.set(Calendar.MINUTE, timePicker.minute)
                selectedDate = calendar.timeInMillis
                updateDateDisplay()
            }

            timePicker.show(parentFragmentManager, "TimePicker")
        }

        datePicker.show(parentFragmentManager, "DatePicker")
    }

    private fun updateDateDisplay() {
        // Fecha y hora con el patrón y el formato 12/24 h del idioma y del sistema.
        val locale = Locale.getDefault()
        val skeleton = if (DateFormat.is24HourFormat(requireContext())) "EEEdMMMyHHmm" else "EEEdMMMyhmma"
        val format = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        binding.tvDueDate.text = format.format(java.util.Date(selectedDate)).lowercase(locale)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        // Radio que se guardaba siempre con unas coordenadas, antes de existir el aviso al llegar.
        private const val LOCATION_ONLY_RADIUS = 100f

        /**
         * Parses common Google Maps URL formats to extract latitude and longitude.
         * Works entirely offline using regex patterns.
         */
        fun parseGoogleMapsUrl(url: String): Pair<Double, Double>? {
            val trimmed = url.trim()

            // Pattern 1: https://www.google.com/maps/place/.../@lat,lng,zoom
            val placePattern = "@([-+]?\\d+\\.\\d+),([-+]?\\d+\\.\\d+)".toRegex()
            placePattern.find(trimmed)?.let {
                val lat = it.groupValues[1].toDoubleOrNull()
                val lng = it.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }

            // Pattern 2: ?q=lat,lng or &query=lat,lng
            val queryPattern = "[?&](q|query)=([-+]?\\d+\\.\\d+)%2C([-+]?\\d+\\.\\d+)".toRegex(RegexOption.IGNORE_CASE)
            queryPattern.find(trimmed)?.let {
                val lat = it.groupValues[2].toDoubleOrNull()
                val lng = it.groupValues[3].toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }

            // Pattern 3: ?q=lat,lng (with comma, not URL encoded)
            val queryCommaPattern = "[?&](q|query)=([-+]?\\d+\\.\\d+),([-+]?\\d+\\.\\d+)".toRegex(RegexOption.IGNORE_CASE)
            queryCommaPattern.find(trimmed)?.let {
                val lat = it.groupValues[2].toDoubleOrNull()
                val lng = it.groupValues[3].toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }

            // Pattern 4: geo:lat,lng
            val geoPattern = "geo:(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)".toRegex(RegexOption.IGNORE_CASE)
            geoPattern.find(trimmed)?.let {
                val lat = it.groupValues[1].toDoubleOrNull()
                val lng = it.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }

            return null
        }

        /**
         * Attempts to extract a human-readable location name from a Google Maps URL.
         * For example: /place/Name+Of+Place/ or /place/Name%20Of%20Place/
         */
        fun extractLocationNameFromUrl(url: String): String? {
            val placePattern = "/place/([^/@]+)".toRegex()
            placePattern.find(url)?.let {
                val raw = it.groupValues[1]
                return raw.replace("+", " ").replace("%20", " ").trim()
            }
            return null
        }
    }
}
