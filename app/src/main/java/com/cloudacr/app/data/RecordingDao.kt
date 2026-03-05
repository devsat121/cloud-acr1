package com.cloudacr.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE isStarred = 1 ORDER BY timestamp DESC")
    fun getStarredRecordings(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE phoneNumber LIKE '%' || :query || '%' OR contactName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchRecordings(query: String): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): Recording?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: Recording): Long

    @Update
    suspend fun updateRecording(recording: Recording)

    @Delete
    suspend fun deleteRecording(recording: Recording)

    @Query("DELETE FROM recordings WHERE id IN (:ids)")
    suspend fun deleteRecordingsByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM recordings")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT SUM(fileSizeBytes) FROM recordings")
    fun getTotalSize(): Flow<Long?>

    @Query("SELECT SUM(durationMs) FROM recordings")
    fun getTotalDuration(): Flow<Long?>

    @Query("UPDATE recordings SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("UPDATE recordings SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String?)

    @Query("UPDATE recordings SET transcription = :text WHERE id = :id")
    suspend fun updateTranscription(id: Long, text: String?)
}
