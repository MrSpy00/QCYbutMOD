package com.aegissoft.qcybutmod.core.translate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class TranslateJournalStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val turkish = SupportedLanguage("tr", "Türkçe", "Türkçe", "TR")
    private val english = SupportedLanguage("en", "İngilizce", "English", "EN")

    private fun item(
        original: String,
        translated: String,
        id: String = UUID.randomUUID().toString(),
        timestamp: Long = 1_700_000_000_000L,
        headset: Boolean = true
    ) = TranscriptItem(
        id = id,
        originalText = original,
        translatedText = translated,
        sourceLang = turkish,
        targetLang = english,
        timestamp = timestamp,
        isFromHeadsetMic = headset
    )

    @Test
    fun appendAndLoadRoundtrip() = runBlocking {
        val store = TranslateJournalStore(tempFolder.newFolder())
        val a = item("Merhaba", "Hello", id = "id-a", timestamp = 111L, headset = true)
        val b = item("Günaydın", "Good morning", id = "id-b", timestamp = 222L, headset = false)

        val appended = store.append(listOf(a, b))
        assertNotNull(appended)

        val sessions = store.load()
        assertEquals(1, sessions.size)
        val loaded = sessions.first()
        assertNotNull(loaded.sessionId)
        assertTrue(loaded.startedAt > 0L)
        assertEquals(2, loaded.items.size)

        // Alanlar birebir korunmalı
        val la = loaded.items[0]
        assertEquals("id-a", la.id)
        assertEquals("Merhaba", la.originalText)
        assertEquals("Hello", la.translatedText)
        assertEquals(turkish, la.sourceLang)
        assertEquals(english, la.targetLang)
        assertEquals(111L, la.timestamp)
        assertTrue(la.isFromHeadsetMic)

        val lb = loaded.items[1]
        assertEquals("id-b", lb.id)
        assertEquals("Günaydın", lb.originalText)
        assertEquals("Good morning", lb.translatedText)
        assertEquals(turkish, lb.sourceLang)
        assertEquals(english, lb.targetLang)
        assertEquals(222L, lb.timestamp)
        assertFalse(lb.isFromHeadsetMic)
    }

    @Test
    fun loadOrdersNewestFirst() = runBlocking {
        val store = TranslateJournalStore(tempFolder.newFolder())
        val first = store.append(listOf(item("bir", "one")))!!
        val second = store.append(listOf(item("iki", "two")))!!
        val third = store.append(listOf(item("üç", "three")))!!

        val ids = store.load().map { it.sessionId }
        assertEquals(listOf(third.sessionId, second.sessionId, first.sessionId), ids)
    }

    @Test
    fun searchMatchesOriginalOrTranslatedCaseInsensitive() = runBlocking {
        val store = TranslateJournalStore(tempFolder.newFolder())
        store.append(listOf(item("merhaba dünya", "hello world")))

        // Büyük harf sorgu, Türkçe/original metni bulur
        val byOriginal = store.search("MERHABA")
        assertEquals(1, byOriginal.size)
        assertEquals("merhaba dünya", byOriginal.first().second.originalText)

        // Ve çeviri metni üzerinden de bulunur (yine ignoreCase)
        val byTranslated = store.search("HELLO")
        assertEquals(1, byTranslated.size)
        assertEquals("hello world", byTranslated.first().second.translatedText)
    }

    @Test
    fun searchSpansMultipleSessions() = runBlocking {
        val store = TranslateJournalStore(tempFolder.newFolder())
        val s1 = store.append(listOf(item("birinci oturum metni", "first session text")))!!
        val s2 = store.append(listOf(item("ikinci oturum", "second session")))!!
        // Eşleşmeyen öğe başka oturumda
        store.append(listOf(item("tamamen farklı", "totally different")))

        val hits = store.search("OTURUM")
        assertEquals(2, hits.size)
        assertEquals(
            setOf(s1.sessionId, s2.sessionId),
            hits.map { it.first.sessionId }.toSet()
        )
    }

    @Test
    fun deleteSessionRemovesOnlyThatSession() = runBlocking {
        val store = TranslateJournalStore(tempFolder.newFolder())
        val first = store.append(listOf(item("bir", "one")))!!
        val second = store.append(listOf(item("iki", "two")))!!
        val third = store.append(listOf(item("üç", "three")))!!

        store.deleteSession(second.sessionId)

        val remaining = store.load()
        assertEquals(2, remaining.size)
        assertEquals(listOf(third.sessionId, first.sessionId), remaining.map { it.sessionId })
    }

    @Test
    fun appendSkipsEmpty() = runBlocking {
        val dir = tempFolder.newFolder()
        val store = TranslateJournalStore(dir)

        assertNull(store.append(emptyList()))

        // Hiç dosya oluşmamalı
        assertEquals(0, dir.listFiles()!!.size)
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun capKeeps50Sessions() = runBlocking {
        val store = TranslateJournalStore(tempFolder.newFolder())
        val ids = (1..51).map { n ->
            store.append(listOf(item("metin $n", "text $n")))!!.sessionId
        }

        val sessions = store.load()
        assertEquals(50, sessions.size)
        // En yeniler kalmalı: 51. eklenen başta, ilk eklenen düşmüş olmalı
        assertEquals(ids[50], sessions.first().sessionId)
        assertFalse(sessions.any { it.sessionId == ids[0] })
        assertTrue(sessions.any { it.sessionId == ids[49] })
    }
}
