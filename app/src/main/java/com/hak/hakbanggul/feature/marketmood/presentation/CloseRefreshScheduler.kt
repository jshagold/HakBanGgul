package com.hak.hakbanggul.feature.marketmood.presentation

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hak.hakbanggul.feature.marketmood.domain.MarketCloseTimes
import com.hak.hakbanggul.feature.marketmood.domain.MoodRulesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// 용어: 뒤에서 미리 받기
// CloseRefresh: 장 마감 뒤 날마다 한 번 뒤에서 받는 작업을 넣고 잇는다.
// 정확한 알람이나 주기 작업은 쓰지 않고, 다음 장 마감까지 기다리는 한 번 작업을 날마다 잇는다.
@Singleton
class CloseRefreshScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val rulesRepository: MoodRulesRepository,
    private val clock: Clock,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    // 다음 차례의 작업을 넣는다. 작업이 이미 있으면(기다리는 중이든 도는 중이든) 그대로 둔다.
    suspend fun ensureScheduled() {
        enqueueNext(ExistingWorkPolicy.KEEP)
    }

    // 다음 차례의 작업을 잇는다. 지금 도는 작업이 끝난 뒤 기다리기 시작한다.
    suspend fun scheduleNext() {
        enqueueNext(ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    // 다음 날마다의 장 마감(미국 동부 오후 4시 + 여유)까지 기다린 뒤, 인터넷이 연결되어 있을 때 돈다.
    private suspend fun enqueueNext(policy: ExistingWorkPolicy) {
        val rules = rulesRepository.observe().first()
        val now = clock.instant()
        val delay = Duration.between(now, MarketCloseTimes.nextDailyClose(now, rules.closeGraceMinutes))
        val request = OneTimeWorkRequestBuilder<CloseRefreshWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(connected)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    private companion object {
        // 위젯의 작업 이름과 겹치지 않게 기능의 이름을 앞에 붙인다.
        const val WORK_NAME = "hakbanggul.marketmood.close_refresh"
    }
}
