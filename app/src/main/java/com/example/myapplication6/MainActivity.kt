package com.example.myapplication6

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.random.Random
import androidx.compose.ui.text.font.FontStyle
// Step 1: Make sure the imports are correct
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// Screen routes definition
sealed class Screen(val route: String) {
    object TopLevel : Screen("top_level")
    object MiddleLevel : Screen("middle_level/{theme}") {
        fun createRoute(theme: String) = "middle_level/$theme"
    }
    object Journal : Screen("journal")
}

// Data classes
data class Quote(
    val content: String,
    val author: String,
    val category: List<String>? = emptyList()
)






data class TopLevelTile(val title: String, val description: String = "")

// Quote manager singleton
object QuoteManager {
    private var quotes: List<Quote> = emptyList()
    private val usedQuotes = mutableMapOf<String, MutableSet<Int>>()

    fun initialize(context: Context) {
        try {
            val jsonString = context.assets.open("quotes.json").bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<Quote>>() {}.type
            val parsedQuotes: List<Quote>? = Gson().fromJson(jsonString, listType)
            quotes = parsedQuotes?.map { quote ->
                // Ensure category is never null
                quote.copy(category = quote.category ?: emptyList())
            } ?: emptyList()
            Log.d("QuoteManager", "Initialized with ${quotes.size} quotes")
        } catch (e: IOException) {
            Log.e("QuoteManager", "Error reading JSON file: ${e.message}")
            quotes = emptyList()
        } catch (e: com.google.gson.JsonSyntaxException) {
            Log.e("QuoteManager", "Error parsing JSON: ${e.message}")
            quotes = emptyList()
        } catch (e: Exception) {
            Log.e("QuoteManager", "Unexpected error: ${e.message}")
            quotes = emptyList()
        }
    }

    fun getRandomQuote(category: String? = null): Quote? {
        if (quotes.isEmpty()) {
            Log.w("QuoteManager", "No quotes loaded. Make sure QuoteManager.initialize() is called.")
            return null
        }

        // Map specific categories to related terms to expand search
        val searchTerms = when (category?.lowercase()) {
            "motivation" -> listOf("motivation", "inspiration", "potential", "passion", "action")
            "happiness" -> listOf("happiness", "joy", "contentment", "peace", "tranquility")
            "inspiration" -> listOf("inspiration", "wisdom", "passion", "potential")
            else -> listOf(category?.lowercase() ?: "")
        }

        val filteredQuotes = if (category != null) {
            quotes.filter { quote ->
                quote.category?.any { cat ->
                    searchTerms.any { term -> cat.equals(term, ignoreCase = true) }
                } == true
            }
        } else {
            quotes
        }

        // If no quotes found with the expanded search, return a random quote
        if (filteredQuotes.isEmpty()) {
            Log.w("QuoteManager", "No quotes found for category: $category, returning random quote")
            return quotes.random()
        }

        val categoryKey = category ?: "all"
        if (!usedQuotes.containsKey(categoryKey)) {
            usedQuotes[categoryKey] = mutableSetOf()
        }

        val usedQuoteIndices = usedQuotes[categoryKey]!!

        if (usedQuoteIndices.size >= filteredQuotes.size) {
            usedQuoteIndices.clear()
            Log.d("QuoteManager", "Reset used quotes for category: $categoryKey")
        }

        val availableIndices = filteredQuotes.indices.filter { it !in usedQuoteIndices }

        if (availableIndices.isEmpty()) {
            Log.w("QuoteManager", "All quotes used for category: $categoryKey. Resetting...")
            usedQuoteIndices.clear()
            return filteredQuotes.random()
        }

        val randomIndex = availableIndices.random()
        val selectedQuote = filteredQuotes[randomIndex]
        usedQuoteIndices.add(randomIndex)

        Log.d("QuoteManager", "Returning quote for category: $categoryKey. Used quote count: ${usedQuoteIndices.size}")
        return selectedQuote
    }
}

// Top level tiles definition
val topLevelTiles = listOf(
    TopLevelTile("Inspiration", "Quotes to inspire your soul"),
    TopLevelTile("Motivation", "Quotes to drive you forward"),
    TopLevelTile("Happiness", "Quotes to bring you joy")
)

// Main Activity class
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        QuoteManager.initialize(this)
        setContent {
            TileApp()
        }
    }
}

// Main composable
@Composable
fun TileApp() {
    MaterialTheme {
        var quoteContent by remember { mutableStateOf("Click a tile to see a quote") }
        var quoteAuthor by remember { mutableStateOf("") }

        val coroutineScope = rememberCoroutineScope()

        val fetchQuote: (String) -> Unit = { keyword ->
            coroutineScope.launch {
                val quote = QuoteManager.getRandomQuote(keyword)
                if (quote != null) {
                    quoteContent = quote.content
                    quoteAuthor = quote.author
                } else {
                    quoteContent = "No quotes found for '$keyword'"
                    quoteAuthor = ""
                }
            }
        }

        AppNavigation(quoteContent, quoteAuthor, fetchQuote)
    }
}

// Navigation setup
@Composable
fun AppNavigation(quoteContent: String, quoteAuthor: String, fetchQuote: (String) -> Unit) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = Screen.TopLevel.route) {
        composable(Screen.TopLevel.route) {
            TopLevelScreen(
                tiles = topLevelTiles,
                onTileClick = { index ->
                    val theme = topLevelTiles[index].title
                    fetchQuote(theme)
                    navController.navigate(Screen.MiddleLevel.createRoute(theme))
                },
                onJournalClick = {
                    navController.navigate(Screen.Journal.route)
                }
            )
        }
        composable(Screen.MiddleLevel.route) { backStackEntry ->
            val theme = backStackEntry.arguments?.getString("theme") ?: ""

            MiddleLevelScreen(
                theme = theme,
                quoteContent = quoteContent,
                quoteAuthor = quoteAuthor,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Journal.route) {
            JournalScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

// Reusable button component
@Composable
fun TileButton(
    text: String,
    onClick: () -> Unit,
    tileColor: Color = MaterialTheme.colorScheme.primary
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(260.dp)
            .height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = tileColor)
    ) {
        Text(text = text, fontSize = 20.sp)
    }
}

// Top level screen with charity text and hyperlink
@Composable
fun TopLevelScreen(
    tiles: List<TopLevelTile>,
    onTileClick: (Int) -> Unit,
    onJournalClick: () -> Unit
) {
    // Get URI handler to manage the hyperlink click
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            "Wisdom Quotes",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val imageRes = try {
                R.drawable.your_resized_elephant_image
            } catch (e: Exception) {
                null
            }

            if (imageRes != null) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = "Top Level Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(285.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Wisdom\nQuotes",
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            tiles.forEachIndexed { index, tile ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TileButton(text = tile.title, onClick = { onTileClick(index) })
                    Text(
                        text = tile.description,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Journal Button
            Button(
                onClick = onJournalClick,
                modifier = Modifier
                    .width(200.dp)
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4169E1)
                )
            ) {
                Text(
                    text = "Journal",
                    fontSize = 20.sp,
                    color = Color(0xFFADD8E6)
                )
            }

            // Add space between button and text
            Spacer(modifier = Modifier.height(8.dp))

            // Charity text with hyperlink
            val annotatedString = buildAnnotatedString {
                append("There are many ways to make a difference in the world. Choose an unselfish act. For example, give to a charity like ")

                // Create the clickable "Feed My Starving Children" text
                pushStringAnnotation(
                    tag = "URL",
                    annotation = "https://www.fmsc.org/about-us/faqs"
                )
                withStyle(style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                )) {
                    append("Feed My Starving Children")
                }
                pop()

                append(".")
            }

            // Use a Box with padding to ensure text has proper margins
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                ClickableText(
                    text = annotatedString,
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(
                            tag = "URL",
                            start = offset,
                            end = offset
                        ).firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Add extra bottom space
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// Middle level screen
@Composable
fun MiddleLevelScreen(
    theme: String,
    quoteContent: String,
    quoteAuthor: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            theme,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "\"$quoteContent\"",
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                if (quoteAuthor.isNotEmpty()) {
                    Text(
                        text = "— $quoteAuthor",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.padding(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Back to Home")
        }
    }
}

// Improved save journal content function with error handling
fun saveJournalContent(context: Context, content: String): Boolean {
    return try {
        val fileName = "journal.txt"
        val file = File(context.filesDir, fileName)

        // Create parent directories if they don't exist
        file.parentFile?.mkdirs()

        FileOutputStream(file).use { output ->
            output.write(content.toByteArray())
        }

        Log.d("JournalScreen", "Journal content saved to ${file.absolutePath}")
        true
    } catch (e: Exception) {
        Log.e("JournalScreen", "Error saving journal content: ${e.message}", e)
        false
    }
}
@Composable
fun JournalScreen(onBack: () -> Unit) {
    var journalContent by remember { mutableStateOf("") }
    var saveStatus by remember { mutableStateOf("") }  // Make sure this line is present
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Try to load existing content when screen opens
    LaunchedEffect(Unit) {
        try {
            val file = File(context.filesDir, "journal.txt")
            if (file.exists()) {
                journalContent = file.readText()
                Log.d("JournalScreen", "Loaded existing journal content")
            }
        } catch (e: Exception) {
            Log.e("JournalScreen", "Error loading journal: ${e.message}", e)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "My Journal",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        // ADD THIS CODE HERE - Step 2: Show save status if not empty
        if (saveStatus.isNotEmpty()) {
            Text(
                text = saveStatus,
                color = if (saveStatus.contains("Error")) Color.Red else Color.Green,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                BasicTextField(
                    value = journalContent,
                    onValueChange = { journalContent = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontStyle = FontStyle.Italic
                    )
                )

                if (journalContent.isEmpty()) {
                    Text(
                        "Start writing your thoughts here...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 24.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }

        // REPLACE THIS BLOCK WITH THE CODE BELOW - Step 3: Update Button's onClick handler
        Button(
            onClick = {
                val success = saveJournalContent(context, journalContent)
                saveStatus = if (success) {
                    "Journal saved successfully!"
                } else {
                    "Error saving journal. Please try again."
                }

                // Clear the status message after 3 seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    saveStatus = ""
                }, 3000)
            },
            modifier = Modifier.padding(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Save Journal")
        }

        // Add a spacer to push the "Back to Home" button up from the bottom
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.padding(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Back to Home")
        }

        // Add another spacer at the bottom to ensure the button isn't too close to the edge
        Spacer(modifier = Modifier.height(16.dp))
    }
}
// Journal screen with fixed positioning and feedback
