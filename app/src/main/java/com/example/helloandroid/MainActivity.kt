package com.example.helloandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.helloandroid.ui.theme.HelloAndroidTheme

import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.helloandroid.data.SettingsRepository // Import repository
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Use AutoMirrored
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator // Already likely there
import com.example.helloandroid.ui.components.NumberPad
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
//import androidx.compose.material.icons.automirrored.filled.ArrowBack // Already imported
//import androidx.compose.material3.* // Already imported
//import androidx.compose.runtime.* // Already imported
import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp // Already imported
import androidx.lifecycle.viewmodel.compose.viewModel // Use this for factory
//import androidx.lifecycle.compose.collectAsStateWithLifecycle // Already imported
import com.example.helloandroid.data.Deck
import com.example.helloandroid.ui.components.NumberPadLayout
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// --- ViewModel Factory ---
// Needed to pass arguments (the repository) to the ViewModel constructor
class QuizViewModelFactory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QuizViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QuizViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create repository instance (needs context)
        val settingsRepository = SettingsRepository(applicationContext)

        setContent {
            HelloAndroidTheme {
                // Instantiate ViewModel using the factory
                val viewModel: QuizViewModel = viewModel(
                    factory = QuizViewModelFactory(settingsRepository)
                )
                QuizApp(viewModel)
            }
        }
    }
}

@Composable
fun QuizApp(viewModel: QuizViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Show loading indicator until initial data is loaded
        if (!uiState.initialKnownCardsLoaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Text("Loading Settings...", modifier = Modifier.padding(top = 60.dp))
            }
        } else {
            // Once loaded, show the correct screen
            when (uiState.currentScreen) {
                AppScreen.HOME -> HomeScreen(uiState, viewModel)
                AppScreen.CATEGORIZATION -> CategorizationScreen(uiState, viewModel)
                AppScreen.QUIZ_SETUP -> QuizSetupScreen(uiState, viewModel)
                AppScreen.QUIZZING -> QuizzingScreen(uiState, viewModel)
                AppScreen.RESULTS -> ResultsScreen(uiState, viewModel)
                AppScreen.CATEGORIZATION_SETUP -> CategorizationSetupScreen(uiState, viewModel)
                AppScreen.DECKS -> DecksScreen(uiState, viewModel) // Handle Decks screen
                AppScreen.COMBOS_SETUP -> CombosSetupScreen(uiState, viewModel)
                AppScreen.COMBOS_QUIZZING -> CombosQuizzingScreen(uiState, viewModel)
                AppScreen.COMBOS_RESULTS -> CombosResultsScreen(uiState, viewModel)
            }
        }

        // Snackbar for global feedback can be added here using Scaffold if needed
    }
}


// --- Screen Composables ---

@Composable
fun HomeScreen(uiState: QuizUiState, viewModel: QuizViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Clash Royale Quiz", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = { viewModel.navigateTo(AppScreen.CATEGORIZATION_SETUP) }) {
            // Dynamically show passes/timer in button text
            Text("Run Categorization (${uiState.categorizationRequiredPasses} passes, ${uiState.categorizationTimerDurationSeconds}s timer)")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.navigateTo(AppScreen.QUIZ_SETUP) }) {
            Text("Start Main Quiz")
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Add Settings Button
        OutlinedButton(onClick = { viewModel.navigateTo(AppScreen.CATEGORIZATION_SETUP) }) {
            Text("Categorization Setup")
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Add Decks Button
        OutlinedButton(onClick = { viewModel.navigateTo(AppScreen.DECKS) }) {
            Text("Manage Decks")
        }
        Spacer(modifier = Modifier.height(16.dp)) // Spacer after Decks button
        // Add Combos Quiz Button
        OutlinedButton(onClick = { viewModel.navigateTo(AppScreen.COMBOS_SETUP) }) {
            Text("Start Combos Quiz")
        }
        Spacer(modifier = Modifier.height(24.dp))


        if (uiState.knownCardCount > 0 || uiState.unknownCardCount > 0) {
            Text("Current Saved Lists:", style = MaterialTheme.typography.titleMedium)
            Text("Known: ${uiState.knownCardCount}")
            Text("Unknown: ${uiState.unknownCardCount}")
            Spacer(modifier = Modifier.height(16.dp))
        }

        uiState.feedbackMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            // Consider clearing feedback after showing or using Snackbar
        }
    }
}

@Composable
fun CategorizationScreen(uiState: QuizUiState, viewModel: QuizViewModel) {
    // Remove: var guess by rememberSaveable { mutableStateOf("") }
    val currentCard = uiState.currentCardName

    // Remove: LaunchedEffect(currentCard) { guess = "" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Use Arrangement.SpaceBetween to push Back button potentially to top, content center, numpad bottom
        // Or keep Arrangement.Center and place IconButton absolutely or within the Column structure
        // Let's add the back button at the top within the Column
    ) {
        // Back Button Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateTo(AppScreen.HOME) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
            }
            Spacer(modifier = Modifier.weight(1f)) // Push title towards center if needed
            Text(
                "Categorization Pass ${uiState.categorizationPass}/${uiState.categorizationRequiredPasses}", // Use required passes from state
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(2f)
            )
            Spacer(modifier = Modifier.weight(1f)) // Balance spacer
        }


        Spacer(modifier = Modifier.height(16.dp)) // Space below title row
        Text("Card ${uiState.categorizationCurrentIndex + 1} / ${uiState.categorizationCardsCurrentPass.size}")
        Spacer(modifier = Modifier.height(32.dp))

        if (currentCard != null) {
            Text(currentCard, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = {
                    // Avoid division by zero if timer is somehow set to 0
                    val totalDuration = uiState.categorizationTimerDurationSeconds.toFloat().coerceAtLeast(1f)
                    (uiState.categorizationTimerValue / totalDuration).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Text(String.format("%.1fs", uiState.categorizationTimerValue))

            Spacer(modifier = Modifier.height(24.dp))

            // Remove OutlinedTextField and Submit Button
            // Add NumberPad
            NumberPad(
                onNumberClick = { number ->
                    viewModel.submitCategorizationGuess(number) // Pass the Int directly
                },
                enabled = uiState.categorizationInputEnabled // Pass enabled state
            )

            Spacer(modifier = Modifier.height(24.dp)) // Space above feedback
            uiState.feedbackMessage?.let {
                Text(
                    it,
                    color = if (it.startsWith("Correct")) Color.Green else MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge // Make feedback a bit larger
                )
            }

        } else {
            // Potentially show a loading indicator or completion message briefly
            Text("Loading card...")
        }

        Spacer(modifier = Modifier.weight(1f)) // Push content towards center if using SpaceBetween
    }
}

@Composable
fun CategorizationSetupScreen(uiState: QuizUiState, viewModel: QuizViewModel) {
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()), // Make scrollable
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // --- Back Button Row ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateTo(AppScreen.HOME) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
            }
            Text(
                "Categorization Setup",
                style = MaterialTheme.typography.headlineSmall, // Adjusted style
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f) // Center title
            )
            Spacer(modifier = Modifier.width(48.dp)) // Match IconButton width for balance
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Timer Duration Setting ---
        Text("Categorization Timer (seconds)", style = MaterialTheme.typography.titleMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(2, 3, 4, 5).forEach { seconds -> // Example options
                Button(
                    onClick = {
                        viewModel.saveTimerSetting(seconds)
                        feedbackMessage = "Timer saved: $seconds seconds"
                    },
                    enabled = uiState.categorizationTimerDurationSeconds != seconds // Disable current selection
                ) {
                    Text("$seconds")
                }
            }
        }
        Text("Current: ${uiState.categorizationTimerDurationSeconds} seconds")

        Spacer(modifier = Modifier.height(32.dp))

        // --- Required Passes Setting ---
        Text("Categorization Required Passes", style = MaterialTheme.typography.titleMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(2, 3, 4).forEach { passes -> // Example options
                Button(
                    onClick = {
                        viewModel.savePassesSetting(passes)
                        feedbackMessage = "Passes saved: $passes"
                    },
                    enabled = uiState.categorizationRequiredPasses != passes // Disable current selection
                ) {
                    Text("$passes")
                }
            }
        }
        Text("Current: ${uiState.categorizationRequiredPasses} passes")

        Spacer(modifier = Modifier.height(32.dp))

        // --- Start Categorization Button ---
        Button(onClick = { viewModel.startCategorization() }) {
            Text("Start Categorization")
        }

        Spacer(modifier = Modifier.height(16.dp))


        // --- Feedback ---
        feedbackMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
            // Auto-clear feedback after a delay
            LaunchedEffect(feedbackMessage) {
                delay(2000L)
                feedbackMessage = null
            }
        }
    }
}

@Composable
fun QuizSetupScreen(uiState: QuizUiState, viewModel: QuizViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()), // Allow scrolling if options get long
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Quiz Setup", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // --- Card Set Selection ---
        Text("Choose Card Set:", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = uiState.selectedQuizSet == QuizSet.ALL,
                onClick = { viewModel.selectQuizSetOption(QuizSet.ALL) }
            )
            Text("All (${uiState.allCards.size})")
        }
        // Only show Known/Unknown if categorization was run and produced results
        if (uiState.knownCardCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = uiState.selectedQuizSet == QuizSet.KNOWN,
                    onClick = { viewModel.selectQuizSetOption(QuizSet.KNOWN) }
                )
                Text("Known (${uiState.knownCardCount})")
            }
        }
        if (uiState.unknownCardCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = uiState.selectedQuizSet == QuizSet.UNKNOWN,
                    onClick = { viewModel.selectQuizSetOption(QuizSet.UNKNOWN) }
                )
                Text("Unknown (${uiState.unknownCardCount})")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))


        // --- Quiz Order Selection ---
        Text("Choose Quiz Order:", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = uiState.selectedQuizOrder == QuizOrder.RANDOM,
                onClick = { viewModel.selectQuizOrderOption(QuizOrder.RANDOM) }
            )
            Text("Random")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = uiState.selectedQuizOrder == QuizOrder.SEQUENTIAL,
                onClick = { viewModel.selectQuizOrderOption(QuizOrder.SEQUENTIAL) }
            )
            Text("Sequential (A-Z)")
        }
        Spacer(modifier = Modifier.height(32.dp))

        // --- Start Button ---
        Button(onClick = { viewModel.startQuiz() }) {
            Text("Start Quiz!")
        }

        Spacer(modifier = Modifier.height(16.dp))
        uiState.feedbackMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}


@Composable
fun QuizzingScreen(uiState: QuizUiState, viewModel: QuizViewModel) {
    // Remove: var guess by rememberSaveable { mutableStateOf("") }
    val currentCard = uiState.currentCardName
    val totalCardsInPass = uiState.quizCardsCurrentPass.size

    // Remove: LaunchedEffect(currentCard) { guess = "" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        // verticalArrangement = Arrangement.Center // Keep centered arrangement
    ) {
        // Back Button Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateTo(AppScreen.HOME) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
            }
            Spacer(modifier = Modifier.weight(1f)) // Push title towards center
            Text(
                if (uiState.quizCurrentPassNumber == 1) "Initial Pass" else "Review Pass ${uiState.quizCurrentPassNumber - 1}",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(2f) // Give title space
            )
            Spacer(modifier = Modifier.weight(1f)) // Balance spacer
        }

        Spacer(modifier = Modifier.height(16.dp)) // Space below title row

        if (totalCardsInPass > 0) {
            Text("Card ${uiState.quizCurrentIndex + 1} / $totalCardsInPass")
        }
        Spacer(modifier = Modifier.height(32.dp))


        if (currentCard != null) {
            Text(currentCard, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))

            // Remove OutlinedTextField and Submit Button
            // Add NumberPad
            NumberPad(
                onNumberClick = { number ->
                    viewModel.submitQuizGuess(number) // Pass the Int directly
                }
                // No enabled flag needed here unless you add loading states for quiz submission
            )

            Spacer(modifier = Modifier.height(24.dp)) // Space above feedback
            uiState.feedbackMessage?.let {
                Text(
                    it,
                    color = when {
                        it.startsWith("Correct") -> Color.Green
                        it.contains("review", ignoreCase = true) -> MaterialTheme.colorScheme.error // Consider an amber/orange color
                        else -> MaterialTheme.colorScheme.error
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge // Make feedback a bit larger
                )
            }

        } else {
            // This state should transition quickly, but handle it just in case
            Text("Loading next card or finishing...")
            CircularProgressIndicator() // Show a spinner if loading takes time
        }
        Spacer(modifier = Modifier.weight(1f)) // Pushes content up if column isn't full
    }
}

@Composable
fun ResultsScreen(uiState: QuizUiState, viewModel: QuizViewModel) {
    val stats = uiState.quizStats

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()), // Make results scrollable
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Quiz Complete!", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Text("--- Quiz Summary ---", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Text("Card Set Tested: ${stats.quizSetName}")
        Text("Total Cards in Set: ${stats.totalCardsInQuiz}")
        Text("Correct on First Try: ${stats.correctFirstTry}")
        Text("Needed Extra Practice: ${stats.neededPractice}")
        Text("Total Guesses Made: ${stats.totalGuesses}")
        Text("Total Passes Taken: ${stats.totalPasses}")

        if (stats.totalCardsInQuiz > 0) {
            Text(String.format("First Try Accuracy: %.1f%%", stats.firstTryAccuracy))
            Text(String.format("Avg Guesses per Card: %.2f", stats.avgGuesses))
        }

        Text("Total Time: ${stats.durationMinutes}m ${stats.durationRemainderSeconds}s")

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = { viewModel.navigateTo(AppScreen.HOME) }) {
            Text("Back to Home")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class) // For FlowRow and ExposedDropdownMenuBox
@Composable
fun DecksScreen(uiState: QuizUiState, viewModel: QuizViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Back Button and Title ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateTo(AppScreen.HOME) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
            }
            Text(
                "Manage Decks",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(48.dp)) // Balance for IconButton
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Create New Deck Section ---
        Text("Create New Deck", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.deckNameInput,
            onValueChange = { viewModel.updateDeckNameInput(it) },
            label = { Text("Deck Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("Select 8 Cards (Selected: ${uiState.selectedCardsForNewDeck.size}/8)", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        // Scrollable area for card selection
        Box(modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp) // Limit height of card selection area
            .verticalScroll(rememberScrollState())
        ) {
            FlowRow( // Auto-wraps items to next line
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                uiState.allCards.keys.sorted().forEach { cardName ->
                    val isSelected = uiState.selectedCardsForNewDeck.contains(cardName)
                    Button(
                        onClick = { viewModel.toggleCardSelectionForNewDeck(cardName) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(2.dp) // Minimal padding around each button
                    ) {
                        Text(cardName, fontSize = 12.sp) // Smaller font for card names
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Selected: ${uiState.selectedCardsForNewDeck.joinToString(", ")}")


        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.saveNewDeck() },
            enabled = uiState.deckNameInput.isNotBlank() && uiState.selectedCardsForNewDeck.size == 8,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Deck")
        }

        uiState.feedbackMessage?.let {
            Text(
                it,
                color = if (it.contains("Error") || it.contains("cannot") || it.contains("must")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            // Auto-clear feedback after a delay
            LaunchedEffect(it) { // it refers to feedbackMessage
                delay(3000L) // Show for 3 seconds
                viewModel.clearFeedback()
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- List of Existing Decks ---
        Text("Your Decks (${uiState.decks.size})", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.decks.isEmpty()) {
            Text("No decks saved yet. Create one above!")
        } else {
            uiState.decks.forEach { deck ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(deck.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Cards: ${deck.cards.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// --- New Composables for Combos Quiz ---

@OptIn(ExperimentalMaterial3Api::class) // For ExposedDropdownMenuBox
@Composable
fun CombosSetupScreen(uiState: QuizUiState, viewModel: QuizViewModel) {
    var deckMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()), // Make screen scrollable
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Back Button and Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateTo(AppScreen.HOME) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
            }
            Text(
                "Combos Quiz Setup",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(48.dp)) // Balance for IconButton
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Deck Selection
        Text("Select Deck", style = MaterialTheme.typography.titleMedium)
        if (uiState.decks.isEmpty()) {
            Text("No decks available. Please create a deck first in 'Manage Decks'.")
        } else {
            ExposedDropdownMenuBox(
                expanded = deckMenuExpanded,
                onExpandedChange = { deckMenuExpanded = !deckMenuExpanded }
            ) {
                OutlinedTextField(
                    value = uiState.selectedDeckForComboQuiz?.name ?: "Select a deck",
                    onValueChange = {}, // Not directly editable
                    readOnly = true,
                    label = { Text("Deck") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deckMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = deckMenuExpanded,
                    onDismissRequest = { deckMenuExpanded = false }
                ) {
                    uiState.decks.forEach { deck ->
                        DropdownMenuItem(
                            text = { Text(deck.name) },
                            onClick = {
                                viewModel.selectDeckForComboQuiz(deck) // ViewModel function
                                deckMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Timer Settings
        Text("Timer Settings", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable Timer")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = uiState.comboQuizTimerEnabled,
                onCheckedChange = { viewModel.setComboTimerEnabled(it) // ViewModel function
                }
            )
        }
        if (uiState.comboQuizTimerEnabled) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                listOf(5, 10, 15).forEach { duration ->
                    Button(
                        onClick = { viewModel.setComboTimerDuration(duration) // ViewModel function
                        },
                        enabled = uiState.comboQuizTimerDurationSeconds != duration
                    ) {
                        Text("${duration}s")
                    }
                }
            }
            Text("Current: ${uiState.comboQuizTimerDurationSeconds}s")
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Number of Questions
        Text("Number of Questions: ${uiState.comboQuizNumberOfQuestions}", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = uiState.comboQuizNumberOfQuestions.toFloat(),
            onValueChange = { viewModel.setComboNumberOfQuestions(it.roundToInt()) // ViewModel function
            },
            valueRange = 5f..50f,
            steps = (50-5-1), // (max - min - 1 step) to make each step an integer
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Cards per Combo
        Text("Cards per Combo", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 3).forEach { count ->
                Button(
                    onClick = { viewModel.setComboCardsPerCombo(count) // ViewModel function
                    },
                    enabled = uiState.comboQuizCardsPerCombo != count
                ) {
                    Text("$count Cards")
                }
            }
        }
        Text("Current: ${uiState.comboQuizCardsPerCombo}")
        Spacer(modifier = Modifier.height(32.dp))

        // Start Quiz Button
        Button(
            onClick = { viewModel.startComboQuiz() // ViewModel function
            },
            enabled = uiState.selectedDeckForComboQuiz != null,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Start Combos Quiz")
        }

        uiState.feedbackMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            LaunchedEffect(it) {
                delay(3000)
                viewModel.clearFeedback() // ViewModel function
            }
        }
    }
}

@Composable
fun CombosQuizzingScreen(uiState: QuizUiState, viewModel: QuizViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back Button and Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                // TODO: Implement confirmation dialog if quiz is in progress
                viewModel.navigateTo(AppScreen.HOME) // Or COMBOS_SETUP, ends quiz
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
            }
            Text(
                "Combos Quiz",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(48.dp)) // Balance for IconButton
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Question Counter
        Text(
            "Question ${uiState.comboQuizCurrentQuestionIndex + 1} / ${uiState.comboQuizNumberOfQuestions}",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Timer Display (if enabled)
        if (uiState.comboQuizTimerEnabled) {
            LinearProgressIndicator(
                progress = { uiState.comboQuizTimerValue / uiState.comboQuizTimerDurationSeconds.toFloat() },
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Text(String.format("%.1fs", uiState.comboQuizTimerValue))
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Display Current Combo Cards
        if (uiState.currentComboCards.isNotEmpty()) {
            Text(
                text = uiState.currentComboCards.joinToString(" + "),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
        } else {
            Text("Loading combo...", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(modifier = Modifier.height(24.dp))

        // NumberPad for Combos
        NumberPad(
            onNumberClick = { guess -> viewModel.submitComboGuess(guess) },
            enabled = uiState.comboQuizInputEnabled,
            layoutType = NumberPadLayout.COMBOS
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Feedback Message
        uiState.feedbackMessage?.let {
            Text(
                it,
                color = if (it.startsWith("Correct")) Color.Green else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.weight(1f)) // Push content to center
    }
}

@Composable
fun CombosResultsScreen(uiState: QuizUiState, viewModel: QuizViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Combos Quiz Complete!", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        val accuracy = if (uiState.comboQuizNumberOfQuestions > 0) {
            (uiState.comboQuizCorrectAnswers.toDouble() / uiState.comboQuizNumberOfQuestions * 100)
        } else {
            0.0 // Avoid division by zero if no questions were asked
        }
        Text(
            "Accuracy: ${String.format("%.1f", accuracy)}%",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "Score: ${uiState.comboQuizCorrectAnswers} / ${uiState.comboQuizNumberOfQuestions}",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { viewModel.navigateTo(AppScreen.COMBOS_SETUP) }) {
                Text("Play Again")
            }
            Button(onClick = { viewModel.navigateTo(AppScreen.HOME) }) {
                Text("Back to Home")
            }
        }
    }
}

// --- Preview --- (Optional, helps in Android Studio design pane)
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    HelloAndroidTheme {
        // Preview one of the screens, maybe the home screen
        // Note: Previews won't have a working ViewModel by default
        // You might need to create a dummy state for previewing complex screens
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Preview Mode")
            Button(onClick = {}) { Text("Sample Button") }
        }
        // Or provide a dummy ViewModel/State if needed for more complex previews
        // val previewState = QuizUiState(currentScreen = AppScreen.HOME)
        // HomeScreen(uiState = previewState, viewModel = // Need a dummy/preview ViewModel)
    }
}