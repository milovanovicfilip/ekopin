package com.example.mobile.ui.settings

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.mobile.R
import com.example.mobile.databinding.ActivitySettingsBinding
import com.example.mobile.util.SettingsPrefs

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupThemeSelector()
        setupSimLocationInput()

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupThemeSelector() {
        val themes = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system)
        )
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spTheme.adapter = adapter

        val currentTheme = SettingsPrefs.getThemeMode(this)
        val themeIndex = when (currentTheme) {
            SettingsPrefs.THEME_LIGHT -> 0
            SettingsPrefs.THEME_DARK -> 1
            SettingsPrefs.THEME_SYSTEM -> 2
            else -> 2
        }
        binding.spTheme.setSelection(themeIndex)

        binding.btnApplyTheme.setOnClickListener {
            val selectedIndex = binding.spTheme.selectedItemPosition
            val newTheme = when (selectedIndex) {
                0 -> SettingsPrefs.THEME_LIGHT
                1 -> SettingsPrefs.THEME_DARK
                2 -> SettingsPrefs.THEME_SYSTEM
                else -> SettingsPrefs.THEME_SYSTEM
            }
            
            SettingsPrefs.setThemeMode(this, newTheme)
            applyTheme(newTheme)
        }
    }

    private fun setupSimLocationInput() {
        val savedLocation = SettingsPrefs.getSimLocation(this)
        updateLocationDisplay(savedLocation)

        binding.btnPickLocation.setOnClickListener {
            showLocationPicker()
        }
    }

    private fun showLocationPicker() {
        val savedLocation = SettingsPrefs.getSimLocation(this)
        val (lat, lng) = parseLocation(savedLocation)
        
        val dialog = LocationPickerDialog.newInstance(lat, lng)
        dialog.onLocationSelected = { selectedLat, selectedLng ->
            val locationString = "$selectedLat,$selectedLng"
            SettingsPrefs.setSimLocation(this, locationString)
            updateLocationDisplay(locationString)
            binding.tvLocationSaved.text = getString(R.string.location_saved)
        }
        dialog.show(supportFragmentManager, "LocationPickerDialog")
    }

    private fun parseLocation(location: String): Pair<Double, Double> {
        if (location.isBlank()) return Pair(46.5547, 15.6467) // Default Maribor
        
        val parts = location.split(",")
        if (parts.size != 2) return Pair(46.5547, 15.6467)
        
        val lat = parts[0].toDoubleOrNull() ?: 46.5547
        val lng = parts[1].toDoubleOrNull() ?: 15.6467
        return Pair(lat, lng)
    }

    private fun updateLocationDisplay(location: String) {
        if (location.isBlank()) {
            binding.tvCurrentLocation.text = getString(R.string.no_location_set)
        } else {
            val (lat, lng) = parseLocation(location)
            binding.tvCurrentLocation.text = String.format("📍 %.6f, %.6f", lat, lng)
        }
    }

    private fun applyTheme(theme: String) {
        val mode = when (theme) {
            SettingsPrefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SettingsPrefs.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            SettingsPrefs.THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
