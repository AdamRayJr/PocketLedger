package com.adam.pocketledger

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

object PdfStatementReader {
    fun read(context: Context, uri: Uri): String = context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Could not open the selected PDF" }
        PDDocument.load(input).use { PDFTextStripper().getText(it) }
    }
}
