package com.aegissoft.qcybutmod.core.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AegisLogger {
    private const val MAX_LOG_SIZE = 512 * 1024 // 512 KB
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private var logFile: File? = null

    fun init(context: Context) {
        logFile = File(context.filesDir, "qcy_debug.log")
        if (logFile?.exists() == true && (logFile?.length() ?: 0) > MAX_LOG_SIZE) {
            logFile?.delete()
        }
        i("AegisLogger", "=== AegisLogger Initialized ===")
    }

    private fun writeLine(level: String, tag: String, msg: String, tr: Throwable? = null) {
        val time = dateFormat.format(Date())
        val line = buildString {
            append("$time [$level/$tag] $msg\n")
            if (tr != null) {
                append(Log.getStackTraceString(tr))
                append("\n")
            }
        }
        val file = logFile ?: return
        try {
            FileWriter(file, true).use { writer ->
                writer.write(line)
            }
        } catch (ignored: Exception) {}
    }

    fun d(tag: String, msg: String) {
        try { Log.d(tag, msg) } catch (_: Throwable) {}
        writeLine("D", tag, msg)
    }

    fun i(tag: String, msg: String) {
        try { Log.i(tag, msg) } catch (_: Throwable) {}
        writeLine("I", tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        try { Log.w(tag, msg, tr) } catch (_: Throwable) {}
        writeLine("W", tag, msg, tr)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        try { Log.e(tag, msg, tr) } catch (_: Throwable) {}
        writeLine("E", tag, msg, tr)
    }

    fun getLogText(): String {
        val file = logFile ?: return "Log dosyası hazır değil"
        return try {
            if (file.exists()) file.readText() else "Log dosyası boş"
        } catch (e: Exception) {
            "Log okunamadı: ${e.message}"
        }
    }

    fun clear() {
        val file = logFile ?: return
        try {
            if (file.exists()) file.delete()
            i("AegisLogger", "=== Loglar Temizlendi ===")
        } catch (ignored: Exception) {}
    }
}
