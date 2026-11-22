package com.yashtandon.smartnotes.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yashtandon.smartnotes.ui.ask.AskScreen
import com.yashtandon.smartnotes.ui.editor.NoteEditorScreen
import com.yashtandon.smartnotes.ui.notes.NotesListScreen
import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes (Navigation 2.8+): routes are @Serializable classes.
@Serializable
object NotesRoute

/** noteId = NEW_NOTE_ID opens an empty editor. */
@Serializable
data class EditorRoute(val noteId: Long = NEW_NOTE_ID)

@Serializable
object AskRoute

const val NEW_NOTE_ID = -1L

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = NotesRoute) {
        composable<NotesRoute> {
            NotesListScreen(
                onOpenNote = { id -> navController.navigate(EditorRoute(id)) },
                onNewNote = { navController.navigate(EditorRoute()) },
                onAsk = { navController.navigate(AskRoute) },
            )
        }
        composable<EditorRoute> {
            NoteEditorScreen(onBack = { navController.popBackStack() })
        }
        composable<AskRoute> {
            AskScreen(
                onBack = { navController.popBackStack() },
                onOpenNote = { id -> navController.navigate(EditorRoute(id)) },
            )
        }
    }
}
