package com.aegissoft.qcybutmod.core.translate

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class JournalSession(
    val sessionId: String,
    val startedAt: Long,
    val items: List<TranscriptItem>
)

/**
 * Çeviri günlüğü: oturumları tek bir JSON dosyasında (journal.json) saklar.
 * Oturum başına 50 sınırı append sırasında uygulanır (eski oturumlar FIFO düşer).
 */
class TranslateJournalStore(private val dir: File) {

    companion object {
        private const val TAG = "TranslateJournalStore"
        private const val FILE_NAME = "journal.json"
        private const val MAX_SESSIONS = 50

        fun default(context: Context) =
            TranslateJournalStore(File(context.filesDir, "translate_journal"))
    }

    private val gson = Gson()
    private val file = File(dir, FILE_NAME)
    private val listType = object : TypeToken<List<JournalSession>>() {}.type

    /** Boş liste → null, dosyaya dokunulmaz. Dizini kendisi oluşturur. */
    suspend fun append(
        items: List<TranscriptItem>,
        sessionId: String = UUID.randomUUID().toString(),
        startedAt: Long = System.currentTimeMillis()
    ): JournalSession? = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext null
        val session = JournalSession(sessionId = sessionId, startedAt = startedAt, items = items)
        val sessions = readAll().toMutableList()
        sessions.add(session)
        while (sessions.size > MAX_SESSIONS) sessions.removeAt(0) // FIFO: en eski düşer
        writeAll(sessions)
        session
    }

    /** En yeni oturum başta. */
    suspend fun load(): List<JournalSession> = withContext(Dispatchers.IO) {
        readAll().asReversed()
    }

    /** original +translated metinlerde büyük/küçük harf duyarsız arama. */
    suspend fun search(query: String): List<Pair<JournalSession, TranscriptItem>> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            readAll().asReversed().flatMap { session ->
                session.items
                    .filter {
                        it.originalText.contains(query, ignoreCase = true) ||
                            it.translatedText.contains(query, ignoreCase = true)
                    }
                    .map { session to it }
            }
        }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        val sessions = readAll()
        val remaining = sessions.filterNot { it.sessionId == sessionId }
        if (remaining.size != sessions.size) writeAll(remaining)
    }

    /** Bozuk/okunamaz dosyada sessizce boş döner (fırlatmaz). */
    private fun readAll(): List<JournalSession> {
        if (!file.exists()) return emptyList()
        return try {
            gson.fromJson<List<JournalSession>>(file.readText(), listType) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Journal dosyası okunamadı, yok sayılıyor", e)
            emptyList()
        }
    }

    // shortcut: doğrudan yerinde yazım — süreç ölümü anında dosya yarım kalırsa load()
    // boş döner; upgrade gerekiyorsa yaz-sonra-rename (tmp dosya + atomicMove) kullan.
    private fun writeAll(sessions: List<JournalSession>) {
        dir.mkdirs()
        file.writeText(gson.toJson(sessions, listType))
    }
}
