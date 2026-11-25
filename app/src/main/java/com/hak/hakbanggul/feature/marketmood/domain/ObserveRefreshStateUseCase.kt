package com.hak.hakbanggul.feature.marketmood.domain

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// 다른 기능이 받는 중인지와 받기가 끝난 것을 아는 곳. 모으기 시작하면 지금 상태가 먼저 나온다.
// isRefreshing이 참에서 거짓으로 바뀌면 받기가 끝난 것이다. 어디서 시작한 받기든, 성공이든 실패든 같다.
// 다른 기능에 내놓는 것은 ObserveMoodUseCase, RequestRefreshUseCase, ObserveRefreshStateUseCase와 그 모델뿐이다.
// MarketDataRefresher, RefreshMarketDataUseCase, MarketDataRepository는 다른 기능이 직접 쓰지 않는다.
class ObserveRefreshStateUseCase @Inject constructor(
    private val refresher: MarketDataRefresher,
) {
    operator fun invoke(): Flow<RefreshState> = refresher.state
}
