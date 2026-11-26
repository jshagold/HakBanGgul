package com.hak.hakbanggul

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hak.hakbanggul.feature.marketmood.presentation.MarketMoodScreen
import com.hak.hakbanggul.feature.marketmood.presentation.PricePositionScreen
import com.hak.hakbanggul.feature.marketmood.presentation.RateBurdenScreen
import com.hak.hakbanggul.feature.marketmood.presentation.RegimeScreen
import com.hak.hakbanggul.feature.marketmood.presentation.SectorScreen
import com.hak.hakbanggul.feature.marketmood.presentation.TARGET_ID_ARG
import com.hak.hakbanggul.feature.settings.presentation.SettingsScreen

// 화면 이동. 시작 화면은 시장 분위기이고, 설정 화면과 상세 화면(장세, 주가 위치, 금리 부담), 섹터 화면은 그 위에 쌓는다.
// 각 화면의 뒤로 가기 버튼과 기기의 뒤로 가기로 돌아온다.
// 화면은 다른 기능의 화면을 가져오지 않고 갈 곳을 콜백으로만 받는다.
// showMarketMoodSignal이 바뀌면(위젯에서 열림) 시장 분위기 화면 위에 쌓인 화면을 걷는다. 0은 알림이 없는 것이다.
@Composable
fun AppNavHost(showMarketMoodSignal: Int = 0) {
    val navController = rememberNavController()
    LaunchedEffect(showMarketMoodSignal) {
        // 이미 시장 분위기 화면이면 걷을 것이 없어 아무 일도 없다.
        if (showMarketMoodSignal > 0) navController.returnToMarketMood()
    }
    NavHost(navController = navController, startDestination = Routes.MARKET_MOOD) {
        composable(Routes.MARKET_MOOD) {
            MarketMoodScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onOpenRegime = { navController.navigate(Routes.regime(it)) { launchSingleTop = true } },
                onOpenPricePosition = { navController.navigate(Routes.pricePosition(it)) { launchSingleTop = true } },
                onOpenRateBurden = { navController.navigate(Routes.RATE_BURDEN) { launchSingleTop = true } },
                onOpenSector = { navController.navigate(Routes.sector(it)) { launchSingleTop = true } },
            )
        }
        composable(Routes.SETTINGS) { entry ->
            SettingsScreen(onBack = { navController.popIfTop(entry) })
        }
        composable(Routes.REGIME, arguments = listOf(targetIdArgument())) { entry ->
            RegimeScreen(
                onBack = { navController.popIfTop(entry) },
                onReturnToMain = { navController.returnToMarketMood() },
            )
        }
        composable(Routes.PRICE_POSITION, arguments = listOf(targetIdArgument())) { entry ->
            PricePositionScreen(
                onBack = { navController.popIfTop(entry) },
                onReturnToMain = { navController.returnToMarketMood() },
            )
        }
        composable(Routes.RATE_BURDEN) { entry ->
            RateBurdenScreen(
                onBack = { navController.popIfTop(entry) },
                onReturnToMain = { navController.returnToMarketMood() },
            )
        }
        composable(Routes.SECTOR, arguments = listOf(targetIdArgument())) { entry ->
            SectorScreen(
                onBack = { navController.popIfTop(entry) },
                onReturnToMain = { navController.returnToMarketMood() },
                onOpenRegime = { navController.navigate(Routes.regime(it)) { launchSingleTop = true } },
                onOpenPricePosition = { navController.navigate(Routes.pricePosition(it)) { launchSingleTop = true } },
            )
        }
    }
}

private fun targetIdArgument() = navArgument(TARGET_ID_ARG) { type = NavType.StringType }

// 맨 위가 그 화면일 때만 하나를 걷는다. 버튼을 빠르게 두 번 눌러도 시장 분위기 화면까지 걷히지 않게 한다.
private fun NavHostController.popIfTop(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}

// 시장 분위기 화면 위를 모두 걷는다(위젯에서 열 때, 상세 화면이 첫 화면으로 돌아갈 때).
private fun NavHostController.returnToMarketMood() {
    popBackStack(Routes.MARKET_MOOD, inclusive = false)
}

private object Routes {
    const val MARKET_MOOD = "market_mood"
    const val SETTINGS = "settings"
    const val REGIME = "regime/{$TARGET_ID_ARG}"
    const val PRICE_POSITION = "price_position/{$TARGET_ID_ARG}"
    const val RATE_BURDEN = "rate_burden"
    const val SECTOR = "sector/{$TARGET_ID_ARG}"

    fun regime(targetId: String) = "regime/$targetId"
    fun pricePosition(targetId: String) = "price_position/$targetId"
    fun sector(targetId: String) = "sector/$targetId"
}
