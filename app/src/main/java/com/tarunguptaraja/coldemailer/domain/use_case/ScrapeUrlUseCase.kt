package com.tarunguptaraja.coldemailer.domain.use_case

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import javax.inject.Inject

class ScrapeUrlUseCase @Inject constructor() {
    suspend operator fun invoke(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Using Jsoup to connect and fetch the text
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get()
            
            // Extract all text from the body
            val text = doc.body().text()
            if (text.isNotBlank()) {
                Result.success(text)
            } else {
                Result.failure(Exception("No readable text found on this page."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
