package com.facegate.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session")

class SessionManager(private val context: Context) {
    private companion object {
        val TOKEN_KEY = stringPreferencesKey("jwt_token")
        val ADMIN_ID_KEY = stringPreferencesKey("admin_id")
        val ADMIN_USERNAME_KEY = stringPreferencesKey("admin_username")
        val ADMIN_DISPLAY_NAME_KEY = stringPreferencesKey("admin_display_name")
        val ADMIN_ROLE_KEY = stringPreferencesKey("admin_role")
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[TOKEN_KEY] != null
    }

    suspend fun getToken(): String? {
        return context.dataStore.data.first()[TOKEN_KEY]
    }

    suspend fun saveSession(
        token: String,
        adminId: String,
        username: String,
        displayName: String,
        role: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[ADMIN_ID_KEY] = adminId
            prefs[ADMIN_USERNAME_KEY] = username
            prefs[ADMIN_DISPLAY_NAME_KEY] = displayName
            prefs[ADMIN_ROLE_KEY] = role
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}
