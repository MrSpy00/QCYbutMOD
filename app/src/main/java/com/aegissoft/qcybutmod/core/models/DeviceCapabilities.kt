package com.aegissoft.qcybutmod.core.models

enum class TouchSides { BOTH, LEFT, RIGHT }

data class DeviceCapabilities(
    val vendorId: Int,
    val hasAnc: Boolean,
    val hasLdac: Boolean,
    val hasMultipoint: Boolean,
    val hasFind: Boolean,
    val hasLongPress: Boolean,
    val touchSides: TouchSides,
    val eqBands: Int
)

object CapabilityParser {
    // shortcut: products.json yan (touch side) bilgisini içermiyor; panel JSON verisi geldiğinde güncelle
    private val rightTouchOnlyVendorIds = setOf(16849, 16850)

    fun parse(vendorId: Int, features: Map<String, Any>?, ldacSupported: Boolean): DeviceCapabilities {
        val hasAnc = if (features == null) ProductDatabase.isAncSupported(vendorId)
        else features["anc"] != null
        // Bilinmeyen cihazda varsayılan açık (MeloBuds Pro canlı test edildi); settings VERİLDİYSE cmdid 36 aranır
        val hasMultipoint = features == null ||
            features["settings"] == null ||
            readSettings(features).any { cmdidOf(it) == 36 }
        val hasFind = features == null || features["find_earphone"] == true
        val events = (features?.get("key_function") as? Map<*, *>)?.get("events") as? List<*>
        // key_function yoksa bilinmiyor → göster (MeloBuds Pro 0x2B tablosu 7/8 anahtarlarını canlı kanıtladı)
        val hasLongPress = events == null || events.any { event ->
            ((event as? Map<*, *>)?.get("name") as? String)?.contains("long", ignoreCase = true) == true
        }
        val eqBands = ((features?.get("eq") as? Map<*, *>)?.get("bands") as? Number)?.toInt() ?: 10
        return DeviceCapabilities(
            vendorId = vendorId,
            hasAnc = hasAnc,
            hasLdac = ldacSupported,
            hasMultipoint = hasMultipoint,
            hasFind = hasFind,
            hasLongPress = hasLongPress,
            touchSides = if (vendorId in rightTouchOnlyVendorIds) TouchSides.RIGHT else TouchSides.BOTH,
            eqBands = eqBands
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun readSettings(features: Map<String, Any>): List<Any?> =
        features["settings"] as? List<Any?> ?: emptyList()

    private fun cmdidOf(entry: Any?): Int? = ((entry as? Map<*, *>)?.get("cmdid") as? Number)?.toInt()

    // Tanınamayan model: koruyucu varsayılan — her özellik açık denenir, LDAC yalnızca bilinen listeyle
    fun unknownModel(vendorId: Int, ldacSupported: Boolean): DeviceCapabilities =
        DeviceCapabilities(
            vendorId = vendorId,
            hasAnc = true,
            hasLdac = ldacSupported,
            hasMultipoint = true,
            hasFind = true,
            hasLongPress = true,
            touchSides = TouchSides.BOTH,
            eqBands = 10
        )
}

fun DeviceCapabilities.longPressAvailable(keyMappings: Map<Int, Int>): Boolean =
    hasLongPress || 7 in keyMappings || 8 in keyMappings
