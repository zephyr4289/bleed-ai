package com.zai.chat.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [RECON] Multipart upload response — we only need the id to reference in completions. */
@Serializable
data class FileUploadResponse(
    @SerialName("id") val id: String,
    @SerialName("filename") val filename: String
)

/** [RECON] GET /api/models → OpenAI-style wrapper. */
@Serializable
data class ModelsListResponse(
    @SerialName("data") val data: List<ModelEntry> = emptyList()
)

@Serializable
data class ModelEntry(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String? = null
)
