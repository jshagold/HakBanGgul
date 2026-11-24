package com.hak.hakbanggul.feature.marketmood.domain

import kotlinx.coroutines.flow.Flow

// 지금 쓰는 규칙을 내준다. 나중에 쓰는 사람이 규칙을 고치게 되면 이 구현만 바꾼다.
interface MoodRulesRepository {
    fun observe(): Flow<MoodRules>
}
