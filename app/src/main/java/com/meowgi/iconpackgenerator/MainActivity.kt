package com.meowgi.iconpackgenerator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.meowgi.iconpackgenerator.ui.theme.IPGTheme
import com.meowgi.iconpackgenerator.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IPGTheme {
                MainScreen()
            }
        }
    }
}
