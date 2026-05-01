package com.tarunguptaraja.coldemailer

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tarunguptaraja.coldemailer.domain.model.JobAnalysis
import com.tarunguptaraja.coldemailer.domain.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiManager @Inject constructor(
    private val apiKey: String,
    private val crashlytics: FirebaseCrashlytics
) {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    suspend fun analyzeJD(
        input: Any,
        resumeText: String,
        userProfile: Profile,
        tone: String = "Professional"
    ): JobAnalysis? = withContext(Dispatchers.IO) {
        Log.d("GeminiManager", "analyzeJD called. Input type: ${if (input is Bitmap) "Bitmap" else "String"}")
        try {
            val prompt = """
                You are an expert recruitment assistant and high-conversion cold email writer. 
                I will provide you with a Job Description (JD) and a User Resume.
                Your goal is to write a cold email that guarantees a response from the recruiter.

                Your task:
                1. Extract the primary email address(es) mentioned for application.
                2. Extract the company name and role name.
                3. Act as an ATS and score the User's Resume against the JD (0-100).
                4. Provide 2-3 brief bullet points of feedback on missing skills.
                5. Based on the User's Resume and the JD, write a $tone tone email:
                   - A SUBJECT LINE: Catchy, personal, and professional.
                   - AN INITIAL COLD EMAIL: 
                        * STRICTLY 2-3 short paragraphs max.
                        * Extremely positive and enthusiastic.
                        * FOCUS ON TRANSFERABLE SKILLS: If the user's background is different from the JD (e.g., Android dev applying for Backend), do NOT mention the current irrelevant title. Instead, highlight the shared technical skills (e.g., Java, problem-solving, architecture) and how they apply to the target role.
                        * Make it feel like they are already a perfect fit for the specific team.
                   - A FOLLOW-UP EMAIL: A very brief, polite 1-2 sentence follow-up.
                
                IMPORTANT FORMATTING RULES:
                - Return ONLY a valid JSON object.
                - JSON keys: "emails" (array of strings), "company" (string), "role" (string), "atsScore" (number), "atsFeedback" (array of strings), "subject" (string), "initialBody" (string), "followUpBody" (string).
                - Use \n for newlines in the body strings.
            """.trimIndent()

            val content = if (input is Bitmap) {
                content {
                    image(input)
                    text(prompt)
                    text("User Resume Context:\n$resumeText")
                }
            } else {
                content {
                    text(prompt)
                    text("Job Description:\n$input")
                    text("User Resume Context:\n$resumeText")
                }
            }

            val response = generativeModel.generateContent(content)
            val responseText = response.text ?: ""
            Log.d("GeminiManager", "Response received: $responseText")

            // Extract JSON from the response text
            val jsonRegex = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = jsonRegex.find(responseText)?.value
            
            if (jsonMatch != null) {
                val jsonObject = JSONObject(jsonMatch)
                
                val emails = mutableListOf<String>()
                val emailsArray = jsonObject.optJSONArray("emails")
                if (emailsArray != null) {
                    for (i in 0 until emailsArray.length()) {
                        emails.add(emailsArray.getString(i))
                    }
                }

                val feedback = mutableListOf<String>()
                val feedbackArray = jsonObject.optJSONArray("atsFeedback")
                if (feedbackArray != null) {
                    for (i in 0 until feedbackArray.length()) {
                        feedback.add(feedbackArray.getString(i))
                    }
                }

                JobAnalysis(
                    emails = emails,
                    company = jsonObject.optString("company"),
                    role = jsonObject.optString("role"),
                    atsScore = jsonObject.optInt("atsScore"),
                    atsFeedback = feedback,
                    subject = jsonObject.optString("subject"),
                    initialBody = jsonObject.optString("initialBody"),
                    followUpBody = jsonObject.optString("followUpBody")
                )
            } else {
                Log.e("GeminiManager", "No JSON found in response")
                null
            }
        } catch (e: Exception) {
            Log.e("GeminiManager", "Error analyzing JD: ${e.message}", e)
            crashlytics.recordException(e)
            null
        }
    }
}
