package com.hak.hakbanggul.feature.marketmood.domain

import javax.inject.Inject

// 다른 기능이 받아 달라고 청하는 곳. 받기가 끝난 뒤(또는 받지 않기로 한 뒤)에 돌아온다.
// 다른 기능에 내놓는 것은 ObserveMoodUseCase, RequestRefreshUseCase, ObserveRefreshStateUseCase와 그 모델뿐이다.
// MarketDataRefresher, RefreshMarketDataUseCase, MarketDataRepository는 다른 기능이 직접 쓰지 않는다.
class RequestRefreshUseCase @Inject constructor(
    private val refresher: MarketDataRefresher,
) {
    suspend operator fun invoke(condition: RefreshCondition): RefreshRequestResult = refresher.request(condition)
}
