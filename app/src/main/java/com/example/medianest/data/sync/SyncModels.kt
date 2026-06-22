package com.example.medianest.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RegisterRequest(val deviceName: String = "")

@Serializable
data class RegisterResponse(val deviceId: String, val apiKey: String)

@Serializable
data class SyncPushItem(val table: String, val rowId: String, val operation: String, val payload: Map<String, JsonElement>)

@Serializable
data class SyncPushRequest(val deviceId: String, val changes: List<SyncPushItem>)

@Serializable
data class SyncPushResponse(val accepted: Int)

@Serializable
data class SyncPullResponse(val version: Long, val changes: List<Map<String, JsonElement?>>, val hasMore: Boolean)
