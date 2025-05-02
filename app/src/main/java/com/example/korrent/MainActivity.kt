package com.example.korrent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
// import androidx.compose.ui.tooling.preview.Preview // Preview no longer needed
import com.example.korrent.ui.theme.KorrentTheme
import com.example.korrent.ui.screen.search.SearchScreen // Import the main screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KorrentTheme { // Apply the custom theme
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Display the main SearchScreen composable
                    SearchScreen()
                }
            }
        }
    }
}

// Removed placeholder Greeting and GreetingPreview