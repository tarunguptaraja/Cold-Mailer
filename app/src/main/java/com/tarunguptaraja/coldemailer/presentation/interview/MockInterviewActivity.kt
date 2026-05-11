package com.tarunguptaraja.coldemailer.presentation.interview

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import com.tarunguptaraja.coldemailer.DocumentTextExtractor
import com.tarunguptaraja.coldemailer.R
import com.tarunguptaraja.coldemailer.databinding.ActivityMockInterviewBinding
import com.tarunguptaraja.coldemailer.BottomNavHelper
import com.tarunguptaraja.coldemailer.domain.model.AnswerType
import com.tarunguptaraja.coldemailer.domain.model.InterviewType
import com.tarunguptaraja.coldemailer.presentation.interview.adapter.QuestionAnalysisAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MockInterviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMockInterviewBinding
    private val viewModel: MockInterviewViewModel by viewModels()

    @Inject
    lateinit var bottomNavHelper: BottomNavHelper

    @Inject
    lateinit var documentTextExtractor: DocumentTextExtractor

    @Inject
    lateinit var profilePreferenceManager: com.tarunguptaraja.coldemailer.ProfilePreferenceManager

    // Text-to-Speech
    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false

    // Speech-to-Text
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var mediaActionSound: MediaActionSound? = null
    private var pulseAnimator: ObjectAnimator? = null

    // File pickers
    private val pickJdLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = getFileName(it) ?: "JD.pdf"
            viewModel.onJobDescriptionFileSelected(it, fileName)
        }
    }

    private val pickResumeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = getFileName(it) ?: "Resume.pdf"
            viewModel.onResumeFileSelected(it, fileName)
        }
    }

    private lateinit var questionAnalysisAdapter: QuestionAnalysisAdapter

    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        binding = ActivityMockInterviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        mediaActionSound = MediaActionSound()
        mediaActionSound?.load(MediaActionSound.START_VIDEO_RECORDING)
        mediaActionSound?.load(MediaActionSound.STOP_VIDEO_RECORDING)

        initTextToSpeech()
        initSpeechRecognizer()
        setupUI()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation.selectedItemId = R.id.nav_mock_interview
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
        mediaActionSound?.release()
        pulseAnimator?.cancel()
    }

    // ==================== ANIMATIONS ====================

    private fun startPulseAnimation() {
        if (pulseAnimator == null) {
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.2f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.2f)
            pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.btnRecord, scaleX, scaleY).apply {
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                duration = 500
            }
        }
        pulseAnimator?.start()
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        binding.btnRecord.scaleX = 1f
        binding.btnRecord.scaleY = 1f
    }

    // ==================== INITIALIZATION ====================

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                ttsInitialized = true
                
                // Set up utterance progress listener to detect when speech finishes
                textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // TTS started
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        // TTS finished - wait 2 seconds then start mic if in voice mode
                        binding.root.postDelayed({
                            if (binding.voiceContainer.visibility == View.VISIBLE && !isListening) {
                                startListening()
                            }
                        }, 2000)
                    }
                    
                    override fun onError(utteranceId: String?) {
                        // TTS error
                    }
                })
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    binding.tvVoiceStatus.text = "Listening..."
                    startPulseAnimation()
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    binding.tvVoiceStatus.text = "Tap microphone to start speaking"
                    mediaActionSound?.play(MediaActionSound.STOP_VIDEO_RECORDING)
                    stopPulseAnimation()
                }

                override fun onError(error: Int) {
                    isListening = false
                    binding.tvVoiceStatus.text = "Error: ${getErrorText(error)}"
                    mediaActionSound?.play(MediaActionSound.STOP_VIDEO_RECORDING)
                    stopPulseAnimation()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        binding.tvRecordedText.text = text
                        binding.tvRecordedText.visibility = View.VISIBLE
                        binding.etAnswer.setText(text)
                    }
                    isListening = false
                    binding.tvVoiceStatus.text = "Tap microphone to start speaking"
                    stopPulseAnimation()
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error"
        }
    }

    // ==================== UI SETUP ====================

    private fun setupUI() {
        bottomNavHelper.setupBottomNav(
            this,
            binding.bottomNavigation,
            R.id.nav_mock_interview
        )

        binding.btnHistory.setOnClickListener {
            showHistoryDialog()
        }

        // Setup RecyclerView
        questionAnalysisAdapter = QuestionAnalysisAdapter()
        binding.recyclerQuestionAnalysis.apply {
            layoutManager = LinearLayoutManager(this@MockInterviewActivity)
            adapter = questionAnalysisAdapter
        }

        // Setup form inputs
        binding.etJobRole.doAfterTextChanged {
            viewModel.onJobRoleChanged(it.toString())
        }

        binding.etExperience.doAfterTextChanged {
            viewModel.onExperienceChanged(it.toString())
        }

        binding.etJobDescription.doAfterTextChanged {
            viewModel.onJobDescriptionTextChanged(it.toString())
        }

        binding.etResumeText.doAfterTextChanged {
            // Handle resume text paste if needed
        }

        // File upload buttons
        binding.btnUploadJd.setOnClickListener {
            pickJdLauncher.launch(DocumentTextExtractor.getSupportedMimeTypes())
        }

        binding.btnUploadResume.setOnClickListener {
            pickResumeLauncher.launch(DocumentTextExtractor.getSupportedMimeTypes())
        }

        binding.btnClearJd.setOnClickListener {
            viewModel.clearJobDescription()
        }

        binding.btnClearResume.setOnClickListener {
            viewModel.clearResume()
        }

        // Interview type chips
        binding.chipGroupInterviewType.setOnCheckedStateChangeListener { group, checkedIds ->
            val type = when (checkedIds.firstOrNull()) {
                R.id.chip_technical -> InterviewType.TECHNICAL
                R.id.chip_behavioral -> InterviewType.BEHAVIORAL
                R.id.chip_hr -> InterviewType.HR
                R.id.chip_mixed -> InterviewType.MIXED
                else -> InterviewType.MIXED
            }
            viewModel.onInterviewTypeChanged(type)
        }

        // Question count slider
        binding.sliderQuestionCount.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            val count = value.toInt()
            binding.tvQuestionCountLabel.text = "$count Questions"
            viewModel.onQuestionCountChanged(count)
        })

        // Start interview button
        binding.btnStartInterview.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    1002
                )
            } else {
                viewModel.startInterview()
            }
        }

        // Interview screen buttons
        binding.btnSpeakQuestion.setOnClickListener {
            speakCurrentQuestion()
        }
        
        binding.btnSilenceTts.setOnClickListener {
            // Stop current TTS playback
            textToSpeech?.stop()
            binding.btnSilenceTts.visibility = View.GONE
            binding.btnSpeakQuestion.visibility = View.VISIBLE
        }

        binding.btnSkip.setOnClickListener {
            viewModel.skipQuestion()
        }

        binding.btnSubmit.setOnClickListener {
            val answer = binding.etAnswer.text.toString()
            if (answer.isBlank()) {
                Toast.makeText(this, "Please provide an answer", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val isVoiceMode = binding.voiceContainer.visibility == View.VISIBLE
            viewModel.submitAnswer(answer, isVoice = isVoiceMode)
            binding.etAnswer.text?.clear()
            binding.tvRecordedText.visibility = View.GONE
        }

        // Enable scrolling for the multiline edit text
        @SuppressLint("ClickableViewAccessibility")
        binding.etAnswer.setOnTouchListener { v, event ->
            if (v.id == R.id.et_answer) {
                v.parent.requestDisallowInterceptTouchEvent(true)
                when (event.action and MotionEvent.ACTION_MASK) {
                    MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        // Record button
        binding.btnRecord.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }

        // Switch to Text mode
        binding.btnSwitchToText.setOnClickListener {
            binding.voiceContainer.visibility = View.GONE
            binding.textInputContainer.visibility = View.VISIBLE
        }

        // Switch to Voice mode
        binding.btnSwitchToVoice.setOnClickListener {
            binding.textInputContainer.visibility = View.GONE
            binding.voiceContainer.visibility = View.VISIBLE
            checkAudioPermission()
        }

        // Retry button
        binding.btnRetry.setOnClickListener {
            viewModel.reset()
        }
    }

    // ==================== SPEECH ====================

    private fun speakCurrentQuestion() {
        if (!ttsInitialized) return
        
        val currentState = viewModel.uiState.value as? InterviewUiState.InProgress
        val question = currentState?.currentQuestion
        
        question?.let {
            binding.btnSpeakQuestion.visibility = View.GONE
            binding.btnSilenceTts.visibility = View.VISIBLE
            
            // Speak with utterance ID to track completion
            textToSpeech?.speak(it.question, TextToSpeech.QUEUE_FLUSH, null, "question_${it.id}")
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
            return
        }

        mediaActionSound?.play(MediaActionSound.START_VIDEO_RECORDING)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        mediaActionSound?.play(MediaActionSound.STOP_VIDEO_RECORDING)
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002) {
            viewModel.startInterview()
        } else if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                Toast.makeText(this, "Microphone permission required for voice input", Toast.LENGTH_LONG).show()
                binding.voiceContainer.visibility = View.GONE
                binding.textInputContainer.visibility = View.VISIBLE
            }
        }
    }

    // ==================== STATE OBSERVATION ====================

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tokens.collect { tokens ->
                    binding.tvTokens.text = "%,d Tokens".format(tokens)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUIState(state)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.setupState.collect { setupState ->
                    updateSetupUI(setupState)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tokenBreakdown.collect { breakdown ->
                    updateTokenBreakdown(breakdown)
                }
            }
        }
    }

    private fun showHistoryDialog() {
        val history = profilePreferenceManager.getInterviewHistory()
        if (history.isEmpty()) {
            Toast.makeText(this, "No interview history found.", Toast.LENGTH_SHORT).show()
            return
        }

        val items = history.map { record ->
            val date = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(java.util.Date(record.timestamp))
            "${record.jobRole} - Score: ${record.result.overallScore}/100\n$date"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Interview History")
            .setItems(items) { _, which ->
                val record = history[which]
                showHistoryDetailsDialog(record)
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showHistoryDetailsDialog(record: com.tarunguptaraja.coldemailer.domain.model.InterviewHistoryRecord) {
        val message = StringBuilder()
        message.append("Role: ${record.jobRole}\n")
        message.append("Experience: ${record.experience}\n")
        message.append("Score: ${record.result.overallScore}/100\n\n")
        message.append("Strengths:\n- ${record.result.strengths.joinToString("\n- ")}\n\n")
        message.append("Areas for Improvement:\n- ${record.result.weaknesses.joinToString("\n- ")}\n\n")
        
        message.append("Question Analysis:\n")
        record.result.questionAnalysis.forEachIndexed { index, qa ->
            message.append("Q${index + 1}: ${qa.question}\n")
            message.append("Ans: ${qa.userAnswer}\n")
            message.append("Feedback: ${qa.feedback} (Score: ${qa.score}/100)\n\n")
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Interview Details")
            .setMessage(message.toString())
            .setPositiveButton("Close", null)
            .show()
    }

    private fun updateUIState(state: InterviewUiState) {
        // Hide all containers first
        binding.setupContainer.visibility = View.GONE
        binding.loadingContainer.visibility = View.GONE
        binding.interviewContainer.visibility = View.GONE
        binding.evaluatingContainer.visibility = View.GONE
        binding.resultsContainer.visibility = View.GONE
        binding.progressLoading.visibility = View.GONE

        when (state) {
            is InterviewUiState.Setup -> {
                binding.setupContainer.visibility = View.VISIBLE
                binding.bottomNavigation.visibility = View.VISIBLE
            }
            is InterviewUiState.Loading -> {
                binding.loadingContainer.visibility = View.VISIBLE
                binding.bottomNavigation.visibility = View.GONE
                binding.tvLoadingMessage.text = state.message
            }
            is InterviewUiState.InProgress -> {
                binding.interviewContainer.visibility = View.VISIBLE
                binding.bottomNavigation.visibility = View.GONE
                updateInterviewUI(state)
            }
            is InterviewUiState.Evaluating -> {
                binding.evaluatingContainer.visibility = View.VISIBLE
                binding.bottomNavigation.visibility = View.GONE
                binding.tvEvaluatingMessage.text = state.progress
                binding.tvEvaluatingTokens.text = "Session tokens used: ${state.sessionTokensUsed}"
            }
            is InterviewUiState.Completed -> {
                binding.resultsContainer.visibility = View.VISIBLE
                binding.bottomNavigation.visibility = View.VISIBLE
                updateResultsUI(state)
            }
        }
    }

    private fun updateSetupUI(state: InterviewSetupState) {
        // Update JD file name
        binding.tvJdFileName.text = state.jobDescriptionFileName.ifEmpty { "No JD file selected" }
        binding.btnClearJd.visibility = if (state.jobDescriptionUri != null) View.VISIBLE else View.GONE

        // Update Resume file name
        binding.tvResumeFileName.text = state.resumeFileName.ifEmpty { "No resume selected" }
        binding.btnClearResume.visibility = if (state.resumeUri != null) View.VISIBLE else View.GONE

        // Show error
        binding.tvError.visibility = if (state.error != null) View.VISIBLE else View.GONE
        binding.tvError.text = state.error

        // Update loading state
        binding.progressLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
    }

    private fun updateInterviewUI(state: InterviewUiState.InProgress) {
        val currentQuestion = state.currentQuestion
        
        currentQuestion?.let { question ->
            binding.tvQuestion.text = question.question
            binding.tvCategory.text = if (question.isFollowUp) "Follow-up: ${question.category}" else question.category
            binding.tvProgress.text = "${state.sessionState.questionsAsked.size}/${state.sessionState.maxQuestions}"
            
            // Update progress indicator
            binding.progressIndicator.progress = ((state.sessionState.questionsAsked.size) * 100) / state.sessionState.maxQuestions
            
            // Update session tokens
            binding.tvSessionTokens.text = "Session Tokens: ${state.sessionTokensUsed}"
            
            // Reset TTS buttons and auto-play
            binding.btnSpeakQuestion.visibility = View.VISIBLE
            binding.btnSilenceTts.visibility = View.GONE
            
            // Auto-speak the question
            speakCurrentQuestion()
        }
    }

    private fun updateResultsUI(state: InterviewUiState.Completed) {
        // Update score
        binding.tvFinalScore.text = state.result.overallScore.toString()
        binding.scoreProgress.progress = state.result.overallScore
        binding.tvTotalTokens.text = "Total tokens used: ${state.totalTokensUsed}"

        // Update strengths
        binding.tvStrengths.text = state.result.strengths.joinToString("\n") { "• $it" }

        // Update weaknesses
        binding.tvWeaknesses.text = state.result.weaknesses.joinToString("\n") { "• $it" }

        // Update question analysis
        questionAnalysisAdapter.submitList(state.result.questionAnalysis)
    }

    private fun updateTokenBreakdown(breakdown: TokenUsageBreakdown) {
        val sb = StringBuilder()
        sb.appendLine("Question Generation: ${breakdown.questionGeneration}")
        sb.appendLine("Answer Evaluations: ${breakdown.answerEvaluations.values.sum()}")
        sb.appendLine("Final Report: ${breakdown.finalReport}")
        binding.tvTokenBreakdown.text = sb.toString()
    }

    // ==================== HELPERS ====================

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val state = viewModel.uiState.value
        if (state is InterviewUiState.InProgress || state is InterviewUiState.Evaluating) {
            // Show confirmation dialog or handle back press in interview
            Toast.makeText(this, "Interview in progress. Please complete or skip remaining questions.", Toast.LENGTH_SHORT).show()
        } else {
            super.onBackPressed()
        }
    }
}
