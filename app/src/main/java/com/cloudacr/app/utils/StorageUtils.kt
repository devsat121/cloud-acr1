package com.cloudacr.app.utils

import android.content.Context
import android.os.Environment
import androidx.preference.PreferenceManager
import com.cloudacr.app.CloudACRApp
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object StorageUtils {

    fun createRecordingFile(context: Context, phoneNumber: String): File {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val format = prefs.getString(CloudACRApp.PREF_SAVE_FORMAT, "m4a") ?: "m4a"

        val dir = getRecordingsDirectory(context)
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val safe = phoneNumber.replace(Regex("[^0-9+]"), "").ifBlank { "unknown" }
        return File(dir, "CloudACR_${safe}_$timestamp.$format")
    }

    fun getRecordingsDirectory(context: Context): File {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val customPath = prefs.getString(CloudACRApp.PREF_SAVE_LOCATION, null)
        return if (customPath != null) {
            File(customPath)
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "CloudACR Recordings"
            )
        }
    }

    fun formatBytes(bytes: Long): String = when {
        bytes > 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes > 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    fun formatDuration(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / 1000 / 60) % 60
        val h = ms / 1000 / 3600
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
