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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel // Use this for factory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

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
                AppScreen.SETTINGS -> SettingsScreen(uiState, viewModel) // Add Settings case
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

        Button(onClick = { viewModel.startCategorization() }) {
            // Dynamically show passes/timer in button text
            Text("Run Categorization (${uiState.categorizationRequiredPasses} passes, ${uiState.categorizationTimerDurationSeconds}s timer)")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.navigateTo(AppScreen.QUIZ_SETUP) }) {
            Text("Start Main Quiz")
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Add Settings Button
        OutlinedButton(onClick = { viewModel.navigateTo(AppScreen.SETTINGS) }) {
            Text("Settings")
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
fun SettingsScreen(uiState: QuizUiState, viewModel: QuizViewModel) {
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
                "Settings",
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