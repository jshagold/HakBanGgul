package com.hak.hakbanggul.core.ui

import androidx.annotation.StringRes
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.model.DataSource

// 출처마다 화면에 보이는 문구
object SourceLabels {

    // 출처 이름
    @StringRes
    fun nameRes(source: DataSource): Int = when (source) {
        DataSource.TWELVE_DATA -> R.string.source_twelve_data_name
        DataSource.FRED -> R.string.source_fred_name
    }

    // 무엇에 쓰는 키인지
    @StringRes
    fun descriptionRes(source: DataSource): Int = when (source) {
        DataSource.TWELVE_DATA -> R.string.source_twelve_data_description
        DataSource.FRED -> R.string.source_fred_description
    }
}
