package com.yashtandon.smartnotes

import com.yashtandon.smartnotes.data.ai.CloudAiEngine
import com.yashtandon.smartnotes.data.ai.HybridAiEngine
import com.yashtandon.smartnotes.domain.ai.OnDeviceStatus
import com.yashtandon.smartnotes.domain.ai.OnDeviceSummarizer
import com.yashtandon.smartnotes.domain.model.AiOutput
import com.yashtandon.smartnotes.domain.model.AiSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HybridAiEngineTest {

    private val onDevice = mockk<OnDeviceSummarizer>()
    private val cloud = mockk<CloudAiEngine>()
    private val engine = HybridAiEngine(onDevice, cloud)
    private val longText = "word ".repeat(120) // > 400 chars

    @Test
    fun `uses on-device model when it is ready`() = runTest {
        coEvery { onDevice.status() } returns OnDeviceStatus.READY
        coEvery { onDevice.summarize(longText) } returns "- local summary"

        val result = engine.summarize(longText)

        assertEquals(AiOutput("- local summary", AiSource.ON_DEVICE), result)
        coVerify(exactly = 0) { cloud.summarize(any()) }
    }

    @Test
    fun `falls back to cloud when on-device is unavailable`() = runTest {
        coEvery { onDevice.status() } returns OnDeviceStatus.UNAVAILABLE
        coEvery { cloud.summarize(longText) } returns AiOutput("- cloud summary", AiSource.CLOUD)

        assertEquals(AiSource.CLOUD, engine.summarize(longText).source)
    }

    @Test
    fun `falls back to cloud when on-device inference fails`() = runTest {
        coEvery { onDevice.status() } returns OnDeviceStatus.READY
        coEvery { onDevice.summarize(any()) } throws IllegalStateException("AICore busy")
        coEvery { cloud.summarize(longText) } returns AiOutput("- cloud summary", AiSource.CLOUD)

        assertEquals(AiSource.CLOUD, engine.summarize(longText).source)
    }

    @Test
    fun `short notes go straight to cloud without checking the device`() = runTest {
        coEvery { cloud.summarize("short note") } returns AiOutput("- s", AiSource.CLOUD)

        engine.summarize("short note")

        coVerify(exactly = 0) { onDevice.status() }
    }
}
