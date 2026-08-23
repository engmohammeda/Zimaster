package com.zmastery.english.domain.repositories

import com.zmastery.english.data.AppState

/**
 * Repository interface for app state persistence.
 *
 * Abstracts the storage mechanism so the ViewModel doesn't depend on
 * DataStore directly. Enables testing with an in-memory implementation.
 */
interface StateRepository {
    /** Load the current app state, or null if none exists. */
    suspend fun load(): AppState?

    /** Save the app state. Throws on failure. */
    suspend fun save(state: AppState)

    /** Clear all stored state. */
    suspend fun clear()

    /** Encode state to JSON string (for cloud sync / backup). */
    fun encode(state: AppState): String

    /** Decode JSON string to state, or null if invalid. */
    fun decode(raw: String): AppState?
}
