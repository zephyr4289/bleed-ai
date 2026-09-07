package com.zai.chat.data.mapper

import com.zai.chat.data.local.entity.ChatEntity
import com.zai.chat.data.local.entity.MessageEntity
import com.zai.chat.data.model.Chat
import com.zai.chat.data.model.Message
import com.zai.chat.data.model.MessageRole
import com.zai.chat.data.model.SearchCitation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ── Chat ────────────────────────────────────────────────────────────────

fun ChatEntity.toDomain(): Chat = Chat(
    id = id,
    title = title,
    updatedAt = updatedAt,
    pinned = pinned,
    folderId = folderId,
    fullyCached = fullyCached
)

fun Chat.toEntity(): ChatEntity = ChatEntity(
    id = id,
    title = title,
    updatedAt = updatedAt,
    pinned = pinned,
    folderId = folderId,
    fullyCached = fullyCached
)

// ── Message ─────────────────────────────────────────────────────────────

/**
 * Null-safe decode: a corrupted/legacy JSON blob degrades to empty list
 * instead of crashing the whole chat screen.
 */
fun MessageEntity.toDomain(json: Json): Message = Message(
    id = id,
    chatId = chatId,
    role = MessageRole.fromWire(role),
    content = content,
    reasoning = reasoning,
    attachments = attachmentsJson.decodeListOrEmpty(json),
    citations = searchResultsJson.decodeCitationsOrEmpty(json),
    createdAt = createdAt,
    tokenCount = tokenCount,
    isPartial = isPartial
)

/**
 * [newId] is supplied by the repository — streaming persistence and
 * optimistic inserts each generate their own UUIDs, so the mapper never
 * hides id semantics.
 */
fun Message.toEntity(newId: String, json: Json): MessageEntity = MessageEntity(
    id = newId,
    chatId = chatId,
    role = role.wire,
    content = content,
    reasoning = reasoning?.takeIf { it.isNotBlank() },
    attachmentsJson = json.encodeToString(attachments),
    searchResultsJson = json.encodeToString(citations),
    createdAt = createdAt,
    tokenCount = tokenCount,
    isPartial = isPartial
)

// ── private decode helpers ──────────────────────────────────────────────

private fun String?.decodeListOrEmpty(json: Json): List<String> =
    this?.let { raw ->
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    } ?: emptyList()

private fun String?.decodeCitationsOrEmpty(json: Json): List<SearchCitation> =
    this?.let { raw ->
        runCatching { json.decodeFromString<List<SearchCitation>>(raw) }.getOrDefault(emptyList())
    } ?: emptyList()
