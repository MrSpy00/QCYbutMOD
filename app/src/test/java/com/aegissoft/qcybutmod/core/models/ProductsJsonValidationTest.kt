package com.aegissoft.qcybutmod.core.models

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProductsJsonValidationTest {

    private fun productsFile(): File {
        val candidates = listOf(
            File("src/main/res/raw/products.json"),
            File("app/src/main/res/raw/products.json")
        )
        return candidates.firstOrNull { it.exists() }
            ?: candidates.first() // assertexists fails with clear path
    }

    @Test
    fun productsFileParsesAndIsWellFormed() {
        val file = productsFile()
        assertTrue("products.json bulunamadı: ${file.absolutePath}", file.exists())
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Map<String, Any>>>() {}.type
        val map: Map<String, Map<String, Any>> = Gson().fromJson(file.readText(), type)
        assertNotNull("products.json boş/bozuk", map)
        assertTrue("Beklenen min 100 cihaz, gelen: ${map.size}", map.size >= 100)
        for ((key, entry) in map) {
            val keyInt = key.toIntOrNull()
            assertNotNull("Anahtar int değil: $key", keyInt)
            val title = entry["title"] as? String
            assertFalse("title boş: $key", title.isNullOrBlank())
            val vid = entry["vendorId"]
            if (vid is Double) {
                assertEquals("vendorId anahtarla uyuşmuyor: $key", keyInt!!.toDouble(), vid, 0.0)
            }
            entry["features"]?.let {
                assertTrue("features Map değil: $key", it is Map<*, *>)
            }
            (entry["modelId"] as? Double)?.let {
                assertTrue("modelId negatif: $key", it >= 0)
            }
        }
    }

    @Test
    fun unknownModelDefaultsEverythingOn() {
        val caps = ProductDatabase.getCapabilities(9999999)
        assertTrue("Bilinmeyen modelde ANC kapalı kalmamalı", caps.hasAnc)
        assertTrue(caps.hasMultipoint)
        assertTrue(caps.hasFind)
        assertTrue(caps.hasLongPress)
        assertEquals(TouchSides.BOTH, caps.touchSides)
        assertEquals(10, caps.eqBands)
        assertFalse("Bilinmeyen modelde LDAC varsayılan kapalı", caps.hasLdac)
    }

    @Test
    fun forcedVendorIdOverridesCapabilities() {
        try {
            // 19786 MeloBuds Pro: ANC destekler
            assertTrue(ProductDatabase.getCapabilities(19786).hasAnc)
            // 19059 T13: ANC desteklemez → manuel seçim onu seçince 19786 sorgusu bile T13 gibi yanıtlar
            ProductDatabase.setForcedVendorId(19059)
            assertFalse(ProductDatabase.getCapabilities(19786).hasAnc)
            // Genel QCY Aygıtı (0) → unknownModel yolu
            ProductDatabase.setForcedVendorId(0)
            val generic = ProductDatabase.getCapabilities(19786)
            assertTrue(generic.hasAnc)
            assertEquals(TouchSides.BOTH, generic.touchSides)
        } finally {
            ProductDatabase.setForcedVendorId(null)
        }
        // Sıfırlama sonrası otomatik algılama geri geldi
        assertTrue(ProductDatabase.getCapabilities(19786).hasAnc)
    }
}
