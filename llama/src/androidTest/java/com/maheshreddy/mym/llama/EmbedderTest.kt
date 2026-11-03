package com.maheshreddy.mym.llama

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

/** BGE small (MIT) through llama.cpp: vectors are normalised and closer for related sentences. */
@RunWith(AndroidJUnit4::class)
class EmbedderTest {
    private fun dot(a: FloatArray, b: FloatArray) = a.indices.sumOf { (a[it] * b[it]).toDouble() }

    @Test
    fun relatedSentencesAreCloserThanUnrelatedOnes() = runBlocking {
        Embedder.load(TestModels.copy("bge-small-en-v1.5-q8_0.gguf")).use { e ->
            val jig = e.embed("A drill jig guides the drill bit with hardened bushes so holes land in the same place.")
            val bush = e.embed("Worn drill bushes make hole positions drift.")
            val cake = e.embed("Bake the cake at 180 degrees for forty minutes.")
            assertEquals(384, jig.size)
            assertEquals(1.0, sqrt(dot(jig, jig)), 1e-3)
            val related = dot(jig, bush)
            val unrelated = dot(jig, cake)
            assertTrue("related $related should beat unrelated $unrelated", related > unrelated + 0.1)
        }
    }
}
