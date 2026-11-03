package com.maheshreddy.mym

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maheshreddy.mym.core.library.AnswerPart
import com.maheshreddy.mym.core.library.Candidate
import com.maheshreddy.mym.core.library.Privacy
import com.maheshreddy.mym.core.library.Retrieval
import com.maheshreddy.mym.llama.Embedder
import com.maheshreddy.mym.ui.ask.AskContent
import com.maheshreddy.mym.ui.ask.AskUiState
import com.maheshreddy.mym.ui.ask.Exchange
import com.maheshreddy.mym.ui.ask.Reply
import com.maheshreddy.mym.ui.theme.MindTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Ask: search by meaning with the real BGE embedder, the privacy filter, and citation chips that open entries. */
@RunWith(AndroidJUnit4::class)
class AskTest {
    @get:Rule val compose = createComposeRule()

    private val entries = listOf(
        1L to "Drill jigs: hardened bushes guide the drill so every hole lands in the same place; replace bushes when worn.",
        2L to "Datum selection: pick the datum features the part is measured from, usually the mating faces.",
        3L to "Engineering change orders (ECO) in Teamcenter need approval from design, quality and manufacturing.",
    )

    @Test
    fun retrievalFindsEntriesByMeaningAndSaysNothingWhenUnrelated() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val model = TestModels.copyTo("bge-small-en-v1.5-q8_0.gguf", File(ctx.filesDir, "test/bge.gguf"))
        Embedder.load(model).use { e ->
            val candidates = entries.map { (id, text) -> Candidate(id, if (id == 3L) Privacy.PRIVATE else Privacy.SHAREABLE, e.embed(text)) }
            val bar = Retrieval.minSimilarityFor("bge-small-en-v1.5")
            // No shared keywords with entry 1: found by meaning only.
            val hits = Retrieval.rank(e.embed("Why would hole positions start drifting on a fixture?"), candidates, emptyList(), minSimilarity = bar)
            assertEquals(1L, hits.firstOrNull()?.entryId)
            // Unrelated question: nothing relevant, so Ask offers an interview instead of guessing.
            val none = Retrieval.rank(e.embed("How long should I bake a sponge cake?"), candidates, emptyList(), minSimilarity = bar)
            assertTrue("unrelated question matched $none", none.isEmpty())
            // Privacy filter: a Private entry is never offered when only Shareable is included.
            val q = e.embed("Who approves an engineering change in Teamcenter?")
            assertEquals(3L, Retrieval.rank(q, candidates, emptyList(), minSimilarity = bar).first().entryId)
            assertTrue(Retrieval.rank(q, candidates, emptyList(), allowed = setOf(Privacy.SHAREABLE), minSimilarity = bar).none { it.entryId == 3L })
        }
    }

    @Test
    fun citationChipsOpenTheirEntry() {
        var opened = -1L
        val answer = Reply.Answer(listOf(AnswerPart.Text("Replace worn bushes"), AnswerPart.Cite(1, 42L), AnswerPart.Text(".")), citedCount = 1)
        compose.setContent {
            MindTheme { AskContent(AskUiState(listOf(Exchange(0, "Why do holes drift?", answer))), {}, { opened = it }, {}, {}) }
        }
        compose.onNodeWithText("From 1 of your entries").assertExists()
        compose.onNodeWithContentDescription("Source 1").performClick()
        assertEquals(42L, opened)
    }

    @Test
    fun emptyStatesOfferSuggestionsOrASession() {
        var asked = ""
        var interview: String? = null
        val state = androidx.compose.runtime.mutableStateOf(AskUiState(suggestions = listOf("What is still open on Drill jigs?", "What have I said about ECO?", "What lessons have I recorded about Tooling?")))
        compose.setContent { MindTheme { AskContent(state.value, { asked = it }, {}, { interview = it }, {}) } }
        assertEquals(3, compose.onAllNodesWithTag("suggestion").fetchSemanticsNodes().size)
        compose.onNodeWithText("What have I said about ECO?").performClick()
        assertEquals("What have I said about ECO?", asked)
        state.value = AskUiState(libraryEmpty = true)
        compose.onNodeWithTag("askStartSession").performClick()
        assertEquals("", interview)
        state.value = AskUiState(listOf(Exchange(1, "Cake?", Reply.Nothing)))
        compose.onNodeWithTag("startInterview").performClick()
        assertEquals("Cake?", interview)
    }
}
