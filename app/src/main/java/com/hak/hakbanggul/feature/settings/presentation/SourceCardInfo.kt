package com.hak.hakbanggul.feature.settings.presentation

import com.hak.hakbanggul.core.model.DataSource

// 출처마다 설정 화면에만 쓰는 고정 정보. 이름과 설명은 core/ui의 SourceLabels에서 얻는다.
data class SourceCardInfo(
    val issueUrl: String,
) {
    companion object {
        fun of(source: DataSource): SourceCardInfo = when (source) {
            DataSource.TWELVE_DATA -> SourceCardInfo(issueUrl = "https://twelvedata.com/register")
            DataSource.FRED -> SourceCardInfo(issueUrl = "https://fredaccount.stlouisfed.org/apikeys")
        }
    }
}
