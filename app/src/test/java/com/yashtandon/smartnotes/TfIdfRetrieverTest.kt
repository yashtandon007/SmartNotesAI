package com.yashtandon.smartnotes

import com.yashtandon.smartnotes.domain.model.Note
import com.yashtandon.smartnotes.domain.rag.TfIdfRetriever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TfIdfRetrieverTest {

    private val retriever = TfIdfRetriever()

    private val notes = listOf(
        Note(id = 1, title = "Goa trip", content = "Book flights to Goa in December. Budget 40k. Stay near Anjuna beach."),
        Note(id = 2, title = "Android interview prep", content = "Revise coroutines, Flow, Compose recomposition and Hilt scopes."),
        Note(id = 3, title = "Groceries", content = "Milk, eggs, bread, coffee beans."),
    )

    @Test
    fun `most relevant note ranks first`() {
        val result = retriever.topK("when should I book the Goa flights?", notes)
        assertEquals(1L, result.first().note.id)
    }

    @Test
    fun `title words are matched`() {
        val result = retriever.topK("interview", notes)
        assertEquals(2L, result.first().note.id)
    }

    @Test
    fun `unrelated question returns nothing`() {
        assertTrue(retriever.topK("quantum chromodynamics", notes).isEmpty())
    }

    @Test
    fun `respects k and sorts by score`() {
        val result = retriever.topK("Goa coffee coroutines", notes, k = 2)
        assertEquals(2, result.size)
        assertTrue(result[0].score >= result[1].score)
    }

    @Test
    fun `tokenizer drops stop words, punctuation and case`() {
        assertEquals(listOf("book", "goa", "flights"), retriever.tokenize("Book the Goa flights!"))
    }
}
