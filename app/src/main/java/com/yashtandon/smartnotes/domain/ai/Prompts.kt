package com.yashtandon.smartnotes.domain.ai

import com.yashtandon.smartnotes.domain.model.Note

/**
 * All prompt text lives here, in one place, so it's easy to tune and to unit-test.
 * Note: Gemini 3.x ignores temperature/top_p/top_k, so output is shaped with
 * system instructions instead of sampling parameters.
 */
object Prompts {

    const val SUMMARY_SYSTEM =
        "You summarise a person's private notes. Reply with 2 to 4 short bullet points, " +
            "each starting with '- '. Keep the author's facts, names and dates. No preamble."

    const val TAGS_SYSTEM =
        "You label personal notes. Return 1 to 5 short, lowercase topic tags " +
            "(one or two words each) as a JSON array of strings. Nothing else."

    const val ANSWER_SYSTEM =
        "You answer questions using ONLY the notes provided. If the notes do not contain " +
            "the answer, say you couldn't find it in the notes. Be concise. When you use a " +
            "note, cite its title in square brackets, e.g. [Trip plan]."

    /** Keeps each note's share of the prompt bounded so a huge note can't blow the context. */
    const val MAX_CHARS_PER_NOTE = 2_000

    fun answerPrompt(question: String, notes: List<Note>): String = buildString {
        appendLine("Notes:")
        notes.forEach { note ->
            appendLine()
            appendLine("### [${note.title.ifBlank { "Untitled" }}]")
            appendLine(note.content.take(MAX_CHARS_PER_NOTE))
        }
        appendLine()
        append("Question: ").append(question.trim())
    }
}
