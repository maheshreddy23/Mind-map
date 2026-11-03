package com.maheshreddy.mym.core.llm

enum class Role { SYSTEM, USER, ASSISTANT }

data class ChatMessage(val role: Role, val content: String)

fun interface TokenCounter {
    fun count(text: String): Int
}

/** Chat prompt formats for the supported model families. */
enum class PromptFormat {
    /** Qwen 2.5 and most recent instruct models. */
    CHATML {
        override fun render(messages: List<ChatMessage>, addAssistantPrefix: Boolean) = buildString {
            for (m in messages) {
                append("<|im_start|>").append(m.role.name.lowercase()).append('\n').append(m.content.trim()).append("<|im_end|>\n")
            }
            if (addAssistantPrefix) append("<|im_start|>assistant\n")
        }
    },

    /** Gemma 2/3: no system role, so the system prompt is folded into the first user turn. */
    GEMMA {
        override fun render(messages: List<ChatMessage>, addAssistantPrefix: Boolean) = buildString {
            val system = messages.filter { it.role == Role.SYSTEM }.joinToString("\n\n") { it.content.trim() }
            var systemPending = system.isNotEmpty()
            for (m in messages.filter { it.role != Role.SYSTEM }) {
                val role = if (m.role == Role.ASSISTANT) "model" else "user"
                append("<start_of_turn>").append(role).append('\n')
                if (systemPending && m.role == Role.USER) {
                    append(system).append("\n\n"); systemPending = false
                }
                append(m.content.trim()).append("<end_of_turn>\n")
            }
            if (systemPending) append("<start_of_turn>user\n").append(system).append("<end_of_turn>\n")
            if (addAssistantPrefix) append("<start_of_turn>model\n")
        }
    },

    /** Phi-3.5 / Phi-4-mini (MIT licensed). */
    PHI {
        override fun render(messages: List<ChatMessage>, addAssistantPrefix: Boolean) = buildString {
            for (m in messages) {
                append("<|").append(m.role.name.lowercase()).append("|>").append(m.content.trim()).append("<|end|>")
            }
            if (addAssistantPrefix) append("<|assistant|>")
        }
    },

    LLAMA3 {
        override fun render(messages: List<ChatMessage>, addAssistantPrefix: Boolean) = buildString {
            for (m in messages) {
                append("<|start_header_id|>").append(m.role.name.lowercase()).append("<|end_header_id|>\n\n")
                    .append(m.content.trim()).append("<|eot_id|>")
            }
            if (addAssistantPrefix) append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        }
    };

    abstract fun render(messages: List<ChatMessage>, addAssistantPrefix: Boolean): String

    companion object {
        fun detect(template: String?): PromptFormat = when {
            template == null -> CHATML
            "<start_of_turn>" in template -> GEMMA
            "<|start_header_id|>" in template -> LLAMA3
            "<|end|>" in template && "<|assistant|>" in template -> PHI
            else -> CHATML
        }
    }
}
