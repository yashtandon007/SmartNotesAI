package com.yashtandon.smartnotes

import androidx.lifecycle.SavedStateHandle
import com.yashtandon.smartnotes.domain.ai.AiEngine
import com.yashtandon.smartnotes.domain.model.AiOutput
import com.yashtandon.smartnotes.domain.model.AiSource
import com.yashtandon.smartnotes.domain.model.Note
import com.yashtandon.smartnotes.domain.usecase.SuggestTagsUseCase
import com.yashtandon.smartnotes.domain.usecase.SummarizeNoteUseCase
import com.yashtandon.smartnotes.ui.editor.NoteEditorViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NoteEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val existing = Note(id = 7, title = "Standup", content = "Shipped login screen. Blocked on API review.")
    private val repository = FakeNotesRepository(listOf(existing))
    private val ai = mockk<AiEngine>()

    private fun viewModel(noteId: Long) = NoteEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("noteId" to noteId)),
        repository = repository,
        summarizeNote = SummarizeNoteUseCase(ai),
        suggestTagsFor = SuggestTagsUseCase(ai),
    )

    @Test
    fun `loads existing note`() {
        val vm = viewModel(7)
        assertEquals("Standup", vm.state.value.title)
    }

    @Test
    fun `summary is shown with its source and saved`() = runTest {
        coEvery { ai.summarize(any()) } returns AiOutput("- Shipped login", AiSource.ON_DEVICE)
        val vm = viewModel(7)

        vm.summarize()

        assertEquals("- Shipped login", vm.state.value.summary)
        assertEquals(AiSource.ON_DEVICE, vm.state.value.summarySource)
        assertEquals("- Shipped login", repository.getNote(7)?.summary)
        assertNull(vm.state.value.runningTask)
    }

    @Test
    fun `tags are cleaned, merged and de-duplicated`() = runTest {
        coEvery { ai.suggestTags(any()) } returns listOf("#Work", " standup ", "work")
        val vm = viewModel(7)

        vm.generateTags()

        assertEquals(listOf("work", "standup"), vm.state.value.tags)
    }

    @Test
    fun `AI error becomes a user-facing message`() = runTest {
        coEvery { ai.summarize(any()) } throws RuntimeException("Network unavailable")
        val vm = viewModel(7)

        vm.summarize()

        assertEquals("Network unavailable", vm.state.value.message)
        assertNull(vm.state.value.runningTask)
    }

    @Test
    fun `new note is created on save and close`() = runTest {
        val vm = viewModel(-1)
        vm.onTitleChange("Ideas")
        vm.onContentChange("Build an on-device notes summariser")

        vm.saveAndClose()

        assertTrue(vm.state.value.isClosed)
        assertTrue(repository.getAllNotes().any { it.title == "Ideas" })
    }
}
