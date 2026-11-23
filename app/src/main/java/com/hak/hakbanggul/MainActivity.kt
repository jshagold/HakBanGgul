package com.hak.hakbanggul

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hak.hakbanggul.feature.settings.presentation.SettingsScreen
import com.hak.hakbanggul.ui.theme.HakBanGgulTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HakBanGgulTheme {
                // 첫 화면은 설정 화면이다.
                SettingsScreen()
            }
        }
    }
}
