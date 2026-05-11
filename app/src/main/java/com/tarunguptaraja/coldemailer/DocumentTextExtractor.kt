package com.tarunguptaraja.coldemailer

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentTextExtractor @Inject constructor(@ApplicationContext private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    data class TextExtractionResult(
        val text: String,
        val fileType: String
    )

    suspend fun extractText(uri: Uri, fileName: String): TextExtractionResult = withContext(Dispatchers.IO) {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        
        when (extension) {
            "pdf" -> extractFromPdf(uri)
            "txt" -> extractFromTxt(uri)
            "docx" -> extractFromDocx(uri)
            else -> TextExtractionResult("", "unknown")
        }
    }

    private suspend fun extractFromPdf(uri: Uri): TextExtractionResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            TextExtractionResult(text ?: "", "pdf")
        } catch (e: Exception) {
            e.printStackTrace()
            TextExtractionResult("", "pdf")
        }
    }

    private suspend fun extractFromTxt(uri: Uri): TextExtractionResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val text = reader.use { it.readText() }
            TextExtractionResult(text, "txt")
        } catch (e: Exception) {
            e.printStackTrace()
            TextExtractionResult("", "txt")
        }
    }

    private suspend fun extractFromDocx(uri: Uri): TextExtractionResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val zipInputStream = ZipInputStream(inputStream)
            val stringBuilder = StringBuilder()
            
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val content = zipInputStream.bufferedReader().use { it.readText() }
                    // Basic XML tag stripping - extract text between <w:t> tags
                    val regex = "<w:t[^>]*>([^<]*)</w:t>".toRegex()
                    val matches = regex.findAll(content)
                    matches.forEach { match ->
                        stringBuilder.append(match.groupValues[1]).append(" ")
                    }
                    break
                }
                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()
            
            TextExtractionResult(stringBuilder.toString().trim(), "docx")
        } catch (e: Exception) {
            e.printStackTrace()
            TextExtractionResult("", "docx")
        }
    }

    companion object {
        fun getSupportedMimeTypes(): Array<String> = arrayOf(
            "application/pdf",
            "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
        
        fun getSupportedExtensions(): List<String> = listOf("pdf", "txt", "docx")
    }
}
