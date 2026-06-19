package com.facegate.core.data.local.dao

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncStore by preferencesDataStore(name = "sync_metadata")

class SyncMetadata(private val context: Context) {
    private companion object {
        val LAST_FACE_SYNC_KEY = stringPreferencesKey("last_face_sync")
    }

    suspend fun getLastFaceSync(): String? {
        return context.syncStore.data.first()[LAST_FACE_SYNC_KEY]
    }

    suspend fun setLastFaceSync(timestamp: String) {
        context.syncStore.edit { prefs ->
            prefs[LAST_FACE_SYNC_KEY] = timestamp
        }
    }
}
