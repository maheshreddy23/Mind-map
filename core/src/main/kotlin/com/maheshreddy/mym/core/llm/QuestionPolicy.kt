package com.maheshreddy.mym.core.llm

import com.maheshreddy.mym.core.ChatTurn
import com.maheshreddy.mym.core.Speaker

/** Decides whether the fresher should ask a question after the latest senior turn. */
object QuestionPolicy {
    sealed interface Decision {
        data object Ask : Decision
        data object Listen : Decision
        data object WrapUp : Decision
    }

    fun decide(turns: List<ChatTurn>, everyNTurns: Int): Decision {
        val last = turns.lastOrNull { it.speaker == Speaker.SENIOR } ?: return Decision.Listen
        if (Persona.isWrapUp(last.text)) return Decision.WrapUp
        val sinceQuestion = turns.takeLastWhile { it.speaker != Speaker.FRESHER }.count { it.speaker == Speaker.SENIOR }
        return if (sinceQuestion >= everyNTurns.coerceAtLeast(1)) Decision.Ask else Decision.Listen
    }
}
