package com.example.myapplication6

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.io.IOException

sealed class Screen(val route: String) {
    object TopLevel : Screen("top_level")
    object MiddleLevel : Screen("middle_level/{theme}") {
        fun createRoute(theme: String) = "middle_level/$theme"
    }
    object BottomLevel : Screen("bottom_level/{theme}/{aspect}") {
        fun createRoute(theme: String, aspect: String) = "bottom_level/$theme/$aspect"
    }
}

data class Quote(
    val content: String,
    val author: String,
    val category: List<String>,
    val length: Int
)

object QuoteManager {
    private var quotes: List<Quote> = emptyList()
    private val usedQuotes = mutableMapOf<String, MutableSet<Int>>()

    fun initialize(context: Context) {
        try {
            val jsonString = context.assets.open("quotes.json").bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<Quote>>() {}.type
            quotes = Gson().fromJson(jsonString, listType)
            Log.d("QuoteManager", "Initialized with ${quotes.size} quotes")
        } catch (e: IOException) {
            Log.e("QuoteManager", "Error initializing QuoteManager: ${e.message}")
            quotes = emptyList()
        }
    }

    fun getRandomQuote(category: String? = null): Quote? {
        if (quotes.isEmpty()) {
            Log.w("QuoteManager", "No quotes loaded. Make sure QuoteManager.initialize() is called.")
            return null
        }

        val filteredQuotes = if (category != null) {
            quotes.filter { it.category.any { cat -> cat.equals(category, ignoreCase = true) } }
        } else {
            quotes
        }

        if (filteredQuotes.isEmpty()) {
            Log.w("QuoteManager", "No quotes found for category: $category")
            return null
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
            Log.w("QuoteManager", "All quotes used for category: $categoryKey.  Resetting...")
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

val topLevelTiles = listOf(
    TopLevelTile("Rumi"),
    TopLevelTile("Jung"),
    TopLevelTile("Buddhism"),
    TopLevelTile("Stoicism")
)

val middleLevelTiles = listOf(
    MiddleLevelTile("Love", 0),
    MiddleLevelTile("Life", 0),
    MiddleLevelTile("Self", 1),
    MiddleLevelTile("Shadow", 1),
    MiddleLevelTile("Mindfulness", 2),
    MiddleLevelTile("Compassion", 2),
    MiddleLevelTile("Virtue", 3),
    MiddleLevelTile("Resilience", 3)
)

data class TopLevelTile(val title: String)
data class MiddleLevelTile(val title: String, val topLevelIndex: Int)

class MainActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_CODE_WIFI_PERMISSIONS = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        QuoteManager.initialize(this)
        setContent {
            TileApp()
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.INTERNET
            ),
            REQUEST_CODE_WIFI_PERMISSIONS
        )
    }
}

@Composable
fun TileApp() {
    MaterialTheme {
        var quoteContent by remember { mutableStateOf("Loading quote...") }
        var quoteAuthor by remember { mutableStateOf("") }

        val coroutineScope = rememberCoroutineScope()

        val fetchQuotes: (String) -> Unit = { keyword ->
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

        AppNavigation(quoteContent, quoteAuthor, fetchQuotes)
    }
}

@Composable
fun AppNavigation(quoteContent: String, quoteAuthor: String, fetchQuote: (String) -> Unit) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = Screen.TopLevel.route) {
        composable(Screen.TopLevel.route) {
            TopLevelScreen(
                tiles = topLevelTiles,
                onTileClick = { index ->
                    val theme = topLevelTiles[index].title
                    navController.navigate(Screen.MiddleLevel.createRoute(theme))
                },
                quoteContent = quoteContent,
                quoteAuthor = quoteAuthor
            )
        }
        composable(Screen.MiddleLevel.route) { backStackEntry ->
            val theme = backStackEntry.arguments?.getString("theme") ?: ""

            val topLevelIndex = topLevelTiles.indexOfFirst { it.title == theme }
            val filteredTiles = middleLevelTiles.filter { it.topLevelIndex == topLevelIndex }

            LaunchedEffect(theme) {
                fetchQuote(theme)
            }

            MiddleLevelScreen(
                tiles = filteredTiles,
                onTileClick = { index ->
                    val aspect = filteredTiles[index].title
                    navController.navigate(Screen.BottomLevel.createRoute(theme, aspect))
                },
                onBack = { navController.popBackStack() },
                fetchQuote = fetchQuote
            )
        }
        composable(Screen.BottomLevel.route) { backStackEntry ->
            val theme = backStackEntry.arguments?.getString("theme") ?: ""
            val aspect = backStackEntry.arguments?.getString("aspect") ?: ""

            LaunchedEffect(aspect) {
                fetchQuote(aspect)
            }

            val topLevelIndex = topLevelTiles.indexOfFirst { it.title == theme }
            val filteredTiles = middleLevelTiles.filter { it.topLevelIndex == topLevelIndex }
            val middleLevelIndex = filteredTiles.indexOfFirst { it.title == aspect }

            BottomLevelScreen(
                topLevelIndex = topLevelIndex,
                middleLevelIndex = middleLevelIndex,
                quoteContent = quoteContent,
                quoteAuthor = quoteAuthor,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

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

@Composable
fun QuoteCard(quote: Quote) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = quote.content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (quote.author.isNotEmpty()) {
                Text(
                    text = "- ${quote.author}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun TopLevelScreen(
    tiles: List<TopLevelTile>,
    onTileClick: (Int) -> Unit,
    quoteContent: String,
    quoteAuthor: String
) {
    val quote = Quote(quoteContent, quoteAuthor, emptyList(), 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Inspiration: Home",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.your_resized_elephant_image),
                contentDescription = "Top Level Image",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(200.dp)
            )
        }

        QuoteCard(quote = quote)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            tiles.forEachIndexed { index, tile ->
                TileButton(text = tile.title, onClick = { onTileClick(index) })
            }
        }
    }
}

@Composable
fun MiddleLevelScreen(
    tiles: List<MiddleLevelTile>,
    onTileClick: (Int) -> Unit,
    onBack: () -> Unit,
    fetchQuote: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = onBack) {
            Text("Go Back")
        }
        Spacer(modifier = Modifier.height(16.dp))
        tiles.forEachIndexed { index, tile ->
            TileButton(text = tile.title, onClick = {
                fetchQuote(tile.title)
                onTileClick(index)
            })
        }
        LaunchedEffect(key1 = tiles) {
            if (tiles.isNotEmpty()) {
                fetchQuote(tiles.first().title)
            }
        }
    }
}

@Composable
fun BottomLevelScreen(
    topLevelIndex: Int,
    middleLevelIndex: Int,
    quoteContent: String,
    quoteAuthor: String,
    onBack: () -> Unit
) {
    val quote = Quote(quoteContent, quoteAuthor, emptyList(), 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Bottom Level - Top: ${topLevelIndex + 1}, Middle: ${middleLevelIndex + 1}",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        QuoteCard(quote = quote)

        Button(
            onClick = onBack,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("Back to Middle Level")
        }
    }
}