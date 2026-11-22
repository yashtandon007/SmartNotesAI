package com.yashtandon.smartnotes.data.ai

import android.util.Log
import com.yashtandon.smartnotes.domain.ai.AiEngine
import com.yashtandon.smartnotes.domain.ai.OnDeviceStatus
import com.yashtandon.smartnotes.domain.ai.OnDeviceSummarizer
import com.yashtandon.smartnotes.domain.model.AiOutput
import com.yashtandon.smartnotes.domain.model.AiSource
import com.yashtandon.smartnotes.domain.model.Note
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid strategy — the key design idea of the app:
 *  - Summaries: try Gemini Nano ON-DEVICE first (private, free, works offline).
 *    Fall back to the cloud if the device doesn't support it, the model isn't
 *    downloaded yet, the note is too short, or inference fails.
 *  - Tags and Q&A: use the CLOUD model (needs structured output / more reasoning).
 *
 * Callers only see AiEngine, so they don't know or care which path ran — but the result
 * carries an AiSource so the UI can show "On-device" vs "Cloud".
 */
@Singleton
class HybridAiEngine @Inject constructor(
    private val onDevice: OnDeviceSummarizer,
    private val cloud: CloudAiEngine,
) : AiEngine {

    override suspend fun summarize(text: String): AiOutput {
        if (text.length >= MIN_ON_DEVICE_CHARS && onDevice.status() == OnDeviceStatus.READY) {
            try {
                return AiOutput(onDevice.summarize(text), AiSource.ON_DEVICE)
            } catch (e: CancellationException) {
                throw e // never swallow coroutine cancellation
            } catch (e: Exception) {
                Log.w(TAG, "On-device summary failed, falling back to cloud", e)
            }
        }
        return cloud.summarize(text)
    }

    override suspend fun suggestTags(text: String): List<String> = cloud.suggestTags(text)

    override suspend fun answer(question: String, context: List<Note>): String =
        cloud.answer(question, context)

    private companion object {
        const val TAG = "HybridAiEngine"
        /** ML Kit's "article" input type expects more than 400 characters. */
        const val MIN_ON_DEVICE_CHARS = 400
    }
}
