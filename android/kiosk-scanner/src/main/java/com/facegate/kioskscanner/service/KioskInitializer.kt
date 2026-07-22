package com.facegate.kioskscanner.service

import android.util.Log
import com.facegate.core.data.local.DevicePreferences
import com.facegate.core.data.local.SessionManager
import com.facegate.core.data.local.dao.CampusRuleDao
import com.facegate.core.data.local.dao.FaceVectorDao
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.LoginRequest
import com.facegate.core.face.FaceDetectorWrapper
import com.facegate.core.face.FaceEmbedder
import com.facegate.core.face.FaceMatcher
import com.facegate.core.sync.SyncManager
import com.facegate.kioskscanner.BuildConfig
import com.facegate.kioskscanner.scanner.VoiceFeedback
import com.facegate.kioskscanner.sync.DevicePingWorker
import com.facegate.kioskscanner.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KioskInitializer @Inject constructor(
    private val devicePreferences: DevicePreferences,
    private val sessionManager: SessionManager,
    private val apiService: ApiService,
    private val faceVectorDao: FaceVectorDao,
    private val campusRuleDao: CampusRuleDao,
    private val faceMatcher: FaceMatcher,
    private val faceDetector: FaceDetectorWrapper,
    private val faceEmbedder: FaceEmbedder,
    private val voiceFeedback: VoiceFeedback,
    private val syncManager: SyncManager
) {
    companion object {
        private const val TAG = "KioskInitializer"
    }

    private var scope: CoroutineScope? = null

    fun initialize(context: android.content.Context) {
        if (scope != null) return // already initialized
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope!!.launch {
            try {
                initFaceDetector()
                initFaceEmbedder()
                initVoiceFeedback()
                loginDevice()
                registerDevice()
                loadCachedFaces()
                loadCachedRules()
                // Immediate sync on first boot — download faces + rules
                if (faceVectorDao.count() == 0) {
                    Log.d(TAG, "No cached faces — performing initial sync")
                    val deviceId = devicePreferences.getDeviceId()
                    if (deviceId != null) {
                        syncManager.syncAll(deviceId)
                    }
                }
                scheduleWorkers(context)
                Log.d(TAG, "Kiosk initialization complete")
            } catch (e: Exception) {
                Log.e(TAG, "Kiosk initialization error", e)
            }
        }
    }

    private fun initFaceDetector() {
        faceDetector.init()
        Log.d(TAG, "FaceDetector initialized")
    }

    private fun initFaceEmbedder() {
        faceEmbedder.init()
        Log.d(TAG, "FaceEmbedder initialized")
    }

    private fun initVoiceFeedback() {
        voiceFeedback.init()
        Log.d(TAG, "VoiceFeedback initialized")
    }

    private suspend fun loginDevice() {
        try {
            val response = apiService.deviceLogin(
                LoginRequest(
                    username = BuildConfig.DEVICE_USERNAME,
                    password = BuildConfig.DEVICE_PASSWORD
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val loginData = response.body()!!.`data`
                sessionManager.saveSession(
                    token = loginData.token,
                    adminId = loginData.admin.id,
                    username = loginData.admin.username,
                    displayName = loginData.admin.displayName,
                    role = "device"
                )
                Log.d(TAG, "Device logged in: ${loginData.admin.username} (device)")
            } else {
                Log.w(TAG, "Device login failed: ${response.code()} — ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Device login skipped (offline): ${e.message}")
        }
    }

    private suspend fun registerDevice() {
        try {
            val deviceId = devicePreferences.getOrCreateDeviceId()
            val existingName = devicePreferences.getDeviceName()
            if (existingName != null) {
                Log.d(TAG, "Device already registered: $deviceId")
                return
            }

            val request = mapOf(
                "deviceId" to deviceId,
                "name" to "Kiosk Scanner",
                "location" to "Main Gate"
            )
            val response = apiService.registerDevice(request)
            if (response.isSuccessful) {
                val body = response.body()
                val name = body?.get("name")?.toString() ?: "Kiosk Scanner"
                devicePreferences.setDeviceName(name)
                Log.d(TAG, "Device registered: $deviceId")
            } else {
                Log.w(TAG, "Device registration failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Device registration skipped (offline): ${e.message}")
        }
    }

    private suspend fun loadCachedFaces() {
        val vectors = faceVectorDao.getAll()
        if (vectors.isNotEmpty()) {
            faceMatcher.buildIndex(vectors.map { it.toIndexEntry() })
            Log.d(TAG, "Loaded ${vectors.size} cached faces into matcher")
        } else {
            Log.d(TAG, "No cached faces — will download on first sync")
        }
    }

    private suspend fun loadCachedRules() {
        val rules = campusRuleDao.getAll()
        if (rules.isNotEmpty()) {
            Log.d(TAG, "Loaded ${rules.size} cached rules")
        } else {
            Log.d(TAG, "No cached rules to load")
        }
    }

    private fun scheduleWorkers(context: android.content.Context) {
        SyncWorker.scheduleMidnight(context)
        SyncWorker.schedulePolling(context)
        DevicePingWorker.schedule(context)

        // Start foreground service to prevent Android from killing the kiosk
        KioskForegroundService.start(context)

        Log.d(TAG, "Workers scheduled + foreground service started")
    }

    /** Cancel all coroutines. Call when stopping kiosk service. */
    fun cancel() {
        scope?.cancel()
        scope = null
    }
}
