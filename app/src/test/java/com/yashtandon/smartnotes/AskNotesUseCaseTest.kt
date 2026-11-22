package com.yashtandon.smartnotes

import com.yashtandon.smartnotes.domain.ai.AiEngine
import com.yashtandon.smartnotes.domain.model.Note
import com.yashtandon.smartnotes.domain.rag.TfIdfRetriever
import com.yashtandon.smartnotes.domain.usecase.AskNotesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AskNotesUseCaseTest {

    private val goa = Note(id = 1, title = "Goa trip", content = "Flights booked for 20 December. Hotel near Anjuna.")
    private val gym = Note(id = 2, title = "Gym plan", content = "Leg day Monday, push Wednesday, pull Friday.")
    private val repository = FakeNotesRepository(listOf(goa, gym))
    private val ai = mockk<AiEngine>()
    private val useCase = AskNotesUseCase(repository, TfIdfRetriever(), ai)

    @Test
    fun `sends only relevant notes to the model and returns them as sources`() = runTest {
        val context = slot<List<Note>>()
        coEvery { ai.answer(any(), capture(context)) } returns "Your flights are on 20 December [Goa trip]."

        val result = useCase("When are my Goa flights?")

        assertEquals(listOf(goa), context.captured)
        assertEquals(listOf(goa), result.sources)
        assertEquals("Your flights are on 20 December [Goa trip].", result.answer)
    }

    @Test
    fun `skips the model call when no note is relevant`() = runTest {
        val result = useCase("What is the capital of Peru?")

        assertEquals(AskNotesUseCase.NO_MATCH_ANSWER, result.answer)
        assertEquals(emptyList<Note>(), result.sources)
        coVerify(exactly = 0) { ai.answer(any(), any()) }
    }
}
