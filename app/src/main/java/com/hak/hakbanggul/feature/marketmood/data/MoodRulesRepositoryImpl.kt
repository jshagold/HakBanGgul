package com.hak.hakbanggul.feature.marketmood.data

import com.hak.hakbanggul.feature.marketmood.domain.DefaultMoodRules
import com.hak.hakbanggul.feature.marketmood.domain.MoodRules
import com.hak.hakbanggul.feature.marketmood.domain.MoodRulesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

// 지금은 기본 규칙을 내준다.
class MoodRulesRepositoryImpl @Inject constructor() : MoodRulesRepository {
    override fun observe(): Flow<MoodRules> = flowOf(DefaultMoodRules.rules)
}
