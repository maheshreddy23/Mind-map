package com.freshernotes.core.llm

import com.freshernotes.core.ChatTurn
import com.freshernotes.core.Speaker

/** Conversation memory: a running summary of older turns plus the turns not yet summarized. */
data class ConversationMemory(
    val summary: String,
    /** Number of turns (from the start of the session) already folded into [summary]. */
    val summarizedCount: Int,
    val allTurns: List<ChatTurn>,
) {
    val recentTurns: List<ChatTurn> get() = allTurns.drop(summarizedCount)
}

/**
 * Builds the rolling prompt and decides when to compress. When the prompt (plus room for the reply) passes
 * [threshold] of the context window, the oldest turns are summarized into the running summary, keeping
 * at least [keepRecentTurns] verbatim.
 */
class ContextManager(
    private val model: LanguageModel,
    private val threshold: Float = 0.70f,
    private val keepRecentTurns: Int = 6,
    private val replyReserveTokens: Int = 128,
) {
    /** Prompt messages; [nudge] (if given) is appended to the final senior message. */
    fun messages(memory: ConversationMemory, systemPrompt: String, nudge: String? = null): List<ChatMessage> {
        val system = if (memory.summary.isBlank()) systemPrompt
        else "$systemPrompt\n\nWhat the senior explained earlier in this session (summary):\n${memory.summary.trim()}"
        val turns = memory.recentTurns.filter { it.speaker != Speaker.SYSTEM }.map {
            ChatMessage(if (it.speaker == Speaker.FRESHER) Role.ASSISTANT else Role.USER, it.text)
        }
        val merged = mergeConsecutive(turns).toMutableList()
        val last = merged.lastOrNull()
        if (nudge != null && last != null && last.role == Role.USER) merged[merged.lastIndex] = last.copy(content = last.content + "\n\n" + nudge)
        return listOf(ChatMessage(Role.SYSTEM, system)) + merged
    }

    /** Fraction of the context window the next prompt (plus reply reserve) would use. */
    fun usage(memory: ConversationMemory, systemPrompt: String): Float {
        val prompt = model.format.render(messages(memory, systemPrompt), addAssistantPrefix = true)
        return (model.count(prompt) + replyReserveTokens).toFloat() / model.contextSize
    }

    fun needsCompaction(memory: ConversationMemory, systemPrompt: String): Boolean =
        usage(memory, systemPrompt) > threshold && memory.recentTurns.size > keepRecentTurns

    /** Folds the oldest unsummarized turns into the summary. Returns the new memory state. */
    suspend fun compact(memory: ConversationMemory): ConversationMemory {
        val recent = memory.recentTurns
        if (recent.size <= keepRecentTurns) return memory
        // Fold at least half of the unsummarized turns so compaction does not run again on the next turn.
        val foldCount = maxOf(recent.size - keepRecentTurns, recent.size / 2)
        val toFold = recent.take(foldCount)
        val summary = model.complete(summarizationPrompt(memory.summary, toFold), maxTokens = 400, temperature = 0.2f)
        return memory.copy(summary = summary.ifBlank { memory.summary }, summarizedCount = memory.summarizedCount + foldCount)
    }

    companion object {
        fun transcript(turns: List<ChatTurn>): String = turns.filter { it.speaker != Speaker.SYSTEM }.joinToString("\n") {
            (if (it.speaker == Speaker.FRESHER) "Fresher" else "Senior") + ": " + it.text.trim()
        }

        fun summarizationPrompt(previous: String, turns: List<ChatTurn>): List<ChatMessage> = listOf(
            ChatMessage(
                Role.SYSTEM,
                "You maintain concise working notes of a senior engineer teaching a fresher. Keep every fact, " +
                    "name, number, tool, reason and pitfall. Use short bullet points. Output only the updated notes.",
            ),
            ChatMessage(
                Role.USER,
                buildString {
                    if (previous.isNotBlank()) append("Current notes:\n").append(previous.trim()).append("\n\n")
                    append("New conversation to fold in:\n").append(transcript(turns))
                },
            ),
        )

        /** Chat templates expect alternating roles; join consecutive same-role turns (e.g. two senior turns). */
        fun mergeConsecutive(messages: List<ChatMessage>): List<ChatMessage> {
            val out = ArrayList<ChatMessage>()
            for (m in messages) {
                val last = out.lastOrNull()
                if (last != null && last.role == m.role) out[out.lastIndex] = last.copy(content = last.content + "\n" + m.content)
                else out += m
            }
            return out
        }
    }
}
