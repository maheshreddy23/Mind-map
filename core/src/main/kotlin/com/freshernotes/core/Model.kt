package com.freshernotes.core

/** Who spoke a turn in a session. */
enum class Speaker { SENIOR, FRESHER, SYSTEM }

/** A single utterance in a conversation, independent of storage. */
data class ChatTurn(val speaker: Speaker, val text: String)
