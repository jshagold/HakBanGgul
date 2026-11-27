package com.hak.hakbanggul

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.hak.hakbanggul.core.di.ApplicationScope
import com.hak.hakbanggul.core.ui.AppLaunchIntents
import com.hak.hakbanggul.core.ui.theme.HakBanGgulTheme
import com.hak.hakbanggul.feature.marketmood.presentation.CloseRefreshScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 위젯에서 열려 시장 분위기 화면을 보이라고 알린 횟수. 바뀔 때마다 AppNavHost가 시장 분위기 화면을 보인다.
    private val showMarketMoodSignal = mutableIntStateOf(0)

    @Inject
    lateinit var closeRefreshScheduler: CloseRefreshScheduler

    @Inject
    @field:ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 밝은 화면 하나만 두므로, 기기가 어두운 화면이어도 상태 표시줄의 아이콘을 어두운 색으로 둔다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // 다시 만들어질 때(화면을 돌림 등)는 같은 Intent로 두 번 알리지 않는다.
        if (savedInstanceState == null) notifyIfFromWidget(intent)
        ensureCloseRefreshScheduled()
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

    // 장 마감 뒤 뒤에서 미리 받는 작업을 넣는다(있으면 그대로라 다시 만들어질 때 불려도 된다).
    // 화면이 만들어질 때만 부른다(위젯이 프로세스만 깨운 때는 부르지 않는다). 실패하면 다음에 앱을 열 때 다시 한다.
    private fun ensureCloseRefreshScheduled() {
        appScope.launch {
            try {
                closeRefreshScheduler.ensureScheduled()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 삼킨다.
            }
        }
    }

    private fun notifyIfFromWidget(intent: Intent?) {
        if (AppLaunchIntents.showsMarketMood(intent)) showMarketMoodSignal.intValue++
    }
}
