package com.freshernotes.app.library

import com.freshernotes.app.data.LibraryRepository
import com.freshernotes.app.engine.Engines
import com.freshernotes.app.engine.LlamaLanguageModel
import com.freshernotes.core.llm.ChatMessage
import com.freshernotes.core.llm.LanguageModel
import com.freshernotes.core.llm.Role
import com.freshernotes.core.llm.complete
import javax.inject.Inject
import javax.inject.Singleton

/** "Rewrite summary": a short, coherent prose overview of a topic's entries, placed at the top of its page. */
@Singleton
class TopicSummarizer @Inject constructor(
    private val library: LibraryRepository,
    private val engines: Engines,
) {
    suspend fun rewrite(topicId: Long, model: LanguageModel? = null): Result<String> = runCatching {
        val topic = requireNotNull(library.topic(topicId)) { "Topic not found" }
        val llm = model ?: LlamaLanguageModel(engines.llm())
        // The page without the old summary, trimmed to leave room for the answer.
        val page = topic.markdown.replace(topic.summary, "").let { md ->
            var text = md
            while (text.length > 400 && llm.count(text) > llm.contextSize - 900) text = text.take(text.length * 3 / 4)
            text
        }
        val summary = llm.complete(
            listOf(
                ChatMessage(Role.SYSTEM, "You write a short, coherent overview (3-6 sentences) of a personal knowledge page. Use only what the page says. No headings, no bullets, no preamble."),
                ChatMessage(Role.USER, page),
            ),
            maxTokens = 300, temperature = 0.2f,
        ).trim()
        require(summary.isNotBlank()) { "The model returned nothing" }
        library.setSummary(topicId, summary)
        summary
    }
}
