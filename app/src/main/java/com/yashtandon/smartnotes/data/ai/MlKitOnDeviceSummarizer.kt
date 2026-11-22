package com.yashtandon.smartnotes.data.ai

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import com.yashtandon.smartnotes.domain.ai.OnDeviceStatus
import com.yashtandon.smartnotes.domain.ai.OnDeviceSummarizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.guava.await
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini Nano on the phone via ML Kit's GenAI Summarization API.
 * Docs: https://developers.google.com/ml-kit/genai/summarization/android
 *
 * - Runs through Android AICore, only on supported devices (e.g. recent Pixel / Galaxy flagships).
 * - Private and free: text never leaves the device.
 * - Supports English, Japanese and Korean; input must be under ~4,000 tokens, and
 *   "article" input should be over 400 characters.
 *
 * ML Kit returns Guava ListenableFutures; kotlinx-coroutines-guava's await() turns them
 * into suspend calls so they fit the rest of the coroutine-based code.
 */
@Singleton
class MlKitOnDeviceSummarizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : OnDeviceSummarizer {

    private val summarizer: Summarizer by lazy {
        val options = SummarizerOptions.builder(context)
            .setInputType(SummarizerOptions.InputType.ARTICLE)
            .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            .build()
        Summarization.getClient(options)
    }

    private val downloadStarted = AtomicBoolean(false)

    override suspend fun status(): OnDeviceStatus = runCatching {
        when (summarizer.checkFeatureStatus().await()) {
            FeatureStatus.AVAILABLE -> OnDeviceStatus.READY
            FeatureStatus.DOWNLOADABLE -> {
                startDownloadOnce()
                OnDeviceStatus.DOWNLOADABLE
            }
            FeatureStatus.DOWNLOADING -> OnDeviceStatus.DOWNLOADABLE
            else -> OnDeviceStatus.UNAVAILABLE
        }
    }.getOrElse {
        Log.w(TAG, "On-device GenAI not available", it)
        OnDeviceStatus.UNAVAILABLE
    }

    override suspend fun summarize(text: String): String {
        val request = SummarizationRequest.builder(text).build()
        return summarizer.runInference(request).await().summary
    }

    /** Kicks off the one-time model download in the background; the app uses the cloud meanwhile. */
    private fun startDownloadOnce() {
        if (!downloadStarted.compareAndSet(false, true)) return
        summarizer.downloadFeature(object : DownloadCallback {
            override fun onDownloadStarted(bytesToDownload: Long) {
                Log.i(TAG, "Downloading on-device model: $bytesToDownload bytes")
            }

            override fun onDownloadProgress(totalBytesDownloaded: Long) {}

            override fun onDownloadCompleted() {
                Log.i(TAG, "On-device model ready")
            }

            override fun onDownloadFailed(e: GenAiException) {
                Log.w(TAG, "On-device model download failed", e)
                downloadStarted.set(false)
            }
        })
    }

    private companion object {
        const val TAG = "OnDeviceSummarizer"
    }
}
