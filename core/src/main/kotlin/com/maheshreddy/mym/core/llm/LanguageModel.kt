package com.maheshreddy.mym.core.llm

import kotlinx.coroutines.flow.Flow

/** What the conversation logic needs from an LLM; implemented over llama.cpp in the app and faked in tests. */
interface LanguageModel : TokenCounter {
    val contextSize: Int
    val format: PromptFormat
    fun stream(messages: List<ChatMessage>, maxTokens: Int, temperature: Float): Flow<String>
}

suspend fun LanguageModel.complete(messages: List<ChatMessage>, maxTokens: Int, temperature: Float = 0.3f): String {
    val sb = StringBuilder()
    stream(messages, maxTokens, temperature).collect { sb.append(it) }
    return sb.toString().trim()
}
