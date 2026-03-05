package com.cloudacr.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import android.util.Log
import androidx.preference.PreferenceManager
import com.cloudacr.app.CloudACRApp
import com.cloudacr.app.data.Recording

class PhoneStateReceiver : BroadcastReceiver() {

    private val TAG = "CloudACR_PhoneState"

    companion object {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var callStartTime = 0L
        private var savedPhoneNumber: String = ""
        private var isOutgoing = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(CloudACRApp.PREF_AUTO_RECORD, true)) return

        when (intent.action) {
            Intent.ACTION_NEW_OUTGOING_CALL -> {
                savedPhoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER) ?: ""
                isOutgoing = true
                Log.d(TAG, "Outgoing call to: $savedPhoneNumber")
            }
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        if (incomingNumber.isNotBlank()) {
                            savedPhoneNumber = incomingNumber
                        }
                        isOutgoing = false
                        Log.d(TAG, "Incoming call from: $savedPhoneNumber")
                    }
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        val wasIdle = lastState == TelephonyManager.CALL_STATE_IDLE
                        lastState = TelephonyManager.CALL_STATE_OFFHOOK
                        callStartTime = System.currentTimeMillis()

                        val callType = if (isOutgoing) Recording.CallType.OUTGOING
                        else Recording.CallType.INCOMING

                        val shouldRecord = when (callType) {
                            Recording.CallType.INCOMING ->
                                prefs.getBoolean(CloudACRApp.PREF_RECORD_INCOMING, true)
                            Recording.CallType.OUTGOING ->
                                prefs.getBoolean(CloudACRApp.PREF_RECORD_OUTGOING, true)
                            else -> true
                        }

                        if (shouldRecord) {
                            Log.d(TAG, "Call answered, starting recording ($callType)")
                            CallRecordingService.startRecording(context, savedPhoneNumber, callType)
                        }
                    }
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        if (lastState != TelephonyManager.CALL_STATE_IDLE) {
                            Log.d(TAG, "Call ended, stopping recording")
                            CallRecordingService.stopRecording(context)
                        }
                        lastState = TelephonyManager.CALL_STATE_IDLE
                        isOutgoing = false
                    }
                }
            }
        }
    }
}
