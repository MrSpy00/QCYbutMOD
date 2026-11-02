package com.aegissoft.qcybutmod.core.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Resmi QCY panel JSON'unun (findControlPanels) firmware sürümüne göre
 * takibi ve dosya tabanlı önbelleklenmesi. Dosya adı saf mantıktır (birim
 * testinden çağrılabilir); ağ ve dosya tarafı [init] sonrası çalışır.
 */
object PanelRepository {

    sealed class Status {
        object Idle : Status()
        object Fetching : Status()
        data class Ok(val file: String) : Status()
        data class Error(val message: String) : Status()
    }

    private const val PANEL_URL = "https://api.watch.qcy.com/product/findControlPanels"

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var baseDir: File? = null

    fun init(filesDir: File) {
        baseDir = filesDir
    }

    /**
     * "panel_19786_v1.2.3.json". Düz sürümler noktalarıyla okunur kalır;
     * geçersiz karakter ([^a-zA-Z0-9._]) içeren fw tamamen sadeleştirilir
     * (her harf-dışı karakter → '_'), böylece dosya adı her koşulda güvenli olur.
     */
    fun panelFileName(vendorId: Int, firmwareVersion: String): String {
        val safeFw = if (Regex("[^a-zA-Z0-9._]").containsMatchIn(firmwareVersion)) {
            firmwareVersion.replace(Regex("[^a-zA-Z0-9]"), "_")
        } else {
            firmwareVersion
        }
        return "panel_${vendorId}_$safeFw.json"
    }

    /** Önbellekte panel dosyası var mı (boş dosya "yok" sayılır). */
    fun isCached(vendorId: Int, fw: String): Boolean {
        val dir = baseDir ?: return false
        val file = File(File(dir, "panels"), panelFileName(vendorId, fw))
        return file.exists() && file.length() > 0
    }

    /**
     * Dosya önbellekte varsa Ok döner ve ağ isteği yapmaz; yoksa resmi
     * API'ye FormBody POST atıp gövdeyi filesDir/panels/<name> yazar.
     * 2xx + boş olmayan gövde → Ok, aksi → Error. Bloklayıcı çağrılır
     * (IO coroutine'i içinden), sonuç hem [status] hem [onDone] ile bildirilir.
     */
    fun ensureCached(vendorId: Int, modelId: Int, fw: String, onDone: (Status) -> Unit) {
        val dir = baseDir
        if (dir == null) {
            finish(Status.Error("Depolama hazır değil"), onDone)
            return
        }
        val file = File(File(dir, "panels"), panelFileName(vendorId, fw))
        if (file.exists() && file.length() > 0) {
            finish(Status.Ok(file.name), onDone)
            return
        }

        finish(Status.Fetching, onDone, notifyDone = false)
        try {
            val body = FormBody.Builder()
                .add("modelId", modelId.toString())
                .add("firmwareVersion", fw)
                .build()
            val request = Request.Builder().url(PANEL_URL).post(body).build()
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful && text.isNotEmpty()) {
                    file.parentFile?.mkdirs()
                    file.writeText(text)
                    finish(Status.Ok(file.name), onDone)
                } else {
                    finish(Status.Error("HTTP ${resp.code}"), onDone)
                }
            }
        } catch (e: Exception) {
            finish(Status.Error(e.message ?: "Ağ hatası"), onDone)
        }
    }

    private fun finish(status: Status, onDone: (Status) -> Unit, notifyDone: Boolean = true) {
        _status.value = status
        if (notifyDone) onDone(status)
    }
}
