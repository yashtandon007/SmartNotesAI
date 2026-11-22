package com.yashtandon.smartnotes.data.ai

import com.yashtandon.smartnotes.data.remote.Content
import com.yashtandon.smartnotes.data.remote.GeminiApi
import com.yashtandon.smartnotes.data.remote.GenerateContentRequest
import com.yashtandon.smartnotes.data.remote.GenerationConfig
import com.yashtandon.smartnotes.data.remote.Part
import com.yashtandon.smartnotes.data.remote.Schema
import com.yashtandon.smartnotes.data.remote.parseSseLine
import com.yashtandon.smartnotes.data.remote.text
import com.yashtandon.smartnotes.di.GeminiModel
import com.yashtandon.smartnotes.domain.ai.AiEngine
import com.yashtandon.smartnotes.domain.ai.Prompts
import com.yashtandon.smartnotes.domain.model.AiOutput
import com.yashtandon.smartnotes.domain.model.AiSource
import com.yashtandon.smartnotes.domain.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** All AI features through the cloud Gemini model. */
@Singleton
class CloudAiEngine @Inject constructor(
    private val api: GeminiApi,
    @GeminiModel private val model: String,
    private val json: Json,
) : AiEngine {

    override suspend fun summarize(text: String): AiOutput =
        AiOutput(generate(Prompts.SUMMARY_SYSTEM, text), AiSource.CLOUD)

    override suspend fun suggestTags(text: String): List<String> {
        val raw = generate(
            system = Prompts.TAGS_SYSTEM,
            prompt = text,
            config = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = Schema(type = "ARRAY", items = Schema(type = "STRING")),
            ),
        )
        return runCatching { json.decodeFromString<List<String>>(raw) }
            // Fallback in case a model ignores the schema: split on commas/newlines.
            .getOrElse { raw.trim('[', ']').split(',', '\n').map { it.trim().trim('"') } }
    }

    override suspend fun answer(question: String, context: List<Note>): String =
        generate(Prompts.ANSWER_SYSTEM, Prompts.answerPrompt(question, context))

    override fun answerStream(question: String, context: List<Note>): Flow<String> = flow {
        val body = api.streamGenerateContent(model, request(Prompts.ANSWER_SYSTEM, Prompts.answerPrompt(question, context)))
        // use {} closes the HTTP connection even if the collector cancels mid-stream.
        body.use {
            val source = it.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                parseSseLine(line, json)?.let { chunk -> emit(chunk) }
            }
        }
    }.flowOn(Dispatchers.IO) // blocking socket reads must not run on the main thread

    private suspend fun generate(system: String, prompt: String, config: GenerationConfig? = null): String {
        return api.generateContent(model, request(system, prompt, config)).text()
    }

    private fun request(system: String, prompt: String, config: GenerationConfig? = null) = GenerateContentRequest(
        systemInstruction = Content(parts = listOf(Part(system))),
        contents = listOf(Content(role = "user", parts = listOf(Part(prompt)))),
        generationConfig = config,
    )
}
