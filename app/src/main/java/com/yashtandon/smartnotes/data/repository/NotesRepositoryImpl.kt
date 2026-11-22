package com.yashtandon.smartnotes.data.repository

import com.yashtandon.smartnotes.data.local.NoteDao
import com.yashtandon.smartnotes.data.local.NoteEntity
import com.yashtandon.smartnotes.domain.model.Note
import com.yashtandon.smartnotes.domain.repository.NotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
) : NotesRepository {

    override fun observeNotes(query: String): Flow<List<Note>> {
        val source = if (query.isBlank()) dao.observeAll() else dao.search("%${query.trim()}%")
        return source.map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getNote(id: Long): Note? = dao.getById(id)?.toDomain()

    override suspend fun getAllNotes(): List<Note> = dao.getAll().map { it.toDomain() }

    override suspend fun upsert(note: Note): Long {
        val rowId = dao.upsert(note.toEntity())
        // Room's @Upsert returns -1 when it updated an existing row.
        return if (rowId == -1L) note.id else rowId
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}

private fun NoteEntity.toDomain() = Note(
    id = id,
    title = title,
    content = content,
    summary = summary,
    tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    updatedAt = updatedAt,
)

private fun Note.toEntity() = NoteEntity(
    id = id,
    title = title,
    content = content,
    summary = summary,
    tags = tags.joinToString(","),
    updatedAt = updatedAt,
)
