package com.yashtandon.smartnotes.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yashtandon.smartnotes.domain.model.AiSource
import com.yashtandon.smartnotes.domain.model.Note
import com.yashtandon.smartnotes.domain.repository.NotesRepository
import com.yashtandon.smartnotes.domain.usecase.SuggestTagsUseCase
import com.yashtandon.smartnotes.domain.usecase.SummarizeNoteUseCase
import com.yashtandon.smartnotes.ui.navigation.NEW_NOTE_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AiTask { SUMMARY, TAGS }

/** Single immutable UI state object (MVI-style): the screen renders exactly what's in here. */
data class EditorUiState(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val summary: String? = null,
    val summarySource: AiSource? = null,
    val tags: List<String> = emptyList(),
    val runningTask: AiTask? = null,
    val message: String? = null,
    /** True once the screen should close (after save-on-back or delete). */
    val isClosed: Boolean = false,
) {
    val isNew get() = id == 0L
    val canUseAi get() = content.isNotBlank() && runningTask == null
}

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NotesRepository,
    private val summarizeNote: SummarizeNoteUseCase,
    private val suggestTagsFor: SuggestTagsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        // Type-safe nav stores route fields in SavedStateHandle under their property name.
        val noteId = savedStateHandle.get<Long>("noteId") ?: NEW_NOTE_ID
        if (noteId != NEW_NOTE_ID) load(noteId)
    }

    private fun load(id: Long) = viewModelScope.launch {
        repository.getNote(id)?.let { note ->
            _state.update {
                it.copy(id = note.id, title = note.title, content = note.content, summary = note.summary, tags = note.tags)
            }
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value) }

    fun onContentChange(value: String) = _state.update { it.copy(content = value) }

    fun onRemoveTag(tag: String) = _state.update { it.copy(tags = it.tags - tag) }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun save() = viewModelScope.launch { persist(showMessage = true) }

    fun summarize() = runAi(AiTask.SUMMARY) {
        val result = summarizeNote(_state.value.content)
        _state.update { it.copy(summary = result.text, summarySource = result.source) }
    }

    fun generateTags() = runAi(AiTask.TAGS) {
        val tags = suggestTagsFor(_state.value.content)
        _state.update { it.copy(tags = (it.tags + tags).distinct()) }
    }

    fun delete() = viewModelScope.launch {
        val id = _state.value.id
        if (id != 0L) repository.delete(id)
        _state.update { it.copy(isClosed = true) }
    }

    /**
     * Save, then signal the UI to close. Saving happens before navigation so the
     * write isn't cancelled when the ViewModel is cleared.
     */
    fun saveAndClose() = viewModelScope.launch {
        persist(showMessage = false)
        _state.update { it.copy(isClosed = true) }
    }

    /** Runs one AI task at a time, shows progress, saves the result, and turns errors into a friendly message. */
    private fun runAi(task: AiTask, block: suspend () -> Unit) {
        if (!_state.value.canUseAi) return
        viewModelScope.launch {
            _state.update { it.copy(runningTask = task, message = null) }
            try {
                block()
                persist(showMessage = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "Something went wrong. Try again.") }
            } finally {
                _state.update { it.copy(runningTask = null) }
            }
        }
    }

    private suspend fun persist(showMessage: Boolean) {
        val s = _state.value
        if (s.title.isBlank() && s.content.isBlank()) return
        val id = repository.upsert(
            Note(id = s.id, title = s.title.trim(), content = s.content, summary = s.summary, tags = s.tags),
        )
        _state.update { it.copy(id = id, message = if (showMessage) "Saved" else it.message) }
    }
}
