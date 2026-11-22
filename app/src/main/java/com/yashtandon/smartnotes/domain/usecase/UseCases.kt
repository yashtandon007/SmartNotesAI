package com.yashtandon.smartnotes.domain.usecase

import com.yashtandon.smartnotes.domain.ai.AiEngine
import com.yashtandon.smartnotes.domain.model.AiOutput
import com.yashtandon.smartnotes.domain.model.NotesAnswer
import com.yashtandon.smartnotes.domain.model.StreamedNotesAnswer
import kotlinx.coroutines.flow.flowOf
import com.yashtandon.smartnotes.domain.rag.TfIdfRetriever
import com.yashtandon.smartnotes.domain.repository.NotesRepository
import javax.inject.Inject

/** Summarise one note's text. The engine decides on-device vs cloud. */
class SummarizeNoteUseCase @Inject constructor(private val ai: AiEngine) {
    suspend operator fun invoke(content: String): AiOutput {
        require(content.isNotBlank()) { "Write something before summarising." }
        return ai.summarize(content)
    }
}

/** Suggest topic tags for a note, cleaned and de-duplicated. */
class SuggestTagsUseCase @Inject constructor(private val ai: AiEngine) {
    suspend operator fun invoke(content: String): List<String> {
        require(content.isNotBlank()) { "Write something before generating tags." }
        return ai.suggestTags(content)
            .map { it.trim().lowercase().removePrefix("#") }
            .filter { it.isNotEmpty() && it.length <= 30 }
            .distinct()
            .take(5)
    }
}

/**
 * "Ask your notes" — retrieval-augmented generation:
 * 1. Retrieve: pick the most relevant notes locally (TF-IDF).
 * 2. Augment: put only those notes into the prompt.
 * 3. Generate: let the LLM answer from that context.
 * If nothing relevant is found we skip the LLM call entirely (saves cost, avoids made-up answers).
 */
class AskNotesUseCase @Inject constructor(
    private val repository: NotesRepository,
    private val retriever: TfIdfRetriever,
    private val ai: AiEngine,
) {
    suspend operator fun invoke(question: String, maxNotes: Int = 3): NotesAnswer {
        require(question.isNotBlank()) { "Ask a question first." }
        val relevant = retriever.topK(question, repository.getAllNotes(), k = maxNotes).map { it.note }
        if (relevant.isEmpty()) {
            return NotesAnswer(NO_MATCH_ANSWER, emptyList())
        }
        return NotesAnswer(ai.answer(question, relevant), relevant)
    }

    /**
     * Streaming RAG: retrieval happens first (so sources can be shown right away),
     * then the answer streams in token by token.
     */
    suspend fun stream(question: String, maxNotes: Int = 3): StreamedNotesAnswer {
        require(question.isNotBlank()) { "Ask a question first." }
        val relevant = retriever.topK(question, repository.getAllNotes(), k = maxNotes).map { it.note }
        if (relevant.isEmpty()) {
            return StreamedNotesAnswer(flowOf(NO_MATCH_ANSWER), emptyList())
        }
        return StreamedNotesAnswer(ai.answerStream(question, relevant), relevant)
    }

    companion object {
        const val NO_MATCH_ANSWER = "I couldn't find anything in your notes about that."
    }
}
