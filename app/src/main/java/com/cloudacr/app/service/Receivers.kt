package com.cloudacr.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cloudacr.app.data.Recording

// BootReceiver - re-arms the app after reboot
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("CloudACR_Boot", "Device booted, CloudACR armed and ready")
            // PhoneStateReceiver is a manifest-declared receiver, auto-reactivates
            // Nothing special needed here unless we have a persistent service
        }
    }
}

// HelperCommandReceiver - receives commands from the helper accessibility app
class HelperCommandReceiver : BroadcastReceiver() {

    companion object {
        const val HELPER_PACKAGE = "com.cloudacr.helper"
        const val ACTION_HELPER_CONNECTED = "com.cloudacr.helper.HELPER_CONNECTED"
        const val ACTION_START = "com.cloudacr.helper.START_RECORDING"
        const val ACTION_STOP = "com.cloudacr.helper.STOP_RECORDING"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        var isHelperConnected = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Verify sender is our helper app
        if (intent.getStringExtra("sender_package") != HELPER_PACKAGE) {
            Log.w("CloudACR_Helper", "Rejected command from unknown sender")
            return
        }

        when (intent.action) {
            ACTION_HELPER_CONNECTED -> {
                isHelperConnected = true
                Log.d("CloudACR_Helper", "Helper app connected!")
                // Notify UI
                context.sendBroadcast(Intent("com.cloudacr.HELPER_STATUS_CHANGED").apply {
                    putExtra("connected", true)
                    `package` = context.packageName
                })
            }
            ACTION_START -> {
                val number = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
                val typeStr = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "UNKNOWN"
                val callType = try {
                    Recording.CallType.valueOf(typeStr)
                } catch (e: Exception) {
                    Recording.CallType.UNKNOWN
                }
                Log.d("CloudACR_Helper", "Helper triggered START recording for $number")
                CallRecordingService.startRecording(context, number, callType)
            }
            ACTION_STOP -> {
                Log.d("CloudACR_Helper", "Helper triggered STOP recording")
                CallRecordingService.stopRecording(context)
            }
        }
    }
}
