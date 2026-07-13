package com.facegate.kioskscanner.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.facegate.core.data.local.DevicePreferences
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.DevicePingRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class DevicePingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiService: ApiService,
    private val devicePreferences: DevicePreferences
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "DevicePingWorker"
        const val UNIQUE_NAME = "device_ping"
        private const val PING_INTERVAL_MINUTES = 30L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DevicePingWorker>(
                PING_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val deviceId = devicePreferences.getDeviceId()
            if (deviceId != null) {
                val response = apiService.pingDeviceWithBattery(
                    deviceId, DevicePingRequest(batteryLevel = null)
                )
                if (response.isSuccessful) {
                    Log.d(TAG, "Ping success: $deviceId")
                    Result.success()
                } else {
                    Log.w(TAG, "Ping failed: ${response.code()}")
                    Result.retry()
                }
            } else {
                Log.w(TAG, "No device ID for ping")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ping error", e)
            Result.retry()
        }
    }
}
