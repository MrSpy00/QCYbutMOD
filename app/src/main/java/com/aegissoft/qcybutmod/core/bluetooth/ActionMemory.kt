package com.aegissoft.qcybutmod.core.bluetooth

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class ActionSnapshot(
    val feature: String,
    val targetDevice: String,
    val attemptedAt: Long,
    val success: Boolean,
    val detail: String = ""
)

data class SyncInfo(
    val lastSyncAt: Long? = null,
    val lastModelId: Int = 0,
    val lastFirmware: String = "",
    val lastBudsSeenAt: Long? = null
)

/**
 * Son 20 hareket + son senkron bilgisini hafızada (StateFlow) ve diske (action_history.json + qcy_state prefs) tutar.
 * Tüm disk yazmaları best-effort; başarısız olsa bile uygulama akışı etkilenmez.
 */
class ActionMemory(private val context: Context) {

    companion object {
        private const val FILE_NAME = "action_history.json"
        private const val PREFS = "qcy_state"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_MODEL_ID = "last_model_id"
        private const val KEY_LAST_FIRMWARE = "last_firmware"
        private const val KEY_LAST_BUDS_SEEN_AT = "last_buds_seen_at"
        const val HISTORY_LIMIT = 20
        private val gson = Gson()

        /** JVM testleri için saf JSON/sınır mantığı. */
        fun parseHistory(json: String?): List<ActionSnapshot> = runCatching {
            gson.fromJson(json, Array<ActionSnapshot>::class.java)?.toList() ?: emptyList()
        }.getOrDefault(emptyList())

        fun appendCapped(history: List<ActionSnapshot>, item: ActionSnapshot): List<ActionSnapshot> =
            (listOf(item) + history).take(HISTORY_LIMIT)
    }

    private val historyFile = File(context.filesDir, FILE_NAME)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<ActionSnapshot>> = _history.asStateFlow()

    private val _lastAction = MutableStateFlow(_history.value.firstOrNull())
    val lastAction: StateFlow<ActionSnapshot?> = _lastAction.asStateFlow()

    private val _syncInfo = MutableStateFlow(loadSyncInfo())
    val syncInfo: StateFlow<SyncInfo> = _syncInfo.asStateFlow()

    fun record(feature: String, targetDevice: String, success: Boolean, detail: String = "") {
        val snapshot = ActionSnapshot(
            feature = feature,
            targetDevice = targetDevice.ifBlank { "QCY kulaklık" },
            attemptedAt = System.currentTimeMillis(),
            success = success,
            detail = detail
        )
        val updated = appendCapped(_history.value, snapshot)
        _history.value = updated
        _lastAction.value = snapshot
        runCatching { historyFile.writeText(gson.toJson(updated)) }
        runCatching {
            prefs.edit().putString("last_action", gson.toJson(snapshot)).apply()
        }
    }

    fun markSynced(modelId: Int, firmware: String) {
        val now = System.currentTimeMillis()
        val info = SyncInfo(
            lastSyncAt = now,
            lastModelId = modelId,
            lastFirmware = firmware,
            lastBudsSeenAt = now
        )
        _syncInfo.value = info
        runCatching {
            prefs.edit()
                .putLong(KEY_LAST_SYNC_AT, now)
                .putInt(KEY_LAST_MODEL_ID, modelId)
                .putString(KEY_LAST_FIRMWARE, firmware)
                .putLong(KEY_LAST_BUDS_SEEN_AT, now)
                .apply()
        }
    }

    fun clear() {
        _history.value = emptyList()
        _lastAction.value = null
        runCatching { if (historyFile.exists()) historyFile.delete() }
        runCatching { prefs.edit().remove("last_action").apply() }
    }

    private fun loadHistory(): List<ActionSnapshot> =
        runCatching {
            if (historyFile.exists()) parseHistory(historyFile.readText()) else emptyList()
        }.getOrDefault(emptyList())

    private fun loadSyncInfo(): SyncInfo = runCatching {
        val lastSync = if (prefs.contains(KEY_LAST_SYNC_AT)) prefs.getLong(KEY_LAST_SYNC_AT, 0L) else null
        SyncInfo(
            lastSyncAt = lastSync,
            lastModelId = prefs.getInt(KEY_LAST_MODEL_ID, 0),
            lastFirmware = prefs.getString(KEY_LAST_FIRMWARE, "") ?: "",
            lastBudsSeenAt = if (prefs.contains(KEY_LAST_BUDS_SEEN_AT)) prefs.getLong(KEY_LAST_BUDS_SEEN_AT, 0L) else null
        )
    }.getOrDefault(SyncInfo())
}
