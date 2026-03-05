package com.cloudacr.app.utils

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactUtils {

    fun getContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun formatPhoneNumber(number: String): String {
        val digits = number.replace(Regex("[^0-9+]"), "")
        return when {
            digits.length == 10 ->
                "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
            digits.length == 11 && digits.startsWith("1") ->
                "+1 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
            else -> number
        }
    }
}
