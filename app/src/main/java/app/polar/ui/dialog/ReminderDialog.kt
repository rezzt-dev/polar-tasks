package app.polar.ui.dialog

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import app.polar.R
import app.polar.databinding.DialogReminderBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import android.widget.Toast
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReminderDialog(
    private val reminder: app.polar.data.entity.Reminder? = null,
    private val onSave: ((String, String, Long) -> Unit)? = null,
    private val onSaveWithLocation: ((String, String, Long, Double?, Double?, Float?, String?) -> Unit)? = null
) : BottomSheetDialogFragment() {

    private var _binding: DialogReminderBinding? = null
    private val binding get() = _binding!!

    private var selectedDate: Long = System.currentTimeMillis()

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

            updateDateDisplay()
        } else {
            updateDateDisplay()
        }

        binding.switchLocation.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutLocationSection.isVisible = isChecked
            if (!isChecked) {
                binding.etMapsLink.text?.clear()
                binding.etLocationName.text?.clear()
                binding.etLatitude.text?.clear()
                binding.etLongitude.text?.clear()
            }
        }

        binding.btnExtract.setOnClickListener {
            extractCoordinatesFromLink()
        }

        binding.btnViewOnMap.setOnClickListener {
            openLocationInMaps()
        }

        binding.btnSave.setOnClickListener {
            val title = binding.etTitle.text.toString()
            if (title.isBlank()) {
                binding.etTitle.error = "obligatorio"
                return@setOnClickListener
            }
            val description = binding.etDescription.text.toString()

            val lat: Double?
            val lng: Double?
            val radius: Float?
            val locName: String?

            if (binding.switchLocation.isChecked) {
                lat = binding.etLatitude.text.toString().trim().toDoubleOrNull()
                lng = binding.etLongitude.text.toString().trim().toDoubleOrNull()
                radius = if (lat != null && lng != null) 100f else null
                locName = binding.etLocationName.text.toString().trim().takeIf { it.isNotEmpty() }
            } else {
                lat = null
                lng = null
                radius = null
                locName = null
            }

            if (onSaveWithLocation != null) {
                onSaveWithLocation.invoke(title, description, selectedDate, lat, lng, radius, locName)
            } else {
                onSave?.invoke(title, description, selectedDate)
            }
            dismiss()
        }

        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnDate.setOnClickListener {
            showDateTimePicker()
        }
    }

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
                "introduce coordenadas validas",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val uri = if (name.isNotEmpty()) {
            Uri.parse("geo:$lat,$lng?q=$lat,$lng($name)")
        } else {
            Uri.parse("geo:$lat,$lng?q=$lat,$lng")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            // Fallback to any maps app
            val genericIntent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(genericIntent)
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
        val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val formattedDate = format.format(java.util.Date(selectedDate)).lowercase().replace(".", "")
        binding.tvDueDate.text = formattedDate
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
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
