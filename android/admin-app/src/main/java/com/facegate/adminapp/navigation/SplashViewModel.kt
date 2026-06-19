package com.facegate.adminapp.navigation

import androidx.lifecycle.ViewModel
import com.facegate.core.data.local.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    val sessionManager: SessionManager
) : ViewModel() {

    suspend fun getStartDestination(): String {
        kotlinx.coroutines.delay(1500)
        return if (sessionManager.isLoggedIn.first()) {
            Screen.Dashboard.route
        } else {
            Screen.Login.route
        }
    }
}
