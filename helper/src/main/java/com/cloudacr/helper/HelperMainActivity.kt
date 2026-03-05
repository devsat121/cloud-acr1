package com.cloudacr.helper

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.cloudacr.app.R
import com.cloudacr.helper.databinding.ActivityHelperMainBinding

class HelperMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelperMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelperMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnOpenMainApp.setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage("com.cloudacr.app")
            if (intent != null) startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val enabled = isAccessibilityServiceEnabled()
        val running = ACRAccessibilityService.isRunning

        if (enabled && running) {
            binding.statusIcon.setImageResource(R.drawable.ic_check)
            binding.statusTitle.text = "Helper is Active"
            binding.statusSubtitle.text = "Accessibility service running · Main app connected"
            binding.statusCard.setCardBackgroundColor(getColor(R.color.status_ok))
            binding.btnEnableAccessibility.visibility = View.GONE
        } else if (enabled) {
            binding.statusIcon.setImageResource(R.drawable.ic_check)
            binding.statusTitle.text = "Service Enabled"
            binding.statusSubtitle.text = "Accessibility enabled · Waiting for main app"
            binding.statusCard.setCardBackgroundColor(getColor(R.color.status_warn))
            binding.btnEnableAccessibility.visibility = View.GONE
        } else {
            binding.statusIcon.setImageResource(R.drawable.ic_warning)
            binding.statusTitle.text = "Setup Required"
            binding.statusSubtitle.text = "Enable the accessibility service to allow the helper to work"
            binding.statusCard.setCardBackgroundColor(getColor(R.color.status_error))
            binding.btnEnableAccessibility.visibility = View.VISIBLE
        }

        val mainInstalled = try {
            packageManager.getPackageInfo("com.cloudacr.app", 0)
            true
        } catch (e: Exception) { false }

        binding.mainAppStatus.text = if (mainInstalled)
            "✓ Cloud ACR main app installed"
        else
            "✗ Cloud ACR main app not installed"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${ACRAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return TextUtils.SimpleStringSplitter(':').apply {
            setString(enabledServices)
        }.any { it.equals(service, ignoreCase = true) }
    }
}
