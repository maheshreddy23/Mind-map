package com.maheshreddy.mym.core.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/** Counts whitespace-separated words as tokens and replies with a scripted answer. */
class FakeModel(override val contextSize: Int = 1000, var reply: (List<ChatMessage>) -> String = { "Why is that?" }) : LanguageModel {
    override val format = PromptFormat.CHATML
    val calls = ArrayList<List<ChatMessage>>()
    override fun count(text: String) = text.split(Regex("\\s+")).count { it.isNotEmpty() }
    override fun stream(messages: List<ChatMessage>, maxTokens: Int, temperature: Float): Flow<String> {
        calls += messages
        return reply(messages).split(" ").mapIndexed { i, w -> if (i == 0) w else " $w" }.asFlow()
    }
}
