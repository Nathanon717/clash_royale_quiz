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
import com.example.helloandroid.data.Deck
import com.example.helloandroid.data.SettingsRepository
import kotlinx.coroutines.flow.* // For stateIn, SharingStarted etc.


// --- Enums and Data Classes for State ---

enum class AppScreen {
    HOME, CATEGORIZATION, QUIZ_SETUP, QUIZZING, RESULTS, CATEGORIZATION_SETUP, DECKS,
    COMBOS_SETUP, COMBOS_QUIZZING, COMBOS_RESULTS
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
    val initialKnownCardsLoaded: Boolean = false, // Flag to know when loading is done

    // Decks State
    val decks: List<Deck> = emptyList(),
    val deckNameInput: String = "",
    val selectedCardsForNewDeck: Set<String> = emptySet(),

    // Combos Mode State
    val selectedDeckForComboQuiz: Deck? = null,
    val comboQuizTimerEnabled: Boolean = true,
    val comboQuizTimerDurationSeconds: Int = 10, // Default
    val comboQuizNumberOfQuestions: Int = 10, // Default, range 5-50
    val comboQuizCardsPerCombo: Int = 2, // Default, 2 or 3
    val currentComboCards: List<String> = emptyList(),
    val currentComboCorrectCost: Int = 0,
    val comboQuizCurrentQuestionIndex: Int = 0,
    val comboQuizCorrectAnswers: Int = 0,
    val comboQuizTimerValue: Float = 10.0f, // Default, similar to categorization timer
    val comboQuizInputEnabled: Boolean = true
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
    private var comboQuizTimerJob: Job? = null


    // --- ViewModel Initialization ---
    init {
        loadInitialData()
        loadDecks() // Call to load decks
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            combine(
                settingsRepository.timerDurationSeconds,
                settingsRepository.requiredPasses,
                settingsRepository.knownCards
                // Removed combo settings from here as per revised subtask (no persistence)
            ) { timer, passes, known ->
                // Using a more complex structure or multiple updates if needed
                _uiState.update {
                    it.copy(
                        categorizationTimerDurationSeconds = timer,
                        categorizationRequiredPasses = passes,
                        knownCards = known,
                        initialKnownCardsLoaded = true // Mark loading complete
                        // Combo settings will use defaults from QuizUiState
                    )
                }
            }.take(1) // Ensure this runs once for initial setup
                .collect() // Collect to trigger the flow
        }
    }

    // --- Navigation ---
    fun navigateTo(screen: AppScreen) {
        val currentState = _uiState.value // Get state *before* update

        if (currentState.currentScreen == AppScreen.CATEGORIZATION && screen != AppScreen.CATEGORIZATION) {
            categorizationTimerJob?.cancel()
        }
        if (currentState.currentScreen == AppScreen.COMBOS_QUIZZING && screen != AppScreen.COMBOS_QUIZZING) {
            comboQuizTimerJob?.cancel()
        }


        _uiState.update {
            it.copy(
                currentScreen = screen,
                feedbackMessage = null, // Clear feedback on navigate
                categorizationTimerValue = if (currentState.currentScreen == AppScreen.CATEGORIZATION && screen != AppScreen.CATEGORIZATION) it.categorizationTimerDurationSeconds.toFloat() else it.categorizationTimerValue,
                categorizationInputEnabled = true,
                comboQuizTimerValue = if (currentState.currentScreen == AppScreen.COMBOS_QUIZZING && screen != AppScreen.COMBOS_QUIZZING) it.comboQuizTimerDurationSeconds.toFloat() else it.comboQuizTimerValue,
                comboQuizInputEnabled = true
            )
        }
    }

    // --- Categorization Logic ---

    fun startCategorization() {
        if (!_uiState.value.initialKnownCardsLoaded) return

        val initialCards = _uiState.value.allCards.keys.sorted()
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.CATEGORIZATION,
                categorizationPass = 1,
                categorizationCardsCurrentPass = initialCards,
                categorizationCurrentIndex = 0,
                categorizationFailedCards = emptySet(),
                categorizationNextPassCandidates = emptySet(),
                isLoading = false
            )
        }
        startCardTimer()
    }

    private fun startCardTimer() {
        categorizationTimerJob?.cancel()
        val currentState = _uiState.value
        val timeoutMs = currentState.categorizationTimerDurationSeconds * 1000L
        val initialTimerValue = timeoutMs / 1000f

        _uiState.update { it.copy(categorizationTimerValue = initialTimerValue, categorizationInputEnabled = true) }
        categorizationTimerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val remaining = timeoutMs - (System.currentTimeMillis() - startTime)
                _uiState.update { it.copy(categorizationTimerValue = (remaining / 1000f).coerceAtLeast(0f)) }
                delay(50)
            }
            if (_uiState.value.categorizationInputEnabled) {
                _uiState.update { it.copy(categorizationTimerValue = 0f) }
                handleCategorizationTimeout()
            }
        }
    }

    fun submitCategorizationGuess(guessInt: Int) {
        categorizationTimerJob?.cancel()
        _uiState.update { it.copy(categorizationInputEnabled = false)}

        val currentState = _uiState.value
        val cardName = currentState.currentCardName ?: return
        val correctCost = currentState.currentCorrectCost ?: return

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
            moveToNextCategorizationCard(correct)
        }
    }

    private fun handleCategorizationTimeout() {
        viewModelScope.launch {
            if (!_uiState.value.categorizationInputEnabled) return@launch
            _uiState.update { it.copy(categorizationInputEnabled = false)}
            val currentState = _uiState.value
            val cardName = currentState.currentCardName ?: return@launch
            _uiState.update {
                it.copy(categorizationFailedCards = it.categorizationFailedCards + cardName)
            }
            showTemporaryFeedback("Time's up! Marked as unknown.") {
                moveToNextCategorizationCard(false)
            }
        }
    }

    private fun moveToNextCategorizationCard(wasCorrectThisTime: Boolean) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val nextIndex = currentState.categorizationCurrentIndex + 1
            if (nextIndex < currentState.categorizationCardsCurrentPass.size) {
                _uiState.update {
                    it.copy(categorizationCurrentIndex = nextIndex, feedbackMessage = null)
                }
                startCardTimer()
            } else {
                finishCategorizationPass()
            }
        }
    }

    private fun finishCategorizationPass() {
        val currentState = _uiState.value
        val currentPass = currentState.categorizationPass
        val requiredPasses = currentState.categorizationRequiredPasses
        val successfulThisPass = currentState.categorizationNextPassCandidates
        val failedOverall = currentState.categorizationFailedCards

        if (currentPass >= requiredPasses || successfulThisPass.isEmpty()) {
            val finalKnown = if (currentPass >= requiredPasses) successfulThisPass else emptySet()
            val finalUnknown = currentState.allCards.keys.filter { it in failedOverall || it !in finalKnown }.toSet()
            viewModelScope.launch {
                try {
                    settingsRepository.saveKnownCards(finalKnown)
                    _uiState.update {
                        it.copy(
                            currentScreen = AppScreen.HOME,
                            knownCards = finalKnown,
                            unknownCards = finalUnknown,
                            categorizationPass = 0,
                            categorizationCardsCurrentPass = emptyList(),
                            categorizationCurrentIndex = 0,
                            categorizationFailedCards = emptySet(),
                            categorizationNextPassCandidates = emptySet(),
                            feedbackMessage = "Categorization Complete & Saved: ${finalKnown.size} Known"
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            currentScreen = AppScreen.HOME,
                            feedbackMessage = "Categorization Complete. Error saving results.",
                            knownCards = finalKnown, unknownCards = finalUnknown,
                            categorizationPass = 0, categorizationCardsCurrentPass = emptyList(),
                            categorizationCurrentIndex = 0, categorizationFailedCards = emptySet(),
                            categorizationNextPassCandidates = emptySet()
                        )
                    }
                }
            }
        } else {
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
            if (cardsForNextPass.isNotEmpty()) startCardTimer() else finishCategorizationPass()
        }
    }

    // --- Quiz Setup ---
    fun selectQuizSetOption(set: QuizSet) { _uiState.update { it.copy(selectedQuizSet = set) } }
    fun selectQuizOrderOption(order: QuizOrder) { _uiState.update { it.copy(selectedQuizOrder = order) } }

    fun startQuiz() {
        if (!_uiState.value.initialKnownCardsLoaded) return
        val currentState = _uiState.value
        val cardPool = when (currentState.selectedQuizSet) {
            QuizSet.ALL -> currentState.allCards.keys
            QuizSet.KNOWN -> currentState.knownCards.ifEmpty { currentState.allCards.keys }
            QuizSet.UNKNOWN -> (currentState.allCards.keys - currentState.knownCards).ifEmpty { currentState.allCards.keys }
        }
        if (cardPool.isEmpty()) {
            _uiState.update { it.copy(feedbackMessage = "Selected card set is empty!") }
            return
        }
        var initialQuizCards = cardPool.toList()
        initialQuizCards = if (currentState.selectedQuizOrder == QuizOrder.RANDOM) initialQuizCards.shuffled(Random(System.currentTimeMillis())) else initialQuizCards.sorted()
        val quizSetName = when (currentState.selectedQuizSet) {
            QuizSet.ALL -> "All Cards"; QuizSet.KNOWN -> "Known Cards"; QuizSet.UNKNOWN -> "Unknown Cards"
        }
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.QUIZZING,
                quizCardsCurrentPass = initialQuizCards, quizCurrentIndex = 0,
                quizCardsForNextPass = mutableSetOf(), quizCurrentPassNumber = 1,
                quizStats = QuizStats(totalCardsInQuiz = initialQuizCards.size, startTime = System.currentTimeMillis(), quizSetName = quizSetName),
                feedbackMessage = null, isLoading = false
            )
        }
    }

    // --- Quizzing Logic ---
    fun submitQuizGuess(guessInt: Int) {
        val currentState = _uiState.value
        val cardName = currentState.currentCardName ?: return
        val correctCost = currentState.currentCorrectCost ?: return
        val isFirstGuessForThisCardInPass = !currentState.quizCardsForNextPass.contains(cardName)
        _uiState.update { st -> st.copy(quizStats = st.quizStats.copy(totalGuesses = st.quizStats.totalGuesses + 1)) }
        if (guessInt == correctCost) {
            val correctFeedback = "Correct! ($correctCost Elixir)"
            _uiState.update { st ->
                st.copy(quizStats = if (isFirstGuessForThisCardInPass) st.quizStats.copy(correctFirstTry = st.quizStats.correctFirstTry + 1) else st.quizStats)
            }
            showTemporaryFeedback(correctFeedback) { moveToNextQuizCard() }
        } else {
            val incorrectFeedback = if (isFirstGuessForThisCardInPass) {
                _uiState.update { st ->
                    st.copy(quizCardsForNextPass = st.quizCardsForNextPass.apply { add(cardName) },
                            quizStats = st.quizStats.copy(neededPractice = st.quizStats.neededPractice + 1))
                }
                "Incorrect. Added to review."
            } else "Incorrect. Try again..."
            _uiState.update { it.copy(feedbackMessage = incorrectFeedback) }
        }
    }

    private fun moveToNextQuizCard() {
        viewModelScope.launch {
            val currentState = _uiState.value; val nextIndex = currentState.quizCurrentIndex + 1
            if (nextIndex < currentState.quizCardsCurrentPass.size) {
                _uiState.update { it.copy(quizCurrentIndex = nextIndex, feedbackMessage = null) }
            } else finishQuizPass()
        }
    }

    private fun finishQuizPass() {
        val currentState = _uiState.value; val cardsForReview = currentState.quizCardsForNextPass.toList()
        if (cardsForReview.isEmpty()) {
            _uiState.update { st ->
                st.copy(currentScreen = AppScreen.RESULTS,
                        quizStats = st.quizStats.copy(endTime = System.currentTimeMillis(), totalPasses = st.quizCurrentPassNumber),
                        feedbackMessage = "Quiz Complete!")
            }
        } else {
            var nextPassCards = if (currentState.selectedQuizOrder == QuizOrder.RANDOM) cardsForReview.shuffled(Random(System.currentTimeMillis())) else cardsForReview.sorted()
            _uiState.update {
                it.copy(quizCardsCurrentPass = nextPassCards, quizCurrentIndex = 0,
                        quizCardsForNextPass = mutableSetOf(), quizCurrentPassNumber = it.quizCurrentPassNumber + 1,
                        feedbackMessage = null)
            }
        }
    }

    // --- Settings Logic ---
    fun saveTimerSetting(seconds: Int) {
        viewModelScope.launch { settingsRepository.saveCategorizationTimer(seconds) }
        _uiState.update { it.copy(categorizationTimerDurationSeconds = seconds) }
    }
    fun savePassesSetting(passes: Int) {
        viewModelScope.launch { settingsRepository.saveCategorizationPasses(passes) }
        _uiState.update { it.copy(categorizationRequiredPasses = passes) }
    }

    // --- Utility ---
    private fun showTemporaryFeedback(message: String, onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(feedbackMessage = message) }
            delay(1200L); _uiState.update { it.copy(feedbackMessage = null) }; onFinished()
        }
    }
    fun clearFeedback() { _uiState.update { it.copy(feedbackMessage = null) } }

    // --- Decks Logic ---
    fun updateDeckNameInput(name: String) { _uiState.update { it.copy(deckNameInput = name) } }
    fun toggleCardSelectionForNewDeck(cardName: String) {
        _uiState.update { current ->
            val sel = current.selectedCardsForNewDeck
            if (sel.contains(cardName)) current.copy(selectedCardsForNewDeck = sel - cardName)
            else if (sel.size < 8) current.copy(selectedCardsForNewDeck = sel + cardName)
            else current.copy(feedbackMessage = "You can only select up to 8 cards.")
        }
    }
    fun saveNewDeck() {
        val current = _uiState.value
        if (current.deckNameInput.isBlank()) { _uiState.update { it.copy(feedbackMessage = "Deck name cannot be empty.") }; return }
        if (current.selectedCardsForNewDeck.size != 8) { _uiState.update { it.copy(feedbackMessage = "You must select exactly 8 cards.") }; return }
        val newDeck = Deck(name = current.deckNameInput, cards = current.selectedCardsForNewDeck)
        val updatedDecks = current.decks + newDeck
        viewModelScope.launch {
            try {
                settingsRepository.saveDecks(updatedDecks)
                _uiState.update { it.copy(decks = updatedDecks, deckNameInput = "", selectedCardsForNewDeck = emptySet(), feedbackMessage = "Deck '${newDeck.name}' saved successfully!") }
            } catch (e: Exception) { _uiState.update { it.copy(feedbackMessage = "Error saving deck: ${e.localizedMessage}") } }
        }
    }
    private fun loadDecks() {
        viewModelScope.launch { settingsRepository.decks.collect { ld -> _uiState.update { it.copy(decks = ld) } } }
    }

    // --- Combo Quiz Logic ---
    fun selectDeckForComboQuiz(deck: Deck) { _uiState.update { it.copy(selectedDeckForComboQuiz = deck) } }
    fun setComboTimerEnabled(enabled: Boolean) {
        _uiState.update { it.copy(comboQuizTimerEnabled = enabled) }
        // viewModelScope.launch { settingsRepository.saveComboTimerEnabled(enabled) } // Persistence excluded
    }
    fun setComboTimerDuration(seconds: Int) {
        _uiState.update { it.copy(comboQuizTimerDurationSeconds = seconds, comboQuizTimerValue = seconds.toFloat()) }
        // viewModelScope.launch { settingsRepository.saveComboTimerDuration(seconds) } // Persistence excluded
    }
    fun setComboNumberOfQuestions(count: Int) {
        val clampedCount = count.coerceIn(5, 50)
        _uiState.update { it.copy(comboQuizNumberOfQuestions = clampedCount) }
        // viewModelScope.launch { settingsRepository.saveComboNumQuestions(clampedCount) } // Persistence excluded
    }
    fun setComboCardsPerCombo(count: Int) {
        val clampedCount = count.coerceIn(2, 3)
        _uiState.update { it.copy(comboQuizCardsPerCombo = clampedCount) }
        // viewModelScope.launch { settingsRepository.saveComboCardsPerCombo(clampedCount) } // Persistence excluded
    }

    fun startComboQuiz() {
        val currentState = _uiState.value
        if (currentState.selectedDeckForComboQuiz == null) {
            _uiState.update { it.copy(feedbackMessage = "Please select a deck first!") }
            return
        }
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.COMBOS_QUIZZING,
                comboQuizCurrentQuestionIndex = 0,
                comboQuizCorrectAnswers = 0,
                feedbackMessage = null,
                comboQuizInputEnabled = true,
                comboQuizTimerValue = it.comboQuizTimerDurationSeconds.toFloat() // Reset timer value
            )
        }
        generateNextCombo()
    }

    private fun generateNextCombo() {
        val currentState = _uiState.value
        if (currentState.comboQuizCurrentQuestionIndex >= currentState.comboQuizNumberOfQuestions) {
            navigateTo(AppScreen.COMBOS_RESULTS)
            return
        }

        currentState.selectedDeckForComboQuiz?.cards?.let { deckCards ->
            if (deckCards.size < currentState.comboQuizCardsPerCombo) {
                // Should not happen if deck validation is correct (8 cards)
                _uiState.update { it.copy(feedbackMessage = "Selected deck has too few cards for this combo size.", currentScreen = AppScreen.COMBOS_SETUP) }
                return
            }
            val combo = deckCards.shuffled().take(currentState.comboQuizCardsPerCombo)
            val cost = combo.sumOf { cardName -> currentState.allCards[cardName] ?: 0 }
            _uiState.update {
                it.copy(
                    currentComboCards = combo,
                    currentComboCorrectCost = cost,
                    comboQuizInputEnabled = true,
                    feedbackMessage = null, // Clear previous feedback
                    comboQuizTimerValue = it.comboQuizTimerDurationSeconds.toFloat() // Reset timer for new question
                )
            }
            if (currentState.comboQuizTimerEnabled) {
                startComboCardTimer()
            }
        } ?: run {
            // Fallback if deck is somehow null, though startComboQuiz should prevent this
             _uiState.update { it.copy(currentScreen = AppScreen.COMBOS_SETUP, feedbackMessage = "Error: No deck selected for combo quiz.") }
        }
    }

    private fun startComboCardTimer() {
        comboQuizTimerJob?.cancel()
        val currentState = _uiState.value
        val timeoutMs = currentState.comboQuizTimerDurationSeconds * 1000L
        _uiState.update { it.copy(comboQuizTimerValue = timeoutMs / 1000f, comboQuizInputEnabled = true) }

        comboQuizTimerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val remaining = timeoutMs - (System.currentTimeMillis() - startTime)
                _uiState.update { it.copy(comboQuizTimerValue = (remaining / 1000f).coerceAtLeast(0f)) }
                delay(50)
            }
            if (_uiState.value.comboQuizInputEnabled) { // Check if still enabled (i.e., not answered)
                _uiState.update { it.copy(comboQuizTimerValue = 0f) }
                handleComboTimeout()
            }
        }
    }

    fun submitComboGuess(guessInt: Int) {
        comboQuizTimerJob?.cancel()
        _uiState.update { it.copy(comboQuizInputEnabled = false) }

        val currentState = _uiState.value
        val correct = guessInt == currentState.currentComboCorrectCost
        val feedback: String

        if (correct) {
            _uiState.update { it.copy(comboQuizCorrectAnswers = it.comboQuizCorrectAnswers + 1) }
            feedback = "Correct!"
        } else {
            feedback = "Incorrect! Cost was ${currentState.currentComboCorrectCost}"
        }

        showTemporaryFeedback(feedback) {
            _uiState.update { it.copy(comboQuizCurrentQuestionIndex = it.comboQuizCurrentQuestionIndex + 1) }
            generateNextCombo()
        }
    }

    private fun handleComboTimeout() {
        viewModelScope.launch {
            if (!_uiState.value.comboQuizInputEnabled) return@launch // Already processed submission
            _uiState.update { it.copy(comboQuizInputEnabled = false) }
            showTemporaryFeedback("Time's up!") {
                _uiState.update { it.copy(comboQuizCurrentQuestionIndex = it.comboQuizCurrentQuestionIndex + 1) }
                generateNextCombo()
            }
        }
    }
}