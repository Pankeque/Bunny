package com.bunny.data.local

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UnreadStoreEntryPoint {
    fun unreadStore(): UnreadStore
}

// In-memory unread counter for direct-message conversations.
// Kept in memory (not persisted) so a fresh cold start begins clean.
@Singleton
class UnreadStore @Inject constructor() {

    private val counts = mutableMapOf<Int, Int>()

    // Conversation currently on screen; its incoming messages are never unread.
    @Volatile
    var activeConversationId: Int? = null

    private val _total = MutableStateFlow(0)
    val total: StateFlow<Int> = _total.asStateFlow()

    private val _byConversation = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val byConversation: StateFlow<Map<Int, Int>> = _byConversation.asStateFlow()

    @Synchronized
    fun increment(conversationId: Int) {
        if (conversationId == activeConversationId) return
        val next = (counts[conversationId] ?: 0) + 1
        counts[conversationId] = next
        publish()
    }

    @Synchronized
    fun markRead(conversationId: Int) {
        if (counts.remove(conversationId) != null) {
            publish()
        }
    }

    @Synchronized
    fun countFor(conversationId: Int): Int = counts[conversationId] ?: 0

    @Synchronized
    fun clearAll() {
        counts.clear()
        publish()
    }

    private fun publish() {
        val snapshot = counts.toMap()
        _total.value = snapshot.values.sum()
        _byConversation.value = snapshot
    }
}
