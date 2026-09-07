package com.zai.chat.di

import android.content.Context
import androidx.room.Room
import com.zai.chat.data.local.ZaiDatabase
import com.zai.chat.data.local.dao.ChatDao
import com.zai.chat.data.local.dao.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ZaiDatabase =
        Room.databaseBuilder(
            context,
            ZaiDatabase::class.java,
            "zai_local_chat.db"
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideChatDao(db: ZaiDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideMessageDao(db: ZaiDatabase): MessageDao = db.messageDao()
}
