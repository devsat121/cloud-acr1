package com.cloudacr.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import com.cloudacr.app.CloudACRApp
import com.cloudacr.app.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Open Accessibility Settings for helper
            findPreference<Preference>("pref_open_accessibility")?.setOnPreferenceClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                true
            }

            // Open App Notifications
            findPreference<Preference>("pref_open_notifications")?.setOnPreferenceClickListener {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                }
                startActivity(intent)
                true
            }

            // Open Storage picker
            findPreference<Preference>(CloudACRApp.PREF_SAVE_LOCATION)?.setOnPreferenceClickListener {
                // TODO: launch directory picker (SAF)
                true
            }
        }
    }
}
