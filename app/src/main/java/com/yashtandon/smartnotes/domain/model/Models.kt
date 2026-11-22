package com.yashtandon.smartnotes.domain.model

/** A note as the rest of the app sees it. Independent of Room and of the network. */
data class Note(
    val id: Long = 0,
    val title: String,
    val content: String,
    val summary: String? = null,
    val tags: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Where an AI result was produced: privately on the phone, or by the cloud model. */
enum class AiSource { ON_DEVICE, CLOUD }

data class AiOutput(val text: String, val source: AiSource)

/** A note returned by the retriever together with its relevance score (0..1). */
data class ScoredNote(val note: Note, val score: Double)

/** Final answer for "Ask your notes", including which notes it was based on. */
data class NotesAnswer(val answer: String, val sources: List<Note>)

/** Streaming version: the sources are known up front, the text arrives chunk by chunk. */
data class StreamedNotesAnswer(val chunks: kotlinx.coroutines.flow.Flow<String>, val sources: List<Note>)
