package com.yashtandon.smartnotes.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

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

    /**
     * Server-sent events (SSE): the server keeps the connection open and sends
     * `data: {json}` lines, each holding the next piece of the answer.
     * @Streaming stops Retrofit from buffering the whole body in memory.
     */
    @Streaming
    @POST("v1beta/models/{model}:streamGenerateContent?alt=sse")
    suspend fun streamGenerateContent(
        @Path("model") model: String,
        @Body body: GenerateContentRequest,
    ): ResponseBody
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

/**
 * Parses one SSE line. Returns the text chunk for `data:` lines that carry text,
 * or null for blank lines, comments, keep-alives and chunks without text.
 */
fun parseSseLine(line: String, json: Json): String? {
    if (!line.startsWith("data:")) return null
    val payload = line.removePrefix("data:").trim()
    if (payload.isEmpty() || payload == "[DONE]") return null
    val response = json.decodeFromString(GenerateContentResponse.serializer(), payload)
    response.promptFeedback?.blockReason?.let { throw IllegalStateException("Request was blocked: $it") }
    return response.candidates.firstOrNull()?.content?.parts.orEmpty()
        .mapNotNull { it.text }
        .joinToString("")
        .ifEmpty { null }
}
