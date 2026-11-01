package com.freshernotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshernotes.app.data.SessionRepository
import com.freshernotes.app.data.db.AppDatabase
import com.freshernotes.app.data.settings.SettingsRepository
import com.freshernotes.app.device.DeviceMonitor
import com.freshernotes.app.device.PerfStats
import com.freshernotes.app.engine.Engines
import com.freshernotes.app.engine.SynthesizerProvider
import com.freshernotes.app.models.ModelStore
import com.freshernotes.app.session.ConversationController
import com.freshernotes.app.session.FresherState
import com.freshernotes.core.Speaker
import com.freshernotes.tts.SpeechSynthesizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** End-to-end conversation loop with the real (stand-in) LLM; speech output is captured instead of played. */
@RunWith(AndroidJUnit4::class)
class ConversationLoopTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var engines: Engines
    private val spoken = ArrayList<String>()

    private val fakeVoice = SynthesizerProvider {
        object : SpeechSynthesizer {
            override val name = "fake"
            override suspend fun speak(text: String, rate: Float) { spoken += text }
            override fun stop() = Unit
            override fun close() = Unit
        }
    }

    @Before
    fun setUp() {
        TestModels.copyTo("qwen2.5-0.5b-instruct-q4_k_m.gguf", File(context.filesDir, "models/llm/qwen2.5-0.5b-instruct-q4_k_m.gguf"))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        engines = Engines(context, ModelStore(context))
    }

    @After
    fun tearDown() = runBlocking {
        engines.releaseAll()
        db.close()
    }

    @Test
    fun seniorTurnProducesSpokenQuestionAndWrapUpEnds() = runBlocking {
        val sessions = SessionRepository(db.sessionDao(), db.turnDao())
        val settings = SettingsRepository(context).apply { update { it.copy(questionEveryNTurns = 1, maxReplyWords = 40) } }
        val controller = ConversationController(sessions, settings, engines, fakeVoice, DeviceMonitor(context), PerfStats())
        val id = sessions.startSession("loop test")

        val wrap1 = controller.onSeniorTurn(
            id,
            "Today I'll explain drill jigs. We clamp the jig to the wing skin using tooling holes as datums, then drill through the bushes.",
            speech = null,
        )
        assertFalse(wrap1)
        val state = controller.state.value
        assertTrue("unexpected state $state", state is FresherState.Idle)
        val turns = sessions.turns(id)
        assertEquals(listOf(Speaker.SENIOR, Speaker.FRESHER), turns.map { it.speaker })
        val question = turns[1].text
        assertEquals(listOf(question), spoken)
        assertTrue("persona must ask a question: $question", question.endsWith("?"))
        assertTrue("too long: $question", question.split(" ").size <= 40)

        assertTrue(controller.onSeniorTurn(id, "Okay, let's wrap up.", speech = null))
        assertEquals(FresherState.WrappedUp, controller.state.value)
        assertEquals(3, sessions.turns(id).size) // the wrap-up turn is saved, no new question
    }

    /**
     * Regression: on the phone, transcripts only appeared after switching apps because each one was saved only
     * after the previous reply (LLM + TTS) finished, and Android TTS could hang. Turns must be saved immediately,
     * and a TTS engine that never completes must not block the session.
     */
    @Test
    fun seniorTurnsAreSavedWhileFresherIsStuckSpeaking() = runBlocking<Unit> {
        var stopped = false
        val stuckVoice = SynthesizerProvider {
            object : SpeechSynthesizer {
                override val name = "stuck"
                override suspend fun speak(text: String, rate: Float) = awaitCancellation()
                override fun stop() { stopped = true }
                override fun close() = Unit
            }
        }
        val sessions = SessionRepository(db.sessionDao(), db.turnDao())
        val settings = SettingsRepository(context).apply { update { it.copy(questionEveryNTurns = 1, maxReplyWords = 40) } }
        val controller = ConversationController(sessions, settings, engines, stuckVoice, DeviceMonitor(context), PerfStats())
        val id = sessions.startSession("stuck tts")

        controller.recordSeniorTurn(id, "We locate drill jigs on tooling holes so every hole lands in the same place.")
        val reply = launch(Dispatchers.Default) { controller.respond(id, speech = null) }
        withTimeout(180_000) { controller.state.first { it is FresherState.Speaking } }

        val t0 = System.nanoTime()
        controller.recordSeniorTurn(id, "Also, the bushes wear out and must be gauged.")
        val savedMs = (System.nanoTime() - t0) / 1_000_000
        assertTrue("saving took $savedMs ms while the fresher was speaking", savedMs < 2_000)
        assertTrue(sessions.turns(id).any { it.text.startsWith("Also, the bushes") })

        withTimeout(60_000) { reply.join() }
        assertTrue("stuck TTS was not stopped", stopped)
        assertEquals(FresherState.Idle, controller.state.value)
    }
}
