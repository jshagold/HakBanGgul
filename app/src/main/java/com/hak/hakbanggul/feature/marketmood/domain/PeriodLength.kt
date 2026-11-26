package com.hak.hakbanggul.feature.marketmood.domain

// 기간의 단위
enum class PeriodUnit { MONTHS, YEARS }

// 용어: 기간의 이름
// "3개월", "2년"처럼 문구에 끼우는 데만 쓴다. 계산에는 쓰지 않는다(계산은 일수로 한다).
data class PeriodLength(val amount: Int, val unit: PeriodUnit)
