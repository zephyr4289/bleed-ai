package com.zai.chat.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FileAttachment(
    val id: String,          // server file_id, goes on the wire
    val name: String,        // DISPLAY_NAME from the content resolver
    val mimeType: String?,   // contentResolver.getType, null-safe
    val sizeBytes: Long? = null
)
