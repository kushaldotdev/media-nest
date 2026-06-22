package com.example.medianest.data.sync

import com.example.medianest.data.preferences.DevicePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val devicePreferences: DevicePreferences
) {
    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
    }

    private val client = okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun register(serverUrl: String, deviceName: String = ""): Result<RegisterResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(RegisterRequest(deviceName)).toRequestBody(JSON_MEDIA)
                val request = Request.Builder()
                    .url("$serverUrl/device/register")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw Exception("Empty response")
                json.decodeFromString<RegisterResponse>(responseBody)
            }
        }

    suspend fun pushChanges(serverUrl: String, apiKey: String, deviceId: String, changes: List<SyncPushItem>): Result<SyncPushResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(SyncPushRequest(deviceId, changes)).toRequestBody(JSON_MEDIA)
                val request = Request.Builder()
                    .url("$serverUrl/sync/push")
                    .post(body)
                    .header("X-API-Key", apiKey)
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw Exception("Empty response")
                json.decodeFromString<SyncPushResponse>(responseBody)
            }
        }

    suspend fun pullChanges(serverUrl: String, apiKey: String, deviceId: String, afterVersion: Long = 0): Result<SyncPullResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$serverUrl/sync/pull?device_id=$deviceId&after_version=$afterVersion&limit=100")
                    .get()
                    .header("X-API-Key", apiKey)
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw Exception("Empty response")
                json.decodeFromString<SyncPullResponse>(responseBody)
            }
        }
}
