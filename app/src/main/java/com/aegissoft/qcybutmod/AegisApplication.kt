package com.aegissoft.qcybutmod

import android.app.Application
import com.aegissoft.qcybutmod.core.models.ProductDatabase
import com.aegissoft.qcybutmod.core.service.QcyBackgroundService
import com.aegissoft.qcybutmod.core.utils.AegisLogger

class AegisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AegisLogger.init(this)
        // Initialize the 199+ device models database from raw JSON
        ProductDatabase.initFromRaw(this)
    }
}
