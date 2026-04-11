package com.tarunguptaraja.coldemailer
 
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.tarunguptaraja.coldemailer.domain.model.JobAnalysis
import com.tarunguptaraja.coldemailer.domain.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiManager @Inject constructor(private val apiKey: String) {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    suspend fun analyzeJD(
        input: Any,
        resumeText: String,
        userProfile: Profile
    ): JobAnalysis? = withContext(Dispatchers.IO) {
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
            val errorMessage = e.message ?: ""
            if (errorMessage.contains("403") || errorMessage.contains("API key")) {
                Log.e("GeminiManager", "Authentication Error: Please check your API key. It may be invalid or leaked.", e)
            } else if (e is kotlinx.serialization.SerializationException) {
                Log.e("GeminiManager", "Serialization Error: This may be due to a known SDK bug when parsing error responses.", e)
            } else {
                Log.e("GeminiManager", "Error analyzing JD: $errorMessage", e)
            }
            null
        }
    }

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun parseResult(json: String): JobAnalysis? {
        return try {
            jsonParser.decodeFromString<JobAnalysis>(json)
        } catch (e: Exception) {
            Log.e("GeminiManager", "Error parsing JSON: ${e.message}", e)
            null
        }
    }
}
