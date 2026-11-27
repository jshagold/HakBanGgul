package com.hak.hakbanggul.feature.marketmood.domain

// 받기 한 번의 진행을 받는 쪽. 모두 기본 구현이 아무것도 하지 않는다.
interface RefreshProgressListener {

    // 이번 받기에서 받을 ETF가 정해짐(요청을 보내기 전, 한 번). 받을 ETF가 없으면 빈 목록이다.
    fun onEtfsDecided(seriesIds: List<String>) {}

    // Twelve Data 묶음 하나를 보냄(그 요청을 보내기 바로 전). 값은 그 묶음 뒤에 아직 보내지 않은 ETF의 수다.
    fun onBatchSent(unsentCount: Int) {}

    // 요청 하나(FRED 한 번, 묶음 하나)가 끝남. 성공이면 저장을 마친 뒤, 실패면 그때.
    // 키 거절로 보내지 않기로 한 묶음의 ETF도 그때 이것으로 알린다.
    fun onRequestFinished(seriesIds: List<String>) {}

    // 이번 받기에서 받을 전체 시장의 값을 모두 저장함(한 번)
    fun onBroadMarketReceived() {}

    companion object {
        // 아무것도 하지 않는 것
        val NONE: RefreshProgressListener = object : RefreshProgressListener {}
    }
}
