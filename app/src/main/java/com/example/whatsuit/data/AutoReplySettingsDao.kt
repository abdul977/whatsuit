package com.example.whatsuit.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable

@Dao
interface AutoReplySettingsDao {
    @Upsert
    suspend fun upsert(settings: AutoReplySettings)

    @Query("SELECT * FROM auto_reply_settings WHERE conversationId = :conversationId")
    suspend fun getByConversationId(conversationId: String): AutoReplySettings?

    @Query("SELECT * FROM auto_reply_settings")
    fun observeAll(): Flow<List<AutoReplySettings>>

    @Query("""
        SELECT ars.* FROM auto_reply_settings ars
        INNER JOIN notifications n ON n.conversationId = ars.conversationId
        WHERE n.id = :notificationId
    """)
    suspend fun getByNotificationId(notificationId: Long): AutoReplySettings?

    // Java-friendly methods
    fun upsertBlocking(settings: AutoReplySettings) = runBlocking {
        upsert(settings)
    }

    fun getByConversationIdBlocking(conversationId: String): AutoReplySettings? = runBlocking {
        getByConversationId(conversationId)
    }

    fun getByNotificationIdBlocking(notificationId: Long): AutoReplySettings? = runBlocking {
        getByNotificationId(notificationId)
    }

    // Optional: Provide a Future-based API for better Java interop
    fun getByConversationIdAsync(conversationId: String): Callable<AutoReplySettings?> = Callable {
        getByConversationIdBlocking(conversationId)
    }
}