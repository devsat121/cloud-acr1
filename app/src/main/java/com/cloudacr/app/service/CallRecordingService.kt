package com.cloudacr.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cloudacr.app.CloudACRApp
import com.cloudacr.app.R
import com.cloudacr.app.data.Recording
import com.cloudacr.app.ui.MainActivity
import com.cloudacr.app.utils.AudioUtils
import com.cloudacr.app.utils.ContactUtils
import com.cloudacr.app.utils.StorageUtils
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CallRecordingService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    private var currentFile: File? = null
    private var recordingStartTime: Long = 0L
    private var currentPhoneNumber: String = ""
    private var currentCallType: Recording.CallType = Recording.CallType.UNKNOWN

    private val NOTIFICATION_ID = 1001
    private val TAG = "CloudACR_Service"

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                currentPhoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
                currentCallType = intent.getSerializableExtra(EXTRA_CALL_TYPE) as? Recording.CallType
                    ?: Recording.CallType.UNKNOWN
                startRecording()
            }
            ACTION_STOP_RECORDING -> {
                stopRecordingAndSave()
            }
            ACTION_DISCARD_RECORDING -> {
                discardRecording()
            }
        }
        return START_STICKY
    }

    private fun startRecording() {
        try {
            val outputFile = StorageUtils.createRecordingFile(this, currentPhoneNumber)
            currentFile = outputFile
            recordingStartTime = System.currentTimeMillis()

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                // Use VOICE_COMMUNICATION for best call audio on modern Android
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setAudioChannels(2)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            Log.d(TAG, "Recording started: ${outputFile.name}")
            showRecordingNotification()
            broadcastRecordingState(true)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            // Fallback to VOICE_CALL source
            startRecordingFallback()
        }
    }

    private fun startRecordingFallback() {
        try {
            val outputFile = currentFile ?: StorageUtils.createRecordingFile(this, currentPhoneNumber)
            currentFile = outputFile

            mediaRecorder?.release()
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            Log.d(TAG, "Recording started (fallback): ${outputFile.name}")
            showRecordingNotification()
            broadcastRecordingState(true)

        } catch (e: Exception) {
            Log.e(TAG, "Fallback recording also failed", e)
            stopSelf()
        }
    }

    private fun stopRecordingAndSave() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            val file = currentFile ?: run {
                stopSelf()
                return
            }

            if (!file.exists() || file.length() < 1024) {
                file.delete()
                stopSelf()
                return
            }

            val duration = System.currentTimeMillis() - recordingStartTime
            val app = application as CloudACRApp
            val contactName = ContactUtils.getContactName(this, currentPhoneNumber)

            serviceScope.launch {
                val recording = Recording(
                    phoneNumber = currentPhoneNumber,
                    contactName = contactName,
                    filePath = file.absolutePath,
                    fileName = file.name,
                    durationMs = duration,
                    fileSizeBytes = file.length(),
                    callType = currentCallType,
                    timestamp = recordingStartTime
                )
                app.repository.insert(recording)
                Log.d(TAG, "Recording saved: ${file.name}, size: ${file.length()} bytes")
                showSavedNotification(contactName ?: currentPhoneNumber, duration)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        } finally {
            broadcastRecordingState(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun discardRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            currentFile?.delete()
            Log.d(TAG, "Recording discarded")
        } catch (e: Exception) {
            Log.e(TAG, "Error discarding recording", e)
        } finally {
            broadcastRecordingState(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun showRecordingNotification() {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, CallRecordingService::class.java).apply { action = ACTION_STOP_RECORDING },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val discardIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CallRecordingService::class.java).apply { action = ACTION_DISCARD_RECORDING },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayName = ContactUtils.getContactName(this, currentPhoneNumber)
            ?: currentPhoneNumber.ifBlank { "Unknown Caller" }

        val notification = NotificationCompat.Builder(this, CloudACRApp.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Recording call…")
            .setContentText(displayName)
            .setOngoing(true)
            .setUsesChronometer(true)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .addAction(R.drawable.ic_delete, "Discard", discardIntent)
            .setContentIntent(openIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun showSavedNotification(name: String, durationMs: Long) {
        val minutes = durationMs / 1000 / 60
        val seconds = (durationMs / 1000) % 60
        val durationStr = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"

        val notification = NotificationCompat.Builder(this, CloudACRApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("Call recorded")
            .setContentText("$name · $durationStr")
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun broadcastRecordingState(isRecording: Boolean) {
        sendBroadcast(Intent(ACTION_RECORDING_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_RECORDING, isRecording)
            `package` = packageName
        })
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CloudACR::RecordingLock")
        wakeLock?.acquire(10 * 60 * 1000L) // Max 10 minutes
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        serviceScope.cancel()
        mediaRecorder?.release()
        mediaRecorder = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_RECORDING = "com.cloudacr.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.cloudacr.STOP_RECORDING"
        const val ACTION_DISCARD_RECORDING = "com.cloudacr.DISCARD_RECORDING"
        const val ACTION_RECORDING_STATE_CHANGED = "com.cloudacr.RECORDING_STATE_CHANGED"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_IS_RECORDING = "extra_is_recording"

        fun startRecording(context: Context, phoneNumber: String, callType: Recording.CallType) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_START_RECORDING
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_CALL_TYPE, callType)
            }
            context.startForegroundService(intent)
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }
    }
}
