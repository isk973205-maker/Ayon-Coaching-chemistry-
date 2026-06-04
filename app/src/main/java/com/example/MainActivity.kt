package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.CoachingRepository
import com.example.ui.AyanCoachingAppContent
import com.example.ui.CoachingViewModel
import com.example.ui.CoachingViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize database and repository with cooperative coroutine scopes
        val database = AppDatabase.getDatabase(this, lifecycleScope)
        val repository = CoachingRepository(database)

        // Instantiate ViewModel factory
        val factory = CoachingViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, factory)[CoachingViewModel::class.java]

        setContent {
            MyApplicationTheme {
                AyanCoachingAppContent(viewModel = viewModel)
            }
        }
    }
}
