package com.example.myapplication6

import android.Manifest
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
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Query
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
    val id: Int,
    val advice: String
)

interface QuoteService {
    @GET("advice")
    suspend fun getQuote(): Response<Quote>
}

interface AdviceService {
    @GET("advice/search/{keyword}")
    suspend fun searchAdvice(@Path("keyword") keyword: String): Response<AdviceResponse>
}

data class ErrorResponse(val message: String)
data class TopLevelTile(val title: String)
data class MiddleLevelTile(val title: String, val topLevelIndex: Int)
data class Slip(
    val id: Int,
    val advice: String
)

data class QuoteResponse(
    val slip: Slip?
)

data class AdviceResponse(
    val slips: List<Slip>?,
    val message: Message?
)

data class Message(
    val text: String
)

val topLevelTiles = listOf(
    TopLevelTile("Inspiration"),
    TopLevelTile("Motivation"),
    TopLevelTile("Happiness")
)

val middleLevelTiles = listOf(
    MiddleLevelTile("Love", 0),
    MiddleLevelTile("Life", 0),
    MiddleLevelTile("Success", 1),
    MiddleLevelTile("Goals", 1),
    MiddleLevelTile("Joy", 2),
    MiddleLevelTile("Peace", 2)
)

val retrofit = Retrofit.Builder()
    .baseUrl("https://api.adviceslip.com/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val adviceService: AdviceService = retrofit.create(AdviceService::class.java)

class MainActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_CODE_WIFI_PERMISSIONS = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TileApp()
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_WIFI_STATE
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

        val fetchAdvice: (String) -> Unit = { keyword ->
            val encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString())
            coroutineScope.launch {
                try {
                    val response = adviceService.searchAdvice(encodedKeyword)
                    if (response.isSuccessful) {
                        val adviceResponse = response.body()
                        if (adviceResponse != null) {
                            adviceResponse.message?.let {
                                quoteContent = it.text
                                quoteAuthor = ""
                            }
                            adviceResponse.slips?.forEach { slip ->
                                quoteContent = slip.advice
                                quoteAuthor = "Unknown"
                            }
                        } else {
                            quoteContent = "Unexpected response from API."
                            quoteAuthor = ""
                        }
                    } else {
                        throw Exception("HTTP ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e("TileApp", "Error: ${e.message}")
                    quoteContent = "Error: ${e.message}"
                    quoteAuthor = ""
                }
            }
        }

        AppNavigation(quoteContent, quoteAuthor, fetchAdvice)
    }
}

@Composable
fun AppNavigation(quoteContent: String, quoteAuthor: String, fetchQuote: (String) -> Unit) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = Screen.TopLevel.route) {
        composable(Screen.TopLevel.route) {
            TopLevelScreen(tiles = topLevelTiles, onTileClick = { index ->
                val theme = topLevelTiles[index].title
                navController.navigate(Screen.MiddleLevel.createRoute(theme))
            })
        }
        composable(Screen.MiddleLevel.route) { backStackEntry ->
            val theme = backStackEntry.arguments?.getString("theme")
            if (theme != null) {
                MiddleLevelScreen(
                    tiles = middleLevelTiles.filter { it.topLevelIndex == topLevelTiles.indexOfFirst { tile -> tile.title == theme } },
                    onTileClick = { index ->
                        val aspect = middleLevelTiles[index].title
                        fetchQuote(aspect)
                        navController.navigate(Screen.BottomLevel.createRoute(theme, aspect))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.BottomLevel.route) { backStackEntry ->
            val theme = backStackEntry.arguments?.getString("theme")
            val aspect = backStackEntry.arguments?.getString("aspect")
            if (theme != null && aspect != null) {
                BottomLevelScreen(
                    topLevelIndex = topLevelTiles.indexOfFirst { it.title == theme },
                    middleLevelIndex = middleLevelTiles.indexOfFirst { it.title == aspect },
                    quoteContent = quoteContent,
                    quoteAuthor = quoteAuthor,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun TileButton(
    text: String,
    onClick: () -> Unit,
    fontSize: TextUnit = 20.sp,
    tileColor: Color = MaterialTheme.colorScheme.primary
) {
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

@Composable
fun TopLevelScreen(tiles: List<TopLevelTile>, onTileClick: (Int) -> Unit) {
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
                .weight(1f)
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
    onBack: () -> Unit
) {
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
            TileButton(
                text = tile.title,
                onClick = { onTileClick(index) })
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

        Button(
            onClick = onBack,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("Back to Middle Level")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TileApp()
}

@Preview
@Composable
fun TileButtonPreview() {
    MaterialTheme {
        TileButton(text = "Test Button", onClick = {})
    }
}