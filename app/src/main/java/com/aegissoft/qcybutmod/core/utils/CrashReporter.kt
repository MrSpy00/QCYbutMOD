package com.aegissoft.qcybutmod.core.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.aegissoft.qcybutmod.BuildConfig
import java.io.File

/**
 * Son yakalanmamış hata kaydını diske yazar ve kullanıcının paylaşmasını sağlar.
 * Kurulum hatası uygulamayı asla öldürmez: her hata yutulur ve mevcut
 * uygulama akışı olduğu gibi devam eder.
 */
object CrashReporter {
    private const val LOG_FILE = "crash_log.txt"
    private const val PREV_LOG_FILE = "crash_log_prev.txt"
    private const val MAX_SHARE_CHARS = 20_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        try {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    writeCrash(appContext, thread, throwable)
                } catch (ignored: Exception) {
                    // Diske yazma başarısız olursa sessiz kal — çökme akışı bozulmasın.
                }
                previous?.uncaughtException(thread, throwable)
            }
        } catch (ignored: Exception) {
            // Handler kurulamasa bile uygulama normal çalışmaya devam eder.
        }
    }

    fun lastCrashFile(context: Context): File? {
        val file = File(context.applicationContext.filesDir, LOG_FILE)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun clear(context: Context) {
        try {
            File(context.applicationContext.filesDir, LOG_FILE).delete()
            File(context.applicationContext.filesDir, PREV_LOG_FILE).delete()
        } catch (ignored: Exception) {
        }
    }

    /** Hata kaydı yoksa null; varsa paylaşılabilir metin Intent'i döner. */
    fun shareIntent(context: Context): Intent? {
        val file = lastCrashFile(context) ?: return null
        val text = try {
            val raw = file.readText()
            if (raw.length > MAX_SHARE_CHARS) {
                raw.take(MAX_SHARE_CHARS) + "\n\n[...kırpıldı...]"
            } else {
                raw
            }
        } catch (e: Exception) {
            return null
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "QCYbutMOD hata kaydı")
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val dir = context.filesDir
        val current = File(dir, LOG_FILE)
        val previous = File(dir, PREV_LOG_FILE)
        if (current.exists()) {
            if (previous.exists()) previous.delete()
            current.renameTo(previous)
        }

        val report = buildString {
            appendLine("QCYbutMOD hata kaydı")
            appendLine("=====================")
            appendLine("Sürüm: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Paket: ${context.packageName}")
            appendLine("Cihaz: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Çalışma süresi: ${SystemClock.elapsedRealtime()} ms")
            appendLine("Thread: ${thread.name}")
            appendLine("Tarih: ${java.util.Date()}")
            appendLine()
            appendLine(android.util.Log.getStackTraceString(throwable))
        }
        current.writeText(report)
    }
}
