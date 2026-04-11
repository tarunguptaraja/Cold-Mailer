package com.tarunguptaraja.coldemailer

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ResumeParser {

    fun init(context: Context) {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun extractText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            text ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
