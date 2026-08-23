package com.zmastery.english.data.repositories

import android.content.Context
import com.zmastery.english.data.AppState
import com.zmastery.english.data.Persistence
import com.zmastery.english.domain.repositories.StateRepository

/**
 * DataStore-backed implementation of [StateRepository].
 *
 * This is the production implementation that persists state using
 * Android's DataStore (the same mechanism used by [Persistence]).
 */
class DataStoreStateRepository(
    private val context: Context,
) : StateRepository {

    override suspend fun load(): AppState? = Persistence.load(context)

    override suspend fun save(state: AppState) = Persistence.save(context, state)

    override suspend fun clear() = Persistence.clear(context)

    override fun encode(state: AppState): String = Persistence.encode(state)

    override fun decode(raw: String): AppState? = Persistence.decode(raw)
}
