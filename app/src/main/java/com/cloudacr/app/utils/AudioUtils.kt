package com.cloudacr.app.utils

import android.media.AudioManager
import android.content.Context

object AudioUtils {

    /**
     * Checks if a call is currently in progress by inspecting audio mode.
     */
    fun isCallActive(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java)
        return am.mode == AudioManager.MODE_IN_CALL ||
               am.mode == AudioManager.MODE_IN_COMMUNICATION
    }

    /**
     * Checks if the device speaker is on during a call.
     */
    fun isSpeakerOn(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java)
        return am.isSpeakerphoneOn
    }
}
