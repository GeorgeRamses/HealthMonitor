package com.healthmonitor.app.shared.remote

/**
 * RemoteDataSource defines the contract to interact with the remote Supabase/Postgres backend.
 * Implementations should provide pullChanges(since) and pushChanges(batch) methods.
 */
interface RemoteDataSource {
    suspend fun pullChangesSince(sinceEpochMs: Long): RemoteChangesResult
    suspend fun pushChanges(changes: List<Any>): RemotePushResult
}

data class RemoteChangesResult(val items: List<Any>, val serverTimeMs: Long)
data class RemotePushResult(val success: Boolean)

