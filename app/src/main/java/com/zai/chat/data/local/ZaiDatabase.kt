package com.zai.chat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zai.chat.data.local.dao.ChatDao
import com.zai.chat.data.local.dao.MessageDao
import com.zai.chat.data.local.entity.ChatEntity
import com.zai.chat.data.local.entity.MessageEntity
import com.zai.chat.data.local.entity.MessageFtsEntity

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageFtsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ZaiDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
}
