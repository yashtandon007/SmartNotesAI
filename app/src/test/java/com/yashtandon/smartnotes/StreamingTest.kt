package com.yashtandon.smartnotes

import com.yashtandon.smartnotes.data.remote.parseSseLine
import com.yashtandon.smartnotes.domain.ai.AiEngine
import com.yashtandon.smartnotes.domain.model.Note
import com.yashtandon.smartnotes.domain.rag.TfIdfRetriever
import com.yashtandon.smartnotes.domain.usecase.AskNotesUseCase
import com.yashtandon.smartnotes.ui.ask.AskViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StreamingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val json = Json { ignoreUnknownKeys = true }
    private val goa = Note(id = 1, title = "Goa trip", content = "Flights booked for 20 December. Hotel near Anjuna.")
    private val ai = mockk<AiEngine>()
    private val useCase = AskNotesUseCase(FakeNotesRepository(listOf(goa)), TfIdfRetriever(), ai)

    // --- SSE parsing ---------------------------------------------------------

    @Test
    fun `parses text from a data line`() {
        val line = """data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Your flights "}]}}]}"""
        assertEquals("Your flights ", parseSseLine(line, json))
    }

    @Test
    fun `ignores blank lines, comments and chunks without text`() {
        assertNull(parseSseLine("", json))
        assertNull(parseSseLine(": keep-alive", json))
        assertNull(parseSseLine("""data: {"candidates":[{"finishReason":"STOP"}]}""", json))
    }

    @Test(expected = IllegalStateException::class)
    fun `blocked prompt raises an error`() {
        parseSseLine("""data: {"promptFeedback":{"blockReason":"SAFETY"}}""", json)
    }

    // --- Use case ------------------------------------------------------------

    @Test
    fun `stream returns sources first and chunks from the engine`() = runTest {
        every { ai.answerStream(any(), any()) } returns flowOf("On ", "20 December.")

        val result = useCase.stream("When are my Goa flights?")

        assertEquals(listOf(goa), result.sources)
        assertEquals(listOf("On ", "20 December."), result.chunks.toList())
    }

    @Test
    fun `stream skips the model when nothing matches`() = runTest {
        val result = useCase.stream("Capital of Peru?")

        assertEquals(listOf(AskNotesUseCase.NO_MATCH_ANSWER), result.chunks.toList())
        assertTrue(result.sources.isEmpty())
    }

    // --- ViewModel -----------------------------------------------------------

    @Test
    fun `chunks are appended to one assistant message`() = runTest {
        every { ai.answerStream(any(), any()) } returns flowOf("Flights are ", "on 20 December ", "[Goa trip].")
        val vm = AskViewModel(useCase)

        vm.onInputChange("When are my Goa flights?")
        vm.send()

        val messages = vm.state.value.messages
        assertEquals(2, messages.size)
        assertEquals("Flights are on 20 December [Goa trip].", messages[1].text)
        assertEquals(listOf(goa), messages[1].sources)
        assertFalse(messages[1].isStreaming)
        assertFalse(vm.state.value.isThinking)
    }

    @Test
    fun `error mid-stream keeps the partial answer and flags it`() = runTest {
        every { ai.answerStream(any(), any()) } returns flow {
            emit("Flights are ")
            throw RuntimeException("timeout")
        }
        val vm = AskViewModel(useCase)

        vm.onInputChange("When are my Goa flights?")
        vm.send()

        val reply = vm.state.value.messages.last()
        assertTrue(reply.isError)
        assertTrue(reply.text.startsWith("Flights are "))
        assertTrue(reply.text.contains("timeout"))
    }
}
