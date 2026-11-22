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
    /** True while the answer is still arriving. */
    val isStreaming: Boolean = false,
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
            try {
                val streamed = askNotes.stream(question)
                // Show an empty assistant bubble (with its sources) straight away...
                appendMessage(ChatMessage("", fromUser = false, sources = streamed.sources, isStreaming = true))
                // ...then grow it chunk by chunk as tokens arrive.
                streamed.chunks.collect { chunk -> updateLastMessage { it.copy(text = it.text + chunk) } }
                updateLastMessage { it.copy(isStreaming = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = e.message ?: "Couldn't get an answer. Check your connection."
                val last = _state.value.messages.lastOrNull()
                if (last != null && !last.fromUser && last.isStreaming) {
                    // Keep any partial answer and flag the failure on the same bubble.
                    updateLastMessage {
                        it.copy(text = if (it.text.isBlank()) error else it.text + "\n\n($error)", isError = true, isStreaming = false)
                    }
                } else {
                    appendMessage(ChatMessage(error, fromUser = false, isError = true))
                }
            } finally {
                _state.update { it.copy(isThinking = false) }
            }
        }
    }

    private fun appendMessage(message: ChatMessage) =
        _state.update { it.copy(messages = it.messages + message) }

    private fun updateLastMessage(transform: (ChatMessage) -> ChatMessage) = _state.update { state ->
        val messages = state.messages
        if (messages.isEmpty()) state else state.copy(messages = messages.dropLast(1) + transform(messages.last()))
    }
}
