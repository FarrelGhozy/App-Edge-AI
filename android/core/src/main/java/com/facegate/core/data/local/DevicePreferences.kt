package com.facegate.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.deviceStore by preferencesDataStore(name = "device_prefs")

class DevicePreferences(private val context: Context) {
    private companion object {
        val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        val DEVICE_NAME_KEY = stringPreferencesKey("device_name")
    }

    suspend fun getDeviceId(): String? {
        return context.deviceStore.data.first()[DEVICE_ID_KEY]
    }

    suspend fun getOrCreateDeviceId(): String {
        val existing = getDeviceId()
        if (existing != null) return existing
        val newId = UUID.randomUUID().toString()
        context.deviceStore.edit { prefs ->
            prefs[DEVICE_ID_KEY] = newId
        }
        return newId
    }

    suspend fun setDeviceId(id: String) {
        context.deviceStore.edit { prefs ->
            prefs[DEVICE_ID_KEY] = id
        }
    }

    suspend fun getDeviceName(): String? {
        return context.deviceStore.data.first()[DEVICE_NAME_KEY]
    }

    suspend fun setDeviceName(name: String) {
        context.deviceStore.edit { prefs ->
            prefs[DEVICE_NAME_KEY] = name
        }
    }
}
