package com.cloudacr.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class HelperBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("CloudACR_Helper", "Boot complete – accessibility service will auto-restart if enabled")
            // Android auto-restarts enabled accessibility services after boot.
            // Nothing extra needed here.
        }
    }
}
