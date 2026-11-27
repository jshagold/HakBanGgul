package com.hak.hakbanggul.feature.marketmood.presentation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hak.hakbanggul.feature.marketmood.di.marketMoodEntryPoint
import com.hak.hakbanggul.feature.marketmood.domain.RefreshCondition
import kotlin.coroutines.cancellation.CancellationException

// 장 마감 뒤 뒤에서 미리 받는 작업. 받을지, 무엇을 받을지, 겹쳐 시작하지 않는 것은 화면의 받기와 같은 받기 관리자가 한다.
// 청이 SKIPPED여도, 받기가 실패해도, 예외가 나도 다음 차례를 잇고 성공으로 끝낸다(실패로 다시 돌리지 않는다).
// 작업이 취소되면 잇지 않는다(WorkManager가 다시 돌린다).
// 시스템이 만들어 생성자로 받을 수 없어 MarketMoodEntryPoint로 받는다.
class CloseRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entry = marketMoodEntryPoint(applicationContext)
        try {
            // 끝나기를 기다린다.
            entry.marketDataRefresher().request(RefreshCondition.WHEN_STALE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 다음 차례를 잇고 성공으로 끝낸다.
        }
        try {
            entry.closeRefreshScheduler().scheduleNext()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 잇지 못하면 다음에 앱을 열 때 다시 넣는다.
        }
        return Result.success()
    }
}
