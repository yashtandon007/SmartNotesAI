package com.yashtandon.smartnotes

import com.yashtandon.smartnotes.domain.model.Note
import com.yashtandon.smartnotes.domain.repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Replaces Dispatchers.Main (used by viewModelScope) with a test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** In-memory repository: a fake is simpler and more readable than mocking a whole repository. */
class FakeNotesRepository(initial: List<Note> = emptyList()) : NotesRepository {
    private val notes = MutableStateFlow(initial.associateBy { it.id })
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeNotes(query: String): Flow<List<Note>> = notes.map { map ->
        map.values.filter { query.isBlank() || it.title.contains(query, true) || it.content.contains(query, true) }
            .sortedByDescending { it.updatedAt }
    }

    override suspend fun getNote(id: Long): Note? = notes.value[id]

    override suspend fun getAllNotes(): List<Note> = notes.value.values.toList()

    override suspend fun upsert(note: Note): Long {
        val id = if (note.id == 0L) nextId++ else note.id
        notes.value = notes.value + (id to note.copy(id = id))
        return id
    }

    override suspend fun delete(id: Long) {
        notes.value = notes.value - id
    }
}
