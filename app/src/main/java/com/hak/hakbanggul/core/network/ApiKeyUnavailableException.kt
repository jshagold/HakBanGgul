package com.hak.hakbanggul.core.network

import com.hak.hakbanggul.core.model.DataSource
import java.io.IOException

// 키가 없거나 틀림이라 요청을 보내지 않았음을 알린다. availability는 MISSING 또는 INVALID다.
class ApiKeyUnavailableException(
    val source: DataSource,
    val availability: ApiKeyAvailability,
) : IOException("$source 키를 쓸 수 없음: $availability")
