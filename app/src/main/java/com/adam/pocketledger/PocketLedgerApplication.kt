package com.adam.pocketledger

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PocketLedgerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
