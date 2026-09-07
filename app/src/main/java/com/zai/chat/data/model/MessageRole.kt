package com.zai.chat.data.model

enum class MessageRole {
    USER, ASSISTANT, SYSTEM;

    /** DB/server wire format is the lowercase name. */
    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(raw: String): MessageRole = when (raw.lowercase()) {
            "user" -> USER
            "assistant" -> ASSISTANT
            else -> SYSTEM
        }
    }
}
