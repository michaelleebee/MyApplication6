package com.example.myapplication6

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication6.R
//import io.ktor.client.*

//import io.ktor.client.request.*
//import io.ktor.client.statement.*
import kotlinx.coroutines.launch
import retrofit2.Retrofit

import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Data classes
data class TopLevelTile(val title: String)
data class MiddleLevelTile(val title: String, val topLevelIndex: Int)
data class BottomLevelTile(val title: String, val content: String)

// Quote API data classes
data class QuoteResponse(
    val content: String,
    val author: String
)

// Retrofit service for Quotable API
interface QuotableService {
    @GET("random")
    suspend fun getRandomQuote(@Query("tags") tags: String): QuoteResponse
}

// Data setup
val topLevelTiles = listOf(
    TopLevelTile("Peace"),
    TopLevelTile("Love"),
    TopLevelTile("Happiness")
)




val middleLevelTiles = listOf(
    // Peace Middle Level
    MiddleLevelTile("Inspiration Love", 0),
    MiddleLevelTile("Inspiration Peace", 0),
    MiddleLevelTile("Inspiration Happiness", 0),
    // Love Middle LevelPlease
    MiddleLevelTile("Inspiration Now", 1),
    MiddleLevelTile("Inspiration Yesterday", 1),
    MiddleLevelTile("Inspiration Tomorrow", 1),
    // Happiness Middle Level
    MiddleLevelTile("Inspiration First", 2),
    MiddleLevelTile("Inspiration Next", 2),
    MiddleLevelTile("Inspiration Last", 2)
)

// Retrofit instance
val retrofit = Retrofit.Builder()
    .baseUrl("https://api.quotable.io/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val quotableService = retrofit.create(QuotableService::class.java)

@Composable
fun TileApp() {
    var currentLevel by remember { mutableStateOf(1) }
    var selectedTopLevelIndex by remember { mutableStateOf(-1) }
    var selectedMiddleLevelIndex by remember { mutableStateOf(-1) }
    var quoteContent by remember { mutableStateOf("Loading quote...") }
    var quoteAuthor by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Function to fetch a quote based on keyword
    fun fetchQuote(keyword: String) {
        coroutineScope.launch {
            try {
                val response = quotableService.getRandomQuote(keyword.lowercase())
                quoteContent = response.content
                quoteAuthor = response.author
            } catch (e: Exception) {
                Log.e("TileApp", "Error fetching quote: ${e.message}")
                quoteContent = "Could not load a quote about $keyword. Please try again."
                quoteAuthor = ""
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (currentLevel) {
            1 -> TopLevelScreen(topLevelTiles) { index ->
                selectedTopLevelIndex = index
                currentLevel = 2
            }
            2 -> MiddleLevelScreen(
                middleLevelTiles.filter { it.topLevelIndex == selectedTopLevelIndex },
                onTileClick = { index ->
                    selectedMiddleLevelIndex = index
                    // Get the keyword for the API call from the selected tile
                    val keyword = when {
                        selectedTopLevelIndex == 0 && index == 0 -> "love" // Peace -> Inspiration Love
                        selectedTopLevelIndex == 1 && index == 3 -> "present" // Love -> Inspiration Now
                        selectedTopLevelIndex == 2 && index == 6 -> "happiness" // Happiness -> Inspiration First
                        else -> topLevelTiles[selectedTopLevelIndex].title // Default to top level keyword
                    }
                    fetchQuote(keyword)
                    currentLevel = 3
                },
                onBack = { currentLevel = 1 }
            )
            3 -> BottomLevelScreen(
                topLevelIndex = selectedTopLevelIndex,
                middleLevelIndex = selectedMiddleLevelIndex,
                quoteContent = quoteContent,
                quoteAuthor = quoteAuthor,
                onBack = { currentLevel = 2 }
            )
        }
    }
}

@Composable
fun TopLevelScreen(tiles: List<TopLevelTile>, onTileClick: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Inspiration: Home", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))

        // Image Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.your_resized_elephant_image),
                contentDescription = "Top Level Image",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Text Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Do not dwell in the past, do not dream of the future,",
                fontSize = 32.sp
            )
            Text(
                text = "concentrate the mind on the present moment.",
                fontSize = 32.sp
            )
        }

        // Button Section
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            tiles.forEachIndexed { index, tile ->
                TileButton(text = tile.title, onClick = { onTileClick(index) })
            }
        }
    }
}

@Composable
fun MiddleLevelScreen(tiles: List<MiddleLevelTile>, onTileClick: (Int) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Middle Level - Top Level Index: ${tiles.first().topLevelIndex + 1}",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        tiles.forEachIndexed { index, tile ->
            TileButton(text = tile.title, onClick = { onTileClick(tile.topLevelIndex * 3 + index) })
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(onClick = onBack) {
            Text("Back to Inspiration: Home")
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
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Bottom Level - Top: ${topLevelIndex + 1}, Middle: ${middleLevelIndex + 1}",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Quote Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = quoteContent,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (quoteAuthor.isNotEmpty()) {
                    Text(
                        text = "— $quoteAuthor",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }

        // Back button
        Button(
            onClick = onBack,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("Back to Middle Level")
        }
    }
}

@Composable
fun TileButton(text: String, onClick: () -> Unit, fontSize: TextUnit = 20.sp, tileColor: Color = MaterialTheme.colorScheme.primary) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(260.dp)
            .height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = tileColor)
    ) {
        Text(text = text, fontSize = fontSize)
    }
}

@Preview(showBackground = true)
@Composable
fun TileButtonPreview() {
    MaterialTheme {
        TileButton(text = "Test Button", onClick = {})
    }
}

// MainActivity class
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TileApp()
            }
        }
    }
}