package com.yashtandon.smartnotes.domain.rag

import com.yashtandon.smartnotes.domain.model.Note
import com.yashtandon.smartnotes.domain.model.ScoredNote
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * The "R" in RAG (retrieval-augmented generation).
 *
 * Before asking the LLM a question, we find the few notes most related to it and send
 * only those. This keeps the prompt small (cheaper, faster) and grounds the answer in the
 * user's own data.
 *
 * Scoring uses TF-IDF + cosine similarity, which runs fully on-device with no extra model:
 *  - TF  (term frequency): how often a word appears in a note.
 *  - IDF (inverse document frequency): rare words across all notes count more than common ones.
 *  - Cosine similarity: angle between the question vector and each note vector (1 = same direction).
 *
 * A production upgrade is to swap this for embedding vectors (semantic search) — the
 * interface stays the same.
 */
class TfIdfRetriever @Inject constructor() {

    fun topK(query: String, notes: List<Note>, k: Int = 3, minScore: Double = 0.05): List<ScoredNote> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty() || notes.isEmpty()) return emptyList()

        val docs = notes.map { tokenize(it.searchableText()) }
        val idf = inverseDocumentFrequency(docs)
        val queryVector = vectorize(queryTokens, idf)

        return notes.indices
            .map { i -> ScoredNote(notes[i], cosine(queryVector, vectorize(docs[i], idf))) }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(k)
    }

    internal fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(NON_WORD)
            .filter { it.length > 1 && it !in STOP_WORDS }

    private fun inverseDocumentFrequency(docs: List<List<String>>): Map<String, Double> {
        val n = docs.size
        val documentFrequency = HashMap<String, Int>()
        docs.forEach { tokens -> tokens.toSet().forEach { documentFrequency.merge(it, 1, Int::plus) } }
        // Smoothed IDF so words that appear everywhere still get a small positive weight.
        return documentFrequency.mapValues { (_, df) -> ln((n + 1.0) / (df + 1.0)) + 1.0 }
    }

    private fun vectorize(tokens: List<String>, idf: Map<String, Double>): Map<String, Double> {
        if (tokens.isEmpty()) return emptyMap()
        val counts = tokens.groupingBy { it }.eachCount()
        return counts.mapValues { (term, count) -> (count.toDouble() / tokens.size) * (idf[term] ?: 0.0) }
    }

    private fun cosine(a: Map<String, Double>, b: Map<String, Double>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val dot = a.entries.sumOf { (term, weight) -> weight * (b[term] ?: 0.0) }
        val normA = sqrt(a.values.sumOf { it * it })
        val normB = sqrt(b.values.sumOf { it * it })
        return if (normA == 0.0 || normB == 0.0) 0.0 else dot / (normA * normB)
    }

    private fun Note.searchableText() = "$title $title ${tags.joinToString(" ")} $content"

    private companion object {
        val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
        val STOP_WORDS = setOf(
            "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "her", "was",
            "one", "our", "out", "has", "have", "had", "his", "how", "its", "may", "who", "did",
            "what", "when", "where", "which", "why", "with", "this", "that", "from", "they",
            "will", "would", "there", "their", "about", "into", "your", "is", "it", "of", "to",
            "in", "on", "at", "as", "an", "be", "by", "or", "do", "me", "my", "we", "so", "if",
            "a", "i",
        )
    }
}
