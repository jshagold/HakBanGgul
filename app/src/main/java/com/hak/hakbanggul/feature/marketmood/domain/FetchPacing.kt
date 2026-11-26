package com.hak.hakbanggul.feature.marketmood.domain

import kotlin.time.Duration

// Twelve Data에 나눠 보내는 법. 판정의 규칙이 아니라 출처의 요금제(분당 한도)에 매인 숫자라 MoodRules에 두지 않는다.
data class FetchPacing(
    // 용어: 나눠 받는 묶음
    // 묶음(batch) 하나에 넣는 종가 시리즈의 수
    val batchSize: Int,
    // 앞 묶음을 보낸 때로부터 다음 묶음을 보내기까지의 간격
    val batchInterval: Duration,
    // 요청 하나(FRED 한 번, 묶음 하나)에 거는 제한. 기다리는 시간은 들지 않는다.
    val requestTimeout: Duration,
)
