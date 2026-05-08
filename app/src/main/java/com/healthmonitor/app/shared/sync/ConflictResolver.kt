package com.healthmonitor.app.shared.sync

import com.healthmonitor.app.data.local.entities.PatientEntity

/**
 * Conflict resolver implementing Win-Latest strategy based on last_modified_at epoch ms.
 */
object ConflictResolver {
    
    interface Syncable {
        val id: String
        val lastModifiedAt: Long
        val isDeleted: Boolean
    }

    fun <T : Syncable> resolve(local: T?, remote: T): T {
        if (local == null) return remote
        return if (remote.lastModifiedAt > local.lastModifiedAt) remote else local
    }
}

