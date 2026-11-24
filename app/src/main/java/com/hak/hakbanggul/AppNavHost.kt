package com.hak.hakbanggul

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hak.hakbanggul.feature.marketmood.presentation.MarketMoodScreen
import com.hak.hakbanggul.feature.settings.presentation.SettingsScreen

// 화면 이동. 시작 화면은 시장 분위기이고, 설정 화면은 그 위에 쌓는다. 기기의 뒤로 가기로 돌아온다.
// 화면은 다른 기능의 화면을 가져오지 않고 갈 곳을 콜백으로만 받는다.
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.MARKET_MOOD) {
        composable(Routes.MARKET_MOOD) {
            MarketMoodScreen(
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen()
        }
    }
}

private object Routes {
    const val MARKET_MOOD = "market_mood"
    const val SETTINGS = "settings"
}
