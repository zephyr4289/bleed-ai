package com.zai.chat.data.model

data class Chat(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val pinned: Boolean,
    val folderId: String?,
    val fullyCached: Boolean
)
