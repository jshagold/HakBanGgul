package com.hak.hakbanggul.core.network

import com.hak.hakbanggul.core.model.DataSource

// 출처마다의 접속 주소. 기능 코드에는 주소를 적지 않는다.
class DataSourceEndpoints {
    fun baseUrl(source: DataSource): String = when (source) {
        DataSource.TWELVE_DATA -> "https://api.twelvedata.com/"
        DataSource.FRED -> "https://api.stlouisfed.org/"
    }
}
