package com.example.helloandroid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.random.Random
import com.example.helloandroid.data.CardData

import androidx.lifecycle.viewModelScope
import com.example.helloandroid.data.SettingsRepository
import kotlinx.coroutines.flow.* // For stateIn, SharingStarted etc.
import kotlinx.coroutines.launch

// --- Enums and Data Classes for State ---

enum class AppScreen {
    HOME, CATEGORIZATION, QUIZ_SETUP, QUIZZING, RESULTS, SETTINGS
}

enum class QuizSet {
    ALL, KNOWN, UNKNOWN
}

enum class QuizOrder {
    RANDOM, SEQUENTIAL
}

data class QuizStats(
    val totalCardsInQuiz: Int = 0,
    val correctFirstTry: Int = 0,
    val neededPractice: Int = 0,
    val totalGuesses: Int = 0,
    val totalPasses: Int = 0,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val quizSetName: String = ""
) {
    val durationSeconds: Long get() = if (endTime > startTime) (endTime - startTime) / 1000 else 0
    val durationMinutes: Long get() = floor(durationSeconds / 60.0).toLong()
    val durationRemainderSeconds: Long get() = durationSeconds % 60
    val firstTryAccuracy: Float get() = if (totalCardsInQuiz > 0) (correctFirstTry.toFloat() / totalCardsInQuiz) * 100f else 0f
    val avgGuesses: Float get() = if (totalCardsInQuiz > 0) totalGuesses.toFloat() / totalCardsInQuiz else 0f
}


data class QuizUiState(
    // General State
    val currentScreen: AppScreen = AppScreen.HOME,
    val isLoading: Boolean = false,
    val feedbackMessage: String? = null, // For showing Correct/Incorrect temporary messages

    // Card Lists
    val allCards: Map<String, Int> = CardData.cardCosts,
    val knownCards: Set<String> = emptySet(),
    val unknownCards: Set<String> = emptySet(),

    // Categorization State
    val categorizationPass: Int = 0,
    val categorizationCardsCurrentPass: List<String> = emptyList(),
    val categorizationCurrentIndex: Int = 0,
    val categorizationFailedCards: Set<String> = emptySet(),
    val categorizationNextPassCandidates: Set<String> = emptySet(), // Track success within a pass
    val categorizationTimerValue: Float = 3.0f,
    val categorizationInputEnabled: Boolean = true,

    // Quiz Setup State
    val selectedQuizSet: QuizSet = QuizSet.ALL,
    val selectedQuizOrder: QuizOrder = QuizOrder.RANDOM,

    // Quizzing State
    val quizCardsCurrentPass: List<String> = emptyList(),
    val quizCurrentIndex: Int = 0,
    val quizCardsForNextPass: MutableSet<String> = mutableSetOf(), // Use set for efficient adding
    val quizCurrentPassNumber: Int = 1,
    val quizStats: QuizStats = QuizStats(),

    // Settings state
    val categorizationTimerDurationSeconds: Int = SettingsRepository.DEFAULT_TIMER_SECONDS,
    val categorizationRequiredPasses: Int = SettingsRepository.DEFAULT_REQUIRED_PASSES,

    // Keep track of loaded known cards separately if needed, or rely on knownCards
    val initialKnownCardsLoaded: Boolean = false // Flag to know when loading is done
) {
    // Helper to get the current card name based on the active phase
    val currentCardName: String?
        get() = when (currentScreen) {
            AppScreen.CATEGORIZATION -> categorizationCardsCurrentPass.getOrNull(categorizationCurrentIndex)
            AppScreen.QUIZZING -> quizCardsCurrentPass.getOrNull(quizCurrentIndex)
            else -> null
        }

    val currentCorrectCost: Int?
        get() = currentCardName?.let { allCards[it] }

    // Calculate counts for display
    val knownCardCount: Int get() = knownCards.size
    val unknownCardCount: Int get() = allCards.keys.count { it !in knownCards }
}

// --- ViewModel ---

class QuizViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private var categorizationTimerJob: Job? = null

    // --- ViewModel Initialization ---
    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // Combine flows for initial loading (optional, but cleaner)
            combine(
                settingsRepository.timerDurationSeconds,
                settingsRepository.requiredPasses,
                settingsRepository.knownCards
            ) { timer, passes, known ->
                Triple(timer, passes, known)
            }.take(1) // Take only the first emission for initial setup
                .collect { (timer, passes, known) ->
                    _uiState.update {
                        it.copy(
                            categorizationTimerDurationSeconds = timer,
                            categorizationRequiredPasses = passes,
                            knownCards = known,
                            initialKnownCardsLoaded = true // Mark loading complete
                            // unknownCards can be derived, no need to store separately in state usually
                        )
                    }
                }
        }

        // Optional: Keep observing settings changes if you want the app to react live
        // viewModelScope.launch { settingsRepository.timerDurationSeconds.collect { timer -> _uiState.update { it.copy(categorizationTimerDurationSeconds = timer) } } }
        // viewModelScope.launch { settingsRepository.requiredPasses.collect { passes -> _uiState.update { it.copy(categorizationRequiredPasses = passes) } } }
        // viewModelScope.launch { settingsRepository.knownCards.collect { known -> _uiState.update { it.copy(knownCards = known) } } }
        // Note: Live updates might reset ongoing quizzes if not handled carefully. The current approach loads once at start.
    }

    // --- Navigation ---
    fun navigateTo(screen: AppScreen) {
        val currentState = _uiState.value // Get state *before* update

        // Cancel timer if navigating away from categorization
        if (currentState.currentScreen == AppScreen.CATEGORIZATION) {
            categorizationTimerJob?.cancel()
            // Optionally reset categorization state fully if desired when navigating away
            // _uiState.update { it.copy(categorizationPass = 0, ... etc ...) }
        }

        // Reset quiz state if navigating away from quizzing/results? (Optional)
        // if (currentState.currentScreen == AppScreen.QUIZZING || currentState.currentScreen == AppScreen.RESULTS) {
        //     _uiState.update { it.copy(quizCardsCurrentPass = emptyList(), ...) }
        // }


        _uiState.update {
            it.copy(
                currentScreen = screen,
                feedbackMessage = null, // Clear feedback on navigate
                // Reset timer value visual if leaving categorization screen
                categorizationTimerValue = if (currentState.currentScreen == AppScreen.CATEGORIZATION) 3.0f else it.categorizationTimerValue,
                categorizationInputEnabled = true // Ensure input is re-enabled when navigating away
            )
        }
    }

    // --- Categorization Logic ---

    fun startCategorization() {
        if (!_uiState.value.initialKnownCardsLoaded) return // Prevent starting before load

        val initialCards = _uiState.value.allCards.keys.sorted()
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.CATEGORIZATION,
                categorizationPass = 1,
                categorizationCardsCurrentPass = initialCards,
                categorizationCurrentIndex = 0,
                categorizationFailedCards = emptySet(),
                categorizationNextPassCandidates = emptySet(),
                // DO NOT reset known/unknown cards here, keep loaded ones until successful save
                isLoading = false
            )
        }
        startCardTimer() // Start timer using loaded duration
    }

    private fun startCardTimer() {
        categorizationTimerJob?.cancel()
        val currentState = _uiState.value
        val timeoutMs = currentState.categorizationTimerDurationSeconds * 1000L // Use state value
        val initialTimerValue = timeoutMs / 1000f

        _uiState.update { it.copy(categorizationTimerValue = initialTimerValue, categorizationInputEnabled = true) }
        categorizationTimerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val remaining = timeoutMs - (System.currentTimeMillis() - startTime)
                // Prevent negative display due to slight delays
                _uiState.update { it.copy(categorizationTimerValue = (remaining / 1000f).coerceAtLeast(0f)) }
                delay(50)
            }
            // Ensure timer value hits exactly 0 if timeout occurs
            if (_uiState.value.categorizationInputEnabled) { // Check if timeout happened before submission
                _uiState.update { it.copy(categorizationTimerValue = 0f) }
                handleCategorizationTimeout()
            }
        }
    }

    fun submitCategorizationGuess(guessInt: Int) {
        categorizationTimerJob?.cancel() // Stop timer on submission
        _uiState.update { it.copy(categorizationInputEnabled = false)} // Disable input while processing

        val currentState = _uiState.value
        val cardName = currentState.currentCardName ?: return
        val correctCost = currentState.currentCorrectCost ?: return
        // val guessInt = guessStr.toIntOrNull() // No longer needed

        var feedback = ""
        var correct = false

        if (guessInt == correctCost) {
            feedback = "Correct!"
            correct = true
            _uiState.update {
                it.copy(categorizationNextPassCandidates = it.categorizationNextPassCandidates + cardName)
            }
        } else {
            feedback = "Incorrect! (Cost: $correctCost)"
            _uiState.update {
                it.copy(categorizationFailedCards = it.categorizationFailedCards + cardName)
            }
        }

        showTemporaryFeedback(feedback) {
            moveToNextCategorizationCard(correct) // Move after showing feedback
        }
    }

    private fun handleCategorizationTimeout() {
        // This function is called by the timer job when time runs out *before* submission
        viewModelScope.launch { // Ensure updates happen on main thread context if needed
            if (!_uiState.value.categorizationInputEnabled) return@launch // Already processed submission

            _uiState.update { it.copy(categorizationInputEnabled = false)} // Disable input

            val currentState = _uiState.value
            val cardName = currentState.currentCardName ?: return@launch

            _uiState.update {
                it.copy(categorizationFailedCards = it.categorizationFailedCards + cardName)
            }
            showTemporaryFeedback("Time's up! Marked as unknown.") {
                moveToNextCategorizationCard(false) // Treat timeout as incorrect
            }
        }
    }

    private fun moveToNextCategorizationCard(wasCorrectThisTime: Boolean) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val nextIndex = currentState.categorizationCurrentIndex + 1

            if (nextIndex < currentState.categorizationCardsCurrentPass.size) {
                // More cards in this pass
                _uiState.update {
                    it.copy(
                        categorizationCurrentIndex = nextIndex,
                        feedbackMessage = null // Clear feedback for next card
                    )
                }
                startCardTimer() // Start timer for the new card
            } else {
                // End of pass
                finishCategorizationPass()
            }
        }
    }

    private fun finishCategorizationPass() {
        val currentState = _uiState.value
        val currentPass = currentState.categorizationPass
        val requiredPasses = currentState.categorizationRequiredPasses // Use state value
        val successfulThisPass = currentState.categorizationNextPassCandidates
        val failedOverall = currentState.categorizationFailedCards

        // Check for completion: >= required passes OR no cards passed this round
        if (currentPass >= requiredPasses || successfulThisPass.isEmpty()) {
            // Final pass complete (or ended early) - THIS IS WHERE WE SAVE
            val finalKnown = if (currentPass >= requiredPasses) successfulThisPass else emptySet() // Only save if required passes met
            val finalUnknown = currentState.allCards.keys.filter { it in failedOverall || it !in finalKnown }.toSet()

            // Save the successfully determined known cards
            viewModelScope.launch {
                try {
                    settingsRepository.saveKnownCards(finalKnown)
                    // Update UI state *after* successful save
                    _uiState.update {
                        it.copy(
                            currentScreen = AppScreen.HOME,
                            knownCards = finalKnown, // Update state with the newly saved list
                            unknownCards = finalUnknown, // Update derived unknown
                            categorizationPass = 0,
                            categorizationCardsCurrentPass = emptyList(),
                            categorizationCurrentIndex = 0,
                            categorizationFailedCards = emptySet(),
                            categorizationNextPassCandidates = emptySet(),
                            feedbackMessage = "Categorization Complete & Saved: ${finalKnown.size} Known"
                        )
                    }
                } catch (e: Exception) {
                    // Handle saving error (e.g., show message)
                    _uiState.update {
                        it.copy(
                            currentScreen = AppScreen.HOME, // Still go home
                            feedbackMessage = "Categorization Complete. Error saving results.",
                            // Keep UI state reflecting the just-calculated lists, even if save failed? Or revert?
                            knownCards = finalKnown,
                            unknownCards = finalUnknown,
                            // Reset categorization state fields anyway
                            categorizationPass = 0,
                            categorizationCardsCurrentPass = emptyList(),
                            categorizationCurrentIndex = 0,
                            categorizationFailedCards = emptySet(),
                            categorizationNextPassCandidates = emptySet()
                        )
                    }
                }
            }
        } else {
            // Prepare for the next pass (Logic remains the same)
            val cardsForNextPass = successfulThisPass.toList().sorted()
            _uiState.update {
                it.copy(
                    categorizationPass = currentPass + 1,
                    categorizationCardsCurrentPass = cardsForNextPass,
                    categorizationCurrentIndex = 0,
                    categorizationNextPassCandidates = emptySet(),
                    feedbackMessage = null
                )
            }
            if (cardsForNextPass.isNotEmpty()) {
                startCardTimer()
            } else {
                // This case (empty successfulThisPass) is handled by the completion check above
                // but call finishCategorizationPass again just to be safe if logic changes
                finishCategorizationPass()
            }
        }
    }

    // --- Quiz Setup ---
    fun selectQuizSetOption(set: QuizSet) {
        _uiState.update { it.copy(selectedQuizSet = set) }
    }

    fun selectQuizOrderOption(order: QuizOrder) {
        _uiState.update { it.copy(selectedQuizOrder = order) }
    }

    fun startQuiz() {
        if (!_uiState.value.initialKnownCardsLoaded) return // Prevent starting before load
        val currentState = _uiState.value
        val cardPool = when (currentState.selectedQuizSet) {
            QuizSet.ALL -> currentState.allCards.keys
            QuizSet.KNOWN -> currentState.knownCards.ifEmpty { currentState.allCards.keys } // Fallback if empty
            QuizSet.UNKNOWN -> {
                // Calculate unknown if not explicitly stored or derive it
                val unknown = currentState.allCards.keys - currentState.knownCards
                unknown.ifEmpty { currentState.allCards.keys } // Fallback if empty
            }
        }

        if (cardPool.isEmpty()) {
            _uiState.update { it.copy(feedbackMessage = "Selected card set is empty!") }
            return
        }

        var initialQuizCards = cardPool.toList()
        if (currentState.selectedQuizOrder == QuizOrder.RANDOM) {
            initialQuizCards = initialQuizCards.shuffled(Random(System.currentTimeMillis()))
        } else {
            initialQuizCards = initialQuizCards.sorted()
        }

        val quizSetName = when (currentState.selectedQuizSet) {
            QuizSet.ALL -> "All Cards"
            QuizSet.KNOWN -> "Known Cards"
            QuizSet.UNKNOWN -> "Unknown Cards"
        }


        _uiState.update {
            it.copy(
                currentScreen = AppScreen.QUIZZING,
                quizCardsCurrentPass = initialQuizCards,
                quizCurrentIndex = 0,
                quizCardsForNextPass = mutableSetOf(),
                quizCurrentPassNumber = 1,
                quizStats = QuizStats( // Reset stats
                    totalCardsInQuiz = initialQuizCards.size,
                    startTime = System.currentTimeMillis(),
                    quizSetName = quizSetName
                ),
                feedbackMessage = null,
                isLoading = false
            )
        }
    }

    // --- Quizzing Logic ---

    fun submitQuizGuess(guessInt: Int) {
        val currentState = _uiState.value
        val cardName = currentState.currentCardName ?: return
        val correctCost = currentState.currentCorrectCost ?: return
        // val guessInt = guessStr.toIntOrNull() // No longer needed
        val isFirstGuessForThisCardInPass = !currentState.quizCardsForNextPass.contains(cardName)

        _uiState.update { st ->
            st.copy(quizStats = st.quizStats.copy(totalGuesses = st.quizStats.totalGuesses + 1))
        }

        if (guessInt == correctCost) {
            val correctFeedback = "Correct! ($correctCost Elixir)"
            _uiState.update { st ->
                st.copy(quizStats = if (isFirstGuessForThisCardInPass) {
                    st.quizStats.copy(correctFirstTry = st.quizStats.correctFirstTry + 1)
                } else {
                    st.quizStats
                }
                )
            }
            showTemporaryFeedback(correctFeedback) {
                moveToNextQuizCard()
            }
        } else {
            val incorrectFeedback = if (isFirstGuessForThisCardInPass) {
                _uiState.update { st ->
                    st.copy(
                        quizCardsForNextPass = st.quizCardsForNextPass.apply { add(cardName) },
                        quizStats = st.quizStats.copy(neededPractice = st.quizStats.neededPractice + 1)
                    )
                }
                "Incorrect. Added to review."
            } else {
                "Incorrect. Try again..."
            }
            _uiState.update { it.copy(feedbackMessage = incorrectFeedback)}
        }
    }

    private fun moveToNextQuizCard() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val nextIndex = currentState.quizCurrentIndex + 1

            if (nextIndex < currentState.quizCardsCurrentPass.size) {
                // More cards in this pass
                _uiState.update {
                    it.copy(
                        quizCurrentIndex = nextIndex,
                        feedbackMessage = null // Clear feedback
                    )
                }
            } else {
                // End of pass
                finishQuizPass()
            }
        }
    }

    private fun finishQuizPass() {
        val currentState = _uiState.value
        val cardsForReview = currentState.quizCardsForNextPass.toList()

        if (cardsForReview.isEmpty()) {
            // Quiz complete!
            _uiState.update { st ->
                st.copy(
                    currentScreen = AppScreen.RESULTS,
                    quizStats = st.quizStats.copy(
                        endTime = System.currentTimeMillis(),
                        totalPasses = st.quizCurrentPassNumber // Record final pass number
                    ),
                    feedbackMessage = "Quiz Complete!"
                )
            }
        } else {
            // Start next review pass
            var nextPassCards = cardsForReview
            if (currentState.selectedQuizOrder == QuizOrder.RANDOM) {
                nextPassCards = nextPassCards.shuffled(Random(System.currentTimeMillis()))
            } else {
                nextPassCards = nextPassCards.sorted()
            }

            _uiState.update {
                it.copy(
                    quizCardsCurrentPass = nextPassCards,
                    quizCurrentIndex = 0,
                    quizCardsForNextPass = mutableSetOf(), // Reset for the new pass
                    quizCurrentPassNumber = it.quizCurrentPassNumber + 1,
                    feedbackMessage = null
                )
            }
        }
    }

    // --- Settings Logic ---
    fun saveTimerSetting(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.saveCategorizationTimer(seconds)
            // Update UI immediately for responsiveness
            _uiState.update { it.copy(categorizationTimerDurationSeconds = seconds) }
        }
    }

    fun savePassesSetting(passes: Int) {
        viewModelScope.launch {
            settingsRepository.saveCategorizationPasses(passes)
            // Update UI immediately
            _uiState.update { it.copy(categorizationRequiredPasses = passes) }
        }
    }

    // --- Utility ---
    private fun showTemporaryFeedback(message: String, onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(feedbackMessage = message) }
            delay(1200L) // Show feedback for 1.2 seconds
            _uiState.update { it.copy(feedbackMessage = null) }
            onFinished() // Execute callback after feedback duration
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null)}
    }
}