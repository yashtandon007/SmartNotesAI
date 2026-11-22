package com.yashtandon.smartnotes.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yashtandon.smartnotes.domain.model.Note
import com.yashtandon.smartnotes.domain.usecase.AskNotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    val sources: List<Note> = emptyList(),
    val isError: Boolean = false,
)

data class AskUiState(
    val input: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val isThinking: Boolean = false,
) {
    val canSend get() = input.isNotBlank() && !isThinking
}

@HiltViewModel
class AskViewModel @Inject constructor(
    private val askNotes: AskNotesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AskUiState())
    val state: StateFlow<AskUiState> = _state.asStateFlow()

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    fun send() {
        val question = _state.value.input.trim()
        if (!_state.value.canSend) return
        _state.update {
            it.copy(input = "", isThinking = true, messages = it.messages + ChatMessage(question, fromUser = true))
        }
        viewModelScope.launch {
            val reply = try {
                val result = askNotes(question)
                ChatMessage(result.answer, fromUser = false, sources = result.sources)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ChatMessage(e.message ?: "Couldn't get an answer. Check your connection.", fromUser = false, isError = true)
            }
            _state.update { it.copy(isThinking = false, messages = it.messages + reply) }
        }
    }
}
