package com.yashtandon.smartnotes.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Gemini REST API (generateContent), called directly with Retrofit.
 * Docs: https://ai.google.dev/api/generate-content
 *
 * The API key is added by an OkHttp interceptor (see NetworkModule), never in this interface.
 */
interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Body body: GenerateContentRequest,
    ): GenerateContentResponse
}

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null,
)

@Serializable
data class Content(
    val role: String? = null,
    val parts: List<Part>,
)

@Serializable
data class Part(val text: String? = null)

/**
 * Structured output: with responseMimeType = "application/json" and a responseSchema,
 * the model is constrained to return JSON matching the schema — no fragile text parsing.
 */
@Serializable
data class GenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: Schema? = null,
)

@Serializable
data class Schema(
    val type: String,
    val items: Schema? = null,
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList(),
    val promptFeedback: PromptFeedback? = null,
)

@Serializable
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
)

@Serializable
data class PromptFeedback(val blockReason: String? = null)

/** Joins all text parts of the first candidate. Throws a readable error if there's none. */
fun GenerateContentResponse.text(): String {
    promptFeedback?.blockReason?.let { throw IllegalStateException("Request was blocked: $it") }
    val text = candidates.firstOrNull()?.content?.parts.orEmpty()
        .mapNotNull { it.text }
        .joinToString("")
        .trim()
    check(text.isNotEmpty()) { "The model returned an empty response." }
    return text
}
