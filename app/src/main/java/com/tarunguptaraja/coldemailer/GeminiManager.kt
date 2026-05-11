package com.tarunguptaraja.coldemailer

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tarunguptaraja.coldemailer.domain.model.AnswerType
import com.tarunguptaraja.coldemailer.domain.model.AtsReport
import com.tarunguptaraja.coldemailer.domain.model.InterviewAnswer
import com.tarunguptaraja.coldemailer.domain.model.InterviewConfig
import com.tarunguptaraja.coldemailer.domain.model.InterviewQuestion
import com.tarunguptaraja.coldemailer.domain.model.InterviewResult
import com.tarunguptaraja.coldemailer.domain.model.JobAnalysis
import com.tarunguptaraja.coldemailer.domain.model.Profile
import com.tarunguptaraja.coldemailer.domain.model.QuestionAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiManager @Inject constructor(
    private val apiKey: String,
    private val crashlytics: FirebaseCrashlytics,
    private val remoteConfigManager: RemoteConfigManager
) {

    private fun getCurrentDate(): String {
        return java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.US).format(java.util.Date())
    }

    private fun getGenerativeModel(): GenerativeModel {
        var modelName = remoteConfigManager.getGeminiModelName()
        if (modelName.isBlank()) modelName = "gemini-2.5-flash"
        
        return GenerativeModel(
            modelName = modelName,
            apiKey = apiKey
        )
    }

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
                        * ANSWER JD QUESTIONS: If the Job Description explicitly asks any questions (e.g., "Are you willing to relocate?", "What is your expected salary?", "Do you have experience in X?"), explicitly provide a brief, professional answer to them in the email body based on the User's Resume. If the answer cannot be determined from the resume, provide a polite, professional placeholder answer.
                        * End the email with a signature: "Thanks,\n${userProfile.name}" and add the contact number "${userProfile.contactNumber}" on the next line if it is not empty.
                   - A FOLLOW-UP EMAIL: A very brief, polite 1-2 sentence follow-up.
                
                IMPORTANT FORMATTING RULES:
                - Return ONLY a valid JSON object.
                - JSON keys: "emails" (array of strings), "company" (string), "role" (string), "atsScore" (number), "atsFeedback" (array of strings), "subject" (string), "initialBody" (string), "followUpBody" (string).
                - Use \n for newlines in the body strings.
                
                Note: Today\'s date is ${getCurrentDate()}. Use this to calculate accurate work durations if you see "present" in the resume.
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

            val response = getGenerativeModel().generateContent(content)
            val responseText = response.text ?: ""
            val tokensUsed = response.usageMetadata?.totalTokenCount ?: 0
            Log.d("GeminiManager", "Response received ($tokensUsed tokens): $responseText")

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
                    followUpBody = jsonObject.optString("followUpBody"),
                    tokensUsed = tokensUsed
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

    suspend fun calculateAtsScore(
        jobProfile: String,
        experience: String,
        resumeText: String
    ): AtsReport? = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are an expert HR Manager and ATS algorithm. Analyze the following resume text against the target job profile and experience level.
                
                Target Job Profile: $jobProfile
                Target Years of Experience: $experience
                
                Resume Text:
                $resumeText
                
                Your task:
                1. Calculate an ATS score (0-100) based on keyword matching, experience relevance, and skill alignment.
                2. Provide a brief summary of why this score was given.
                3. Identify 2-3 specific strengths of the resume for this role.
                4. Identify 2-3 weaknesses or gaps.
                5. List missing keywords that are crucial for this role but not in the resume.
                6. Provide 2-3 actionable tips to improve the resume for this specific profile.
                
                IMPORTANT FORMATTING RULES:
                - Return ONLY a valid JSON object.
                - JSON keys: "score" (number), "summary" (string), "strengths" (array of strings), "weaknesses" (array of strings), "missingKeywords" (array of strings), "improvementTips" (array of strings).
                - Use \n for newlines in the strings.
                
                Note: Today\'s date is ${getCurrentDate()}. Use this to calculate accurate work durations if you see "present" in the resume.
            """.trimIndent()

            val content = content {
                text(prompt)
            }

            val response = getGenerativeModel().generateContent(content)
            val responseText = response.text ?: ""
            val tokensUsed = response.usageMetadata?.totalTokenCount ?: 0
            Log.d("GeminiManager", "ATS Score Response ($tokensUsed tokens): $responseText")

            val jsonRegex = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = jsonRegex.find(responseText)?.value
            
            if (jsonMatch != null) {
                val jsonObject = JSONObject(jsonMatch)
                
                val strengths = mutableListOf<String>()
                val strengthsArray = jsonObject.optJSONArray("strengths")
                strengthsArray?.let { 
                    for (i in 0 until it.length()) strengths.add(it.getString(i))
                }

                val weaknesses = mutableListOf<String>()
                val weaknessesArray = jsonObject.optJSONArray("weaknesses")
                weaknessesArray?.let {
                    for (i in 0 until it.length()) weaknesses.add(it.getString(i))
                }

                val missingKeywords = mutableListOf<String>()
                val missingKeywordsArray = jsonObject.optJSONArray("missingKeywords")
                missingKeywordsArray?.let {
                    for (i in 0 until it.length()) missingKeywords.add(it.getString(i))
                }

                val improvementTips = mutableListOf<String>()
                val improvementTipsArray = jsonObject.optJSONArray("improvementTips")
                improvementTipsArray?.let {
                    for (i in 0 until it.length()) improvementTips.add(it.getString(i))
                }

                AtsReport(
                    score = jsonObject.optInt("score"),
                    summary = jsonObject.optString("summary"),
                    strengths = strengths,
                    weaknesses = weaknesses,
                    missingKeywords = missingKeywords,
                    improvementTips = improvementTips,
                    tokensUsed = tokensUsed
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("GeminiManager", "Error calculating ATS score: ${e.message}", e)
            crashlytics.recordException(e)
            null
        }
    }

    // ==================== INTERVIEW METHODS ====================

    data class InterviewQuestionsResult(
        val questions: List<InterviewQuestion>,
        val tokensUsed: Int
    )

    suspend fun generateInterviewQuestions(
        config: InterviewConfig
    ): InterviewQuestionsResult? = withContext(Dispatchers.IO) {
        try {
            val jdContext = if (config.jobDescription.isBlank()) {
                "[No JD provided - infer requirements from the job role: ${config.jobRole} with ${config.experience} years experience]"
            } else {
                config.jobDescription
            }
            
            val prompt = """
                You are an expert technical interviewer and hiring manager. 
                Generate approximately ${config.questionCount} interview questions for a ${config.jobRole} position requiring ${config.experience} years of experience.
                
                Interview Type: ${config.interviewType}
                
                Job Description Context:
                $jdContext
                
                Candidate Resume Context:
                ${config.resumeText}
                
                Your task:
                1. If no JD provided, first infer typical requirements for this role/experience level
                2. Generate approximately ${config.questionCount} relevant questions (may vary based on role complexity)
                3. For ${config.interviewType} interviews:
                   - TECHNICAL: Focus on technical skills, coding, architecture, problem-solving
                   - BEHAVIORAL: Focus on soft skills, teamwork, leadership, conflict resolution
                   - HR: Focus on company fit, career goals, salary expectations
                   - MIXED: Combine all types evenly
                4. Order questions by difficulty: Start EASY (basic), then MEDIUM, then HARD (advanced)
                5. Each question should have:
                   - A clear, concise question
                   - Expected answer points (what a good answer should cover)
                   - Category (Technical/Behavioral/HR)
                   - Difficulty level (Easy/Medium/Hard)
                
                IMPORTANT FORMATTING RULES:
                - Return ONLY a valid JSON array of questions.
                - Each question object must have: "id" (string), "question" (string), "expectedAnswer" (string), "category" (string), "difficulty" (string).
                - IDs should be "q1", "q2", "q3", etc.
                - Use \n for newlines in strings.
                
                Note: Today\'s date is ${getCurrentDate()}.
            """.trimIndent()

            val content = content {
                text(prompt)
            }

            val response = getGenerativeModel().generateContent(content)
            val responseText = response.text ?: ""
            val tokensUsed = response.usageMetadata?.totalTokenCount ?: 0
            Log.d("GeminiManager", "Interview Questions Response ($tokensUsed tokens): $responseText")

            val jsonRegex = Regex("\\[.*\\]", RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = jsonRegex.find(responseText)?.value

            if (jsonMatch != null) {
                val jsonArray = org.json.JSONArray(jsonMatch)
                val questions = mutableListOf<InterviewQuestion>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    questions.add(
                        InterviewQuestion(
                            id = obj.optString("id", "q${i + 1}"),
                            question = obj.optString("question"),
                            expectedAnswer = obj.optString("expectedAnswer"),
                            category = obj.optString("category"),
                            difficulty = obj.optString("difficulty")
                        )
                    )
                }

                InterviewQuestionsResult(questions, tokensUsed)
            } else {
                Log.e("GeminiManager", "No JSON array found in response")
                null
            }
        } catch (e: Exception) {
            Log.e("GeminiManager", "Error generating interview questions: ${e.message}", e)
            crashlytics.recordException(e)
            null
        }
    }

    data class InterviewEvaluationResult(
        val analysis: QuestionAnalysis,
        val tokensUsed: Int
    )

    suspend fun evaluateInterviewAnswer(
        question: InterviewQuestion,
        answer: InterviewAnswer,
        config: InterviewConfig
    ): InterviewEvaluationResult? = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are an expert interviewer evaluating a candidate's answer.
                
                Job Role: ${config.jobRole}
                Experience Level: ${config.experience}
                
                Question: ${question.question}
                Expected Answer Points: ${question.expectedAnswer}
                Category: ${question.category}
                Difficulty: ${question.difficulty}
                
                Candidate's Answer (${answer.answerType}):
                ${answer.answerText}
                
                Your task:
                1. Score the answer from 0-100 based on:
                   - Relevance to the question
                   - Depth and accuracy of knowledge
                   - Communication clarity
                   - Coverage of expected points
                2. Provide constructive feedback (2-3 sentences)
                3. Suggest an improved answer (concise, ideal response)
                
                IMPORTANT FORMATTING RULES:
                - Return ONLY a valid JSON object.
                - JSON keys: "score" (number 0-100), "feedback" (string), "suggestedAnswer" (string).
                - Use \n for newlines in strings.
            """.trimIndent()

            val content = content {
                text(prompt)
            }

            val response = getGenerativeModel().generateContent(content)
            val responseText = response.text ?: ""
            val tokensUsed = response.usageMetadata?.totalTokenCount ?: 0
            Log.d("GeminiManager", "Answer Evaluation Response ($tokensUsed tokens): $responseText")

            val jsonRegex = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = jsonRegex.find(responseText)?.value

            if (jsonMatch != null) {
                val jsonObject = JSONObject(jsonMatch)

                val analysis = QuestionAnalysis(
                    questionId = answer.questionId,
                    score = jsonObject.optInt("score"),
                    feedback = jsonObject.optString("feedback"),
                    suggestedAnswer = jsonObject.optString("suggestedAnswer"),
                    tokensUsed = tokensUsed
                )

                InterviewEvaluationResult(analysis, tokensUsed)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("GeminiManager", "Error evaluating answer: ${e.message}", e)
            crashlytics.recordException(e)
            null
        }
    }

    data class InterviewReportResult(
        val result: InterviewResult,
        val tokensUsed: Int
    )

    suspend fun generateInterviewReport(
        config: InterviewConfig,
        answers: List<InterviewAnswer>,
        questionAnalyses: List<QuestionAnalysis>
    ): InterviewReportResult? = withContext(Dispatchers.IO) {
        try {
            val avgScore = if (questionAnalyses.isNotEmpty()) {
                questionAnalyses.sumOf { it.score } / questionAnalyses.size
            } else 0

            val prompt = """
                You are an expert hiring manager providing an overall interview assessment.
                
                Job Role: ${config.jobRole}
                Experience Level: ${config.experience}
                Interview Type: ${config.interviewType}
                
                Question Performance:
                ${questionAnalyses.joinToString("\n") { "- Q${it.questionId}: ${it.score}/100 - ${it.feedback}" }}
                
                Overall Score So Far: $avgScore/100
                
                Your task:
                1. Provide 3-4 key strengths observed across all answers
                2. Provide 3-4 key weaknesses or areas for improvement
                3. Give an overall assessment of the candidate's interview performance
                
                IMPORTANT FORMATTING RULES:
                - Return ONLY a valid JSON object.
                - JSON keys: "overallScore" (number 0-100, can adjust from $avgScore), "strengths" (array of strings), "weaknesses" (array of strings).
                - Overall score should reflect the complete picture, not just average.
            """.trimIndent()

            val content = content {
                text(prompt)
            }

            val response = getGenerativeModel().generateContent(content)
            val responseText = response.text ?: ""
            val tokensUsed = response.usageMetadata?.totalTokenCount ?: 0
            Log.d("GeminiManager", "Interview Report Response ($tokensUsed tokens): $responseText")

            val jsonRegex = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = jsonRegex.find(responseText)?.value

            if (jsonMatch != null) {
                val jsonObject = JSONObject(jsonMatch)

                val strengths = mutableListOf<String>()
                val strengthsArray = jsonObject.optJSONArray("strengths")
                strengthsArray?.let {
                    for (i in 0 until it.length()) strengths.add(it.getString(i))
                }

                val weaknesses = mutableListOf<String>()
                val weaknessesArray = jsonObject.optJSONArray("weaknesses")
                weaknessesArray?.let {
                    for (i in 0 until it.length()) weaknesses.add(it.getString(i))
                }

                val result = InterviewResult(
                    overallScore = jsonObject.optInt("overallScore", avgScore),
                    strengths = strengths,
                    weaknesses = weaknesses,
                    questionAnalysis = questionAnalyses,
                    tokensUsed = tokensUsed
                )

                InterviewReportResult(result, tokensUsed)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("GeminiManager", "Error generating interview report: ${e.message}", e)
            crashlytics.recordException(e)
            null
        }
    }

    // ==================== TOPIC-BASED INTERVIEW METHODS ====================

    data class InterviewTopicsResult(
        val topics: List<com.tarunguptaraja.coldemailer.domain.model.InterviewTopic>,
        val tokensUsed: Int
    )

    suspend fun generateInterviewTopics(
        config: InterviewConfig
    ): InterviewTopicsResult? = withContext(Dispatchers.IO) {
        try {
            val jdContext = if (config.jobDescription.isBlank()) {
                "No JD provided - infer typical requirements for ${config.jobRole} with ${config.experience} years experience"
            } else {
                config.jobDescription
            }
            
            val prompt = """
                You are an expert technical interviewer planning an interview.
                
                Job Role: ${config.jobRole}
                Experience Level: ${config.experience} years
                Interview Type: ${config.interviewType}
                
                Job Description Context:
                $jdContext
                
                Candidate Resume:
                ${config.resumeText}
                
                Your task:
                1. Identify 4-6 key topic areas to cover in this interview based on the role and experience level
                2. Each topic should represent a distinct skill/knowledge area relevant to the position
                3. Topics should progress from fundamental to advanced concepts
                
                IMPORTANT FORMATTING RULES:
                - Return ONLY a valid JSON array of topic objects.
                - Each topic object must have: "id" (string like "topic1", "topic2"), "name" (string - topic title), "description" (string - what this topic covers).
                - Example: [{"id": "topic1", "name": "Android Architecture", "description": "MVP, MVVM, Clean Architecture patterns"}]
                
                Note: Today\'s date is ${getCurrentDate()}.
            """.trimIndent()

            val content = content {
                text(prompt)
            }

            val response = getGenerativeModel().generateContent(content)
            val responseText = response.text ?: ""
            val tokensUsed = response.usageMetadata?.totalTokenCount ?: 0
            Log.d("GeminiManager", "Interview Topics Response ($tokensUsed tokens): $responseText")

            val jsonRegex = Regex("\\[.*\\]", RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = jsonRegex.find(responseText)?.value

            if (jsonMatch != null) {
                val jsonArray = org.json.JSONArray(jsonMatch)
                val topics = mutableListOf<com.tarunguptaraja.coldemailer.domain.model.InterviewTopic>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    topics.add(
                        com.tarunguptaraja.coldemailer.domain.model.InterviewTopic(
                            id = obj.optString("id", "topic${i + 1}"),
                            name = obj.optString("name"),
                            description = obj.optString("description"),
                            currentDepth = 1,
                            isComplete = false,
                            questionsAsked = 0,
                            maxDepthForTopic = 3
                        )
                    )
                }

                InterviewTopicsResult(topics, tokensUsed)
            } else {
                Log.e("GeminiManager", "No JSON array found in topics response")
                null
            }
        } catch (e: Exception) {
            Log.e("GeminiManager", "Error generating interview topics: ${e.message}", e)
            crashlytics.recordException(e)
            null
        }
    }

    data class NextQuestionResult(
        val question: InterviewQuestion,
        val tokensUsed: Int
    )

    suspend fun generateNextQuestion(
        config: InterviewConfig,
        topic: com.tarunguptaraja.coldemailer.domain.model.InterviewTopic,
        previousQuestion: InterviewQuestion? = null,
        previousAnswer: InterviewAnswer? = null,
        previousAnalysis: QuestionAnalysis? = null,
        isFollowUp: Boolean = false
    ): NextQuestionResult? = withContext(Dispatchers.IO) {
        try {
            val jdContext = if (config.jobDescription.isBlank()) {
                "No JD provided - typical ${config.jobRole} role requirements"
            } else {
                config.jobDescription
            }
            
            val followUpContext = if (isFollowUp && previousQuestion != null && previousAnswer != null && previousAnalysis != null) {
                """
                This is a FOLLOW-UP question on the SAME topic.
                Previous Question: ${previousQuestion.question}
                Candidate's Answer: ${previousAnswer.answerText}
                Answer Quality: ${previousAnalysis.score}/100
                Areas to probe deeper: ${previousAnalysis.feedback}
                
                Ask a probing follow-up question that:
                - Dives deeper into specific points mentioned (or not mentioned) in their answer
                - Tests their depth of understanding
                - Is more challenging than the previous question
                """
            } else {
                """
                This is the FIRST question on this topic.
                Difficulty Level: ${topic.getDifficultyForNextQuestion()}
                Ask a ${topic.getDifficultyForNextQuestion()} question to start assessing their knowledge on this topic.
                """
            }
            
            val prompt = """
                You are an expert technical interviewer conducting a conversational interview.
                
                Job Role: ${config.jobRole}
                Experience Level: ${config.experience} years
                Interview Type: ${config.interviewType}
                
                Current Topic: ${topic.name}
                Topic Description: ${topic.description}
                Current Depth Level: ${topic.currentDepth}/3 (1=basic, 2=intermediate, 3=advanced)
                
                $followUpContext
                
                Candidate Resume Context:
                ${config.resumeText}
                
                Job Description Context:
                $jdContext
                
                Your task:
                1. Generate ONE targeted interview question for this topic at the appropriate depth
                2. The question should feel natural and conversational (like a human interviewer)
                3. For ${config.interviewType} interviews:
                   - TECHNICAL: Focus on coding, architecture, problem-solving
                   - BEHAVIORAL: Focus on soft skills, scenarios, experiences
                   - HR: Focus on fit, motivation, career goals
                   - MIXED: Balance technical and behavioral
                
                IMPORTANT FORMATTING RULES:
                - Return ONLY a valid JSON object.
                - JSON keys: "id" (string), "question" (string - the actual question text), "expectedAnswer" (string - key points a good answer should cover), "category" (string - Technical/Behavioral/HR), "difficulty" (string - Easy/Medium/Hard).
                - The question should feel like a natural follow-up in a conversation.
                
                Note: Today\'s date is ${getCurrentDate()}.
            """.trimIndent()

            val content = content {
                text(prompt)
            }

            val response = getGenerativeModel().generateContent(content)
            val responseText = response.text ?: ""
            val tokensUsed = response.usageMetadata?.totalTokenCount ?: 0
            Log.d("GeminiManager", "Next Question Response ($tokensUsed tokens): $responseText")

            val jsonRegex = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = jsonRegex.find(responseText)?.value

            if (jsonMatch != null) {
                val jsonObject = JSONObject(jsonMatch)
                
                val question = InterviewQuestion(
                    id = jsonObject.optString("id", "q_${System.currentTimeMillis()}"),
                    question = jsonObject.optString("question"),
                    expectedAnswer = jsonObject.optString("expectedAnswer"),
                    category = jsonObject.optString("category"),
                    difficulty = jsonObject.optString("difficulty"),
                    topicId = topic.id,
                    topicName = topic.name,
                    isFollowUp = isFollowUp,
                    parentQuestionId = if (isFollowUp) previousQuestion?.id else null,
                    followUpReason = if (isFollowUp) "Probing deeper based on previous answer" else null
                )

                NextQuestionResult(question, tokensUsed)
            } else {
                Log.e("GeminiManager", "No JSON found in question response")
                null
            }
        } catch (e: Exception) {
            Log.e("GeminiManager", "Error generating next question: ${e.message}", e)
            crashlytics.recordException(e)
            null
        }
    }

    data class EvaluationWithDecisionResult(
        val analysis: QuestionAnalysis,
        val shouldFollowUp: Boolean,
        val followUpReason: String?,
        val shouldCompleteTopic: Boolean,
        val tokensUsed: Int
    )

    suspend fun evaluateAnswerWithDecision(
        config: InterviewConfig,
        question: InterviewQuestion,
        answer: InterviewAnswer,
        topic: com.tarunguptaraja.coldemailer.domain.model.InterviewTopic
    ): EvaluationWithDecisionResult? = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are an expert interviewer evaluating a candidate's answer and deciding the next step.
                
                Job Role: ${config.jobRole}
                Experience Level: ${config.experience} years
                
                Current Topic: ${topic.name}
                Topic Depth Progress: ${topic.currentDepth}/3 questions asked on this topic
                
                Question Asked: ${question.question}
                Expected Answer Points: ${question.expectedAnswer}
                
                Candidate's Answer: ${answer.answerText}
                Answer Type: ${answer.answerType}
                
                Your task:
                1. Evaluate the answer quality (score 0-100)
                2. Provide specific, actionable feedback
                3. Provide a suggested/improved answer
                4. DECIDE: Should you ask a follow-up question on this topic OR move to the next topic?
                
                Decision Rules:
                - If score < 60 OR answer seems incomplete OR candidate showed shallow understanding → ask FOLLOW-UP (shouldFollowUp: true)
                - If score >= 60 AND currentDepth < 3 AND candidate showed depth → ask FOLLOW-UP to go deeper (shouldFollowUp: true)
                - If score >= 70 AND (currentDepth >= 3 OR answer was comprehensive) → MOVE TO NEXT TOPIC (shouldFollowUp: false, shouldCompleteTopic: true)
                - If topic has 3+ questions already → MOVE TO NEXT TOPIC
                
                IMPORTANT FORMATTING RULES:
                - Return ONLY a valid JSON object.
                - JSON keys: "score" (number 0-100), "feedback" (string - specific feedback), "suggestedAnswer" (string - what a great answer would include), "shouldFollowUp" (boolean), "followUpReason" (string - explain why follow-up or why move on), "shouldCompleteTopic" (boolean - true if we're done with this topic).
            """.trimIndent()

            val content = content {
                text(prompt)
            }

            val response = getGenerativeModel().generateContent(content)
            val responseText = response.text ?: ""
            val tokensUsed = response.usageMetadata?.totalTokenCount ?: 0
            Log.d("GeminiManager", "Evaluation with Decision ($tokensUsed tokens): $responseText")

            val jsonRegex = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = jsonRegex.find(responseText)?.value

            if (jsonMatch != null) {
                val jsonObject = JSONObject(jsonMatch)

                val analysis = QuestionAnalysis(
                    questionId = question.id,
                    question = question.question,
                    userAnswer = answer.answerText,
                    score = jsonObject.optInt("score"),
                    feedback = jsonObject.optString("feedback"),
                    suggestedAnswer = jsonObject.optString("suggestedAnswer"),
                    tokensUsed = tokensUsed
                )

                EvaluationWithDecisionResult(
                    analysis = analysis,
                    shouldFollowUp = jsonObject.optBoolean("shouldFollowUp", false),
                    followUpReason = jsonObject.optString("followUpReason", null),
                    shouldCompleteTopic = jsonObject.optBoolean("shouldCompleteTopic", false),
                    tokensUsed = tokensUsed
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("GeminiManager", "Error evaluating with decision: ${e.message}", e)
            crashlytics.recordException(e)
            null
        }
    }
}
