package com.yashtandon.smartnotes.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yashtandon.smartnotes.domain.model.Note
import com.yashtandon.smartnotes.ui.theme.SmartNotesTheme

@Composable
fun NotesListScreen(
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onAsk: () -> Unit,
    viewModel: NotesListViewModel = hiltViewModel(),
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    NotesListContent(
        notes = notes,
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onOpenNote = onOpenNote,
        onNewNote = onNewNote,
        onAsk = onAsk,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesListContent(
    notes: List<Note>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Smart Notes") },
                actions = { TextButton(onClick = onAsk) { Text("✨ Ask AI") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewNote) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search notes or tags") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (notes.isEmpty()) {
                EmptyState(isSearching = query.isNotBlank())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(note = note, onClick = { onOpenNote(note.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = note.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = note.summary ?: note.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    note.tags.forEach { tag ->
                        SuggestionChip(onClick = {}, label = { Text("#$tag") })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(isSearching: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = if (isSearching) "No notes match your search." else "No notes yet.\nTap + to write your first one.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NotesListPreview() {
    SmartNotesTheme {
        NotesListContent(
            notes = listOf(
                Note(
                    id = 1,
                    title = "Jetpack Compose",
                    content = "Compose is a modern toolkit for building native Android UI.",
                    summary = "Modern UI toolkit for Android.",
                    tags = listOf("android", "ui"),
                ),
                Note(
                    id = 2,
                    title = "Kotlin Coroutines",
                    content = "Asynchronous programming made easy.",
                    tags = listOf("kotlin", "async"),
                ),
                Note(
                    id = 3,
                    title = "",
                    content = "This is a note without a title.",
                )
            ),
            query = "",
            onQueryChange = {},
            onOpenNote = {},
            onNewNote = {},
            onAsk = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NotesListEmptyPreview() {
    SmartNotesTheme {
        NotesListContent(
            notes = emptyList(),
            query = "",
            onQueryChange = {},
            onOpenNote = {},
            onNewNote = {},
            onAsk = {},
        )
    }
}
