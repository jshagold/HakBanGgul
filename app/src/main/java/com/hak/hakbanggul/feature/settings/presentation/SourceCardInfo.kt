package com.hak.hakbanggul.feature.settings.presentation

import androidx.annotation.StringRes
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.model.DataSource

// 출처마다 화면에 쓰는 고정 정보
data class SourceCardInfo(
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    val issueUrl: String,
) {
    companion object {
        fun of(source: DataSource): SourceCardInfo = when (source) {
            DataSource.TWELVE_DATA -> SourceCardInfo(
                nameRes = R.string.source_twelve_data_name,
                descriptionRes = R.string.source_twelve_data_description,
                issueUrl = "https://twelvedata.com/register",
            )
            DataSource.FRED -> SourceCardInfo(
                nameRes = R.string.source_fred_name,
                descriptionRes = R.string.source_fred_description,
                issueUrl = "https://fredaccount.stlouisfed.org/apikeys",
            )
        }
    }
}
