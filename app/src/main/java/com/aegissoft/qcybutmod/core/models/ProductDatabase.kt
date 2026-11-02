package com.aegissoft.qcybutmod.core.models

import android.content.Context
import com.aegissoft.qcybutmod.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

data class ProductInfo(
    val vendorId: Int,
    val title: String,
    val subTitle: String,
    val category: String = "earphones",
    val features: Map<String, Any>? = null
)

object ProductDatabase {
    private val memoryDb = HashMap<Int, ProductInfo>()

    init {
        // Built-in high priority device definitions
        register(ProductInfo(19786, "QCY MeloBuds Pro", "HT08"))
        register(ProductInfo(19785, "QCY MeloBuds Pro", "HT08"))
        register(ProductInfo(19790, "QCY MeloBuds Pro", "HT08"))
        register(ProductInfo(19777, "QCY Melobuds ANC", "HT05"))
        register(ProductInfo(19778, "QCY TWS HT05", "HT05"))
        register(ProductInfo(16849, "QCY H4", "H4"))
        register(ProductInfo(16850, "QCY H4 ANC HEADSET", "H4"))
        register(ProductInfo(32883, "QCY HT16 MeloBuds", "HT16"))
        register(ProductInfo(19059, "QCY T13", "T13"))
        register(ProductInfo(19825, "QCY T13 ANC", "T13 ANC"))
        register(ProductInfo(19218, "QCY T16", "T16"))
        register(ProductInfo(19393, "QCY HT03", "HT03"))
        register(ProductInfo(23877, "QCY MeloBuds N70", "N70"))
    }

    private fun register(info: ProductInfo) {
        memoryDb[info.vendorId] = info
    }

    fun initFromRaw(context: Context) {
        try {
            val inputStream = context.resources.openRawResource(R.raw.products)
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
            val rawMap: Map<String, Map<String, Any>> = Gson().fromJson(reader, type)
            for ((key, value) in rawMap) {
                val vid = key.toIntOrNull() ?: continue
                val title = value["title"] as? String ?: "QCY Earphone"
                val subTitle = value["subTitle"] as? String ?: ""
                val category = value["category"] as? String ?: "earphones"
                @Suppress("UNCHECKED_CAST")
                val features = value["features"] as? Map<String, Any>
                memoryDb[vid] = ProductInfo(vid, title, subTitle, category, features)
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getProduct(vendorId: Int): ProductInfo {
        return memoryDb[vendorId] ?: ProductInfo(vendorId, "QCY Aygıtı (0x${vendorId.toString(16).uppercase()})", "")
    }

    fun findProductByName(name: String): ProductInfo? {
        if (!com.aegissoft.qcybutmod.core.bluetooth.BleAdvertisementParser.isQcyDeviceName(name)) {
            return null
        }

        val cleanName = name.trim().lowercase().replace(" ", "").replace("-", "")

        // 1. Exact or strong match by full title (cleanTitle must be at least 4 characters to prevent false substring hits)
        val matchByTitle = memoryDb.values.firstOrNull { product ->
            val cleanTitle = product.title.trim().lowercase().replace(" ", "").replace("-", "")
            cleanTitle.isNotEmpty() && (
                cleanName == cleanTitle || 
                (cleanTitle.length >= 4 && cleanName.contains(cleanTitle))
            )
        }
        if (matchByTitle != null) return matchByTitle

        // 2. Strict model code matching (e.g. HT08, HT05, H4, T13)
        // Must match as word boundary or start of name to prevent false substring hits (e.g. "fit4" -> "t4")
        return memoryDb.values.firstOrNull { product ->
            val cleanSub = product.subTitle.trim().lowercase().replace(" ", "").replace("-", "")
            if (cleanSub.isEmpty()) false
            else {
                val pattern = Regex("(?:^|[^a-zA-Z0-9])" + Regex.escape(cleanSub) + "(?:$|[^a-zA-Z0-9])", RegexOption.IGNORE_CASE)
                pattern.containsMatchIn(name) || cleanName.startsWith(cleanSub) || cleanName.endsWith(cleanSub)
            }
        }
    }

    fun getAllProducts(): List<ProductInfo> = memoryDb.values.toList()

    fun isAncSupported(vendorId: Int): Boolean {
        val product = memoryDb[vendorId] ?: return vendorId in listOf(19786, 19785, 19790, 19777, 19778, 16849, 16850, 19825, 32883)
        val features = product.features ?: return vendorId in listOf(19786, 19785, 19790, 19777, 19778, 16849, 16850, 19825, 32883)
        return features.containsKey("anc")
    }

    fun isLdacSupported(vendorId: Int): Boolean {
        return vendorId in listOf(19786, 19785, 19790, 16849, 16850, 23877)
    }
}
