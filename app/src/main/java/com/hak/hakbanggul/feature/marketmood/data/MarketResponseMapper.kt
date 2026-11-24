package com.hak.hakbanggul.feature.marketmood.data

import com.hak.hakbanggul.core.model.DailyValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

// 출처의 응답 본문을 일별 값으로 바꾼다. 읽을 수 없으면 null이다. 돌려주는 목록은 날짜 오름차순이다.
class MarketResponseMapper @Inject constructor(
    private val json: Json,
) {

    // 종목이 하나면 본문 맨 위에 meta·values·status가 오고, 여럿이면 종목 이름 아래에 하나씩 담긴다.
    // 요청한 종목이 하나라도 없거나, status가 ok가 아니거나, 값이 하나도 없으면 null이다.
    fun mapTwelveData(body: String, symbols: List<String>): Map<String, List<DailyValue>>? {
        if (symbols.isEmpty()) return null
        val root = parseObject(body) ?: return null
        val single = root.containsKey("status") || root.containsKey("values")
        if (single && symbols.size != 1) return null

        val result = LinkedHashMap<String, List<DailyValue>>()
        for (symbol in symbols) {
            val element = if (single) root else root[symbol] ?: return null
            val dto = decode<TwelveDataTimeSeriesDto>(element) ?: return null
            if (dto.status != "ok") return null
            val values = dto.values.orEmpty().mapNotNull { dailyValue(it.datetime, it.close) }
            if (values.isEmpty()) return null
            result[symbol] = values.sortedBy { it.date }
        }
        return result
    }

    // observations가 없거나 값이 하나도 없으면 null이다.
    fun mapFred(body: String): List<DailyValue>? {
        val root = parseObject(body) ?: return null
        val dto = decode<FredObservationsDto>(root) ?: return null
        val values = dto.observations?.mapNotNull { dailyValue(it.date, it.value) } ?: return null
        if (values.isEmpty()) return null
        return values.sortedBy { it.date }
    }

    // 숫자가 아닌 값(FRED의 ".")이나 읽을 수 없는 날짜의 날은 뺀다.
    private fun dailyValue(date: String?, value: String?): DailyValue? {
        val number = value?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        val day = try {
            LocalDate.parse(date ?: return null)
        } catch (e: DateTimeParseException) {
            return null
        }
        return DailyValue(day, number)
    }

    private fun parseObject(body: String): JsonObject? = try {
        json.parseToJsonElement(body).jsonObject
    } catch (e: IllegalArgumentException) {
        // SerializationException과 jsonObject 변환 실패 모두 IllegalArgumentException이다.
        null
    }

    private inline fun <reified T> decode(element: JsonElement): T? = try {
        json.decodeFromJsonElement<T>(element)
    } catch (e: IllegalArgumentException) {
        null
    }
}
