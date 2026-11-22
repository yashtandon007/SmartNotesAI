package com.yashtandon.smartnotes.domain.ai

import com.yashtandon.smartnotes.domain.model.AiOutput
import com.yashtandon.smartnotes.domain.model.Note

/**
 * Everything the app needs from "an AI". The UI and use cases only know this interface,
 * so the implementation can be cloud, on-device, or a mix (see HybridAiEngine) — and
 * tests can swap in a fake.
 */
interface AiEngine {
    suspend fun summarize(text: String): AiOutput
    suspend fun suggestTags(text: String): List<String>
    suspend fun answer(question: String, context: List<Note>): String
}

/** On-device summarisation (Gemini Nano through ML Kit GenAI / Android AICore). */
interface OnDeviceSummarizer {
    suspend fun status(): OnDeviceStatus
    suspend fun summarize(text: String): String
}

enum class OnDeviceStatus {
    /** Model is on the device and ready. */
    READY,
    /** Device supports it but the model still needs downloading. */
    DOWNLOADABLE,
    /** Device or OS doesn't support on-device GenAI. */
    UNAVAILABLE,
}
