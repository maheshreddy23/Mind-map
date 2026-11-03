package com.maheshreddy.mym.engine

import com.maheshreddy.mym.core.llm.ChatMessage
import com.maheshreddy.mym.core.llm.LanguageModel
import com.maheshreddy.mym.core.llm.PromptFormat
import com.maheshreddy.mym.llama.GenerationParams
import com.maheshreddy.mym.llama.LlamaEngine
import kotlinx.coroutines.flow.Flow

/** Adapts the llama.cpp engine to the platform-independent [LanguageModel] used by the conversation logic. */
class LlamaLanguageModel(private val engine: LlamaEngine) : LanguageModel {
    override val contextSize: Int get() = engine.contextSize
    override val format: PromptFormat get() = engine.format
    override fun count(text: String): Int = engine.count(text)
    override fun stream(messages: List<ChatMessage>, maxTokens: Int, temperature: Float): Flow<String> =
        engine.stream(messages, GenerationParams(maxTokens = maxTokens, temperature = temperature))
}
