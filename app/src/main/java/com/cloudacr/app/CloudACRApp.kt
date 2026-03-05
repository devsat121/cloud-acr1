package com.cloudacr.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.cloudacr.app.data.AppDatabase
import com.cloudacr.app.data.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class CloudACRApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob())
    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { RecordingRepository(database.recordingDao()) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Recording active channel
        val recordingChannel = NotificationChannel(
            CHANNEL_RECORDING,
            "Active Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while a call is being recorded"
            setSound(null, null)
            enableVibration(false)
        }

        // General alerts channel
        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Alerts & Status",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Recording saved, errors, and status updates"
        }

        manager.createNotificationChannel(recordingChannel)
        manager.createNotificationChannel(alertChannel)
    }

    companion object {
        const val CHANNEL_RECORDING = "channel_recording"
        const val CHANNEL_ALERTS = "channel_alerts"
        const val PREF_AUTO_RECORD = "pref_auto_record"
        const val PREF_RECORD_INCOMING = "pref_record_incoming"
        const val PREF_RECORD_OUTGOING = "pref_record_outgoing"
        const val PREF_SAVE_FORMAT = "pref_save_format"
        const val PREF_SAVE_LOCATION = "pref_save_location"
        const val PREF_MAX_DURATION = "pref_max_duration"
        const val PREF_HELPER_ENABLED = "pref_helper_enabled"
        const val ACTION_HELPER_START = "com.cloudacr.helper.START_RECORDING"
        const val ACTION_HELPER_STOP = "com.cloudacr.helper.STOP_RECORDING"
    }
}
