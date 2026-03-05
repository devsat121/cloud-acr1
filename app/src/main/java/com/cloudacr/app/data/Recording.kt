package com.cloudacr.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val contactName: String?,
    val filePath: String,
    val fileName: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val callType: CallType, // INCOMING, OUTGOING, UNKNOWN
    val timestamp: Long,
    val isStarred: Boolean = false,
    val transcription: String? = null,
    val notes: String? = null
) {
    enum class CallType { INCOMING, OUTGOING, UNKNOWN }

    val formattedDuration: String
        get() {
            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / 1000 / 60) % 60
            val hours = durationMs / 1000 / 3600
            return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
            else "%d:%02d".format(minutes, seconds)
        }

    val formattedSize: String
        get() {
            return when {
                fileSizeBytes > 1_048_576 -> "%.1f MB".format(fileSizeBytes / 1_048_576.0)
                fileSizeBytes > 1024 -> "%.1f KB".format(fileSizeBytes / 1024.0)
                else -> "$fileSizeBytes B"
            }
        }

    val displayName: String
        get() = contactName?.takeIf { it.isNotBlank() } ?: phoneNumber.ifBlank { "Unknown" }
}
