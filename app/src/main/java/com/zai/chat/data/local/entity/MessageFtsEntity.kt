package com.zai.chat.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * External-content FTS4 index over [MessageEntity].
 *
 * GOTCHA FIX #1 — this file differs from the PDF, deliberately:
 * the PDF declared `@PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long`
 * in here. Room's contentEntity-FTS validation requires the FTS entity to
 * declare ONLY text fields that exist in the content entity — `rowId` is
 * neither (fails KSP with a field-mismatch error). Room maintains the FTS
 * rowid↔content-rowid mapping and sync triggers automatically; we declare
 * nothing extra.
 *
 * Nullable reasoning mirrors the content entity's declared type.
 */
@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "messages_fts")
data class MessageFtsEntity(
    val content: String,
    val reasoning: String?
)
