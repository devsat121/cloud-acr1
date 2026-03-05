package com.cloudacr.app.data

import kotlinx.coroutines.flow.Flow

class RecordingRepository(private val dao: RecordingDao) {

    val allRecordings: Flow<List<Recording>> = dao.getAllRecordings()
    val starredRecordings: Flow<List<Recording>> = dao.getStarredRecordings()
    val totalCount: Flow<Int> = dao.getTotalCount()
    val totalSize: Flow<Long?> = dao.getTotalSize()
    val totalDuration: Flow<Long?> = dao.getTotalDuration()

    fun searchRecordings(query: String): Flow<List<Recording>> =
        dao.searchRecordings(query)

    suspend fun getById(id: Long): Recording? = dao.getRecordingById(id)

    suspend fun insert(recording: Recording): Long = dao.insertRecording(recording)

    suspend fun update(recording: Recording) = dao.updateRecording(recording)

    suspend fun delete(recording: Recording) = dao.deleteRecording(recording)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteRecordingsByIds(ids)

    suspend fun setStarred(id: Long, starred: Boolean) = dao.setStarred(id, starred)

    suspend fun updateNotes(id: Long, notes: String?) = dao.updateNotes(id, notes)

    suspend fun updateTranscription(id: Long, text: String?) =
        dao.updateTranscription(id, text)
}
