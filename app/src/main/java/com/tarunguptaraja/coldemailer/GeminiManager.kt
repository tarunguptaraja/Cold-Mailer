package com.tarunguptaraja.coldemailer

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiManager(private val apiKey: String) {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    suspend fun analyzeJD(
        input: Any,
        resumeText: String,
        userProfile: ProfileData
    ): AnalysisResult? = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are an expert recruitment assistant. 
                I will provide you with a Job Description (JD) either as text or an image.
                Your task:
                1. Extract the primary email address(es) mentioned for application.
                2. Extract the company name and role name.
                3. Based on the User's Resume and the JD, write TWO emails:
                   - AN INITIAL COLD EMAIL: Personalize it by matching specific achievements from the Resume to requirements in the JD. Mention 2-3 specific skills/projects. Use professional, clear formatting.
                   - A FOLLOW-UP EMAIL: A polite follow-up.
                
                IMPORTANT FORMATTING RULES:
                - Use PLAIN TEXT ONLY. Do not use Markdown (no **, #, or * for lists). 
                - For lists/bullets, use a simple "-" character.
                - Use double newlines between paragraphs for clear spacing.
                
                User's Resume Text:
                $resumeText
                
                User's Name: ${userProfile.name}
                
                Return the result IN JSON FORMAT:
                {
                  "emails": ["email1", "email2"],
                  "company": "Company Name",
                  "role": "Role Name",
                  "initialBody": "The personalized initial cold email",
                  "followUpBody": "The personalized follow-up email"
                }
            """.trimIndent()

            val response = when (input) {
                is String -> generativeModel.generateContent(content {
                    text(prompt)
                    text("JD Source: $input")
                })
                is Bitmap -> generativeModel.generateContent(content {
                    text(prompt)
                    image(input)
                })
                else -> null
            }

            response?.text?.let { resultText ->
                Log.d("GeminiManager", "Raw Response: $resultText")
                // Extract JSON from the response text
                val jsonRegex = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL)
                val jsonMatch = jsonRegex.find(resultText)?.value
                
                if (jsonMatch != null) {
                    parseResult(jsonMatch)
                } else {
                    Log.e("GeminiManager", "No JSON found in response")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiManager", "Error analyzing JD", e)
            null
        }
    }

    private fun parseResult(json: String): AnalysisResult {
        // Simple manual parsing for demo purposes
        fun unescape(text: String): String {
            return text.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .trim()
        }

        val emails = Regex("\"emails\":\\s*\\[(.*?)\\]").find(json)?.groupValues?.get(1)
            ?.split(",")?.map { it.trim().removeSurrounding("\"") } ?: emptyList()
            
        val company = unescape(Regex("\"company\":\\s*\"(.*?)\"").find(json)?.groupValues?.get(1) ?: "")
        val role = unescape(Regex("\"role\":\\s*\"(.*?)\"").find(json)?.groupValues?.get(1) ?: "")
        
        val initialBody = unescape(
            Regex("\"initialBody\":\\s*\"(.*?)\"", RegexOption.DOT_MATCHES_ALL)
                .find(json)?.groupValues?.get(1) ?: ""
        )
        
        val followUpBody = unescape(
            Regex("\"followUpBody\":\\s*\"(.*?)\"", RegexOption.DOT_MATCHES_ALL)
                .find(json)?.groupValues?.get(1) ?: ""
        )

        return AnalysisResult(emails, company, role, initialBody, followUpBody)
    }

    data class AnalysisResult(
        val emails: List<String>,
        val company: String,
        val role: String,
        val initialBody: String,
        val followUpBody: String
    )
}
