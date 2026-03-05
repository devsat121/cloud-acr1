package com.cloudacr.app.ui

import android.app.Application
import androidx.lifecycle.*
import com.cloudacr.app.CloudACRApp
import com.cloudacr.app.data.Recording
import com.cloudacr.app.data.RecordingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RecordingRepository =
        (application as CloudACRApp).repository

    private val _searchQuery = MutableStateFlow("")
    private val _showStarredOnly = MutableStateFlow(false)
    private val _isRecording = MutableLiveData(false)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    val isRecording: LiveData<Boolean> = _isRecording
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    val recordings: StateFlow<List<Recording>> = combine(
        _searchQuery.debounce(300),
        _showStarredOnly
    ) { query, starredOnly -> Pair(query, starredOnly) }
        .flatMapLatest { (query, starredOnly) ->
            when {
                query.isNotBlank() -> repository.searchRecordings(query)
                starredOnly -> repository.starredRecordings
                else -> repository.allRecordings
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats = combine(
        repository.totalCount,
        repository.totalSize,
        repository.totalDuration
    ) { count, size, duration ->
        Triple(count, size ?: 0L, duration ?: 0L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(0, 0L, 0L))

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setShowStarredOnly(v: Boolean) { _showStarredOnly.value = v }
    fun setIsRecording(v: Boolean) { _isRecording.value = v }

    fun toggleStarred(recording: Recording) {
        viewModelScope.launch {
            repository.setStarred(recording.id, !recording.isStarred)
        }
    }

    fun deleteRecording(recording: Recording) {
        viewModelScope.launch {
            try { java.io.File(recording.filePath).delete() } catch (e: Exception) { /* ignore */ }
            repository.delete(recording)
        }
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        viewModelScope.launch {
            // Also delete the actual files
            recordings.value.filter { it.id in ids }.forEach {
                try { java.io.File(it.filePath).delete() } catch (e: Exception) { /* ignore */ }
            }
            repository.deleteByIds(ids)
            clearSelection()
        }
    }

    fun toggleSelection(id: Long) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply {
            if (contains(id)) remove(id) else add(id)
        }
    }

    fun selectAll() {
        _selectedIds.value = recordings.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }
}
