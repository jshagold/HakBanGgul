package com.hak.hakbanggul

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.hak.hakbanggul.core.ui.AppLaunchIntents
import com.hak.hakbanggul.core.ui.theme.HakBanGgulTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 위젯에서 열려 시장 분위기 화면을 보이라고 알린 횟수. 바뀔 때마다 AppNavHost가 시장 분위기 화면을 보인다.
    private val showMarketMoodSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 밝은 화면 하나만 두므로, 기기가 어두운 화면이어도 상태 표시줄의 아이콘을 어두운 색으로 둔다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // 다시 만들어질 때(화면을 돌림 등)는 같은 Intent로 두 번 알리지 않는다.
        if (savedInstanceState == null) notifyIfFromWidget(intent)
        setContent {
            HakBanGgulTheme {
                AppNavHost(showMarketMoodSignal = showMarketMoodSignal.intValue)
            }
        }
    }

    // 이미 열려 있는데 위젯에서 다시 불렸다.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notifyIfFromWidget(intent)
    }

    private fun notifyIfFromWidget(intent: Intent?) {
        if (AppLaunchIntents.showsMarketMood(intent)) showMarketMoodSignal.intValue++
    }
}
