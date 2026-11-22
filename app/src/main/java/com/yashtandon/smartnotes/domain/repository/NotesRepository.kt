package com.yashtandon.smartnotes.domain.repository

import com.yashtandon.smartnotes.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NotesRepository {
    /** Live list of notes, newest first, filtered by [query] (title, content or tags). */
    fun observeNotes(query: String = ""): Flow<List<Note>>

    suspend fun getNote(id: Long): Note?

    /** One-off snapshot of every note, used by the retriever for Q&A. */
    suspend fun getAllNotes(): List<Note>

    /** Inserts when [Note.id] is 0, otherwise updates. Returns the note id. */
    suspend fun upsert(note: Note): Long

    suspend fun delete(id: Long)
}
