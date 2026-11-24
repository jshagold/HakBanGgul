package com.hak.hakbanggul.feature.marketmood.data

import com.hak.hakbanggul.core.model.DailyValue
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MarketResponseMapperTest {

    private val mapper = MarketResponseMapper(Json { ignoreUnknownKeys = true })

    private fun value(date: String, value: Double) = DailyValue(LocalDate.parse(date), value)

    private val twoSymbols = """{"SPY":{"meta":{"symbol":"SPY"},"values":[{"datetime":"2026-11-23","close":"722.28998"}],"status":"ok"},"QQQ":{"meta":{"symbol":"QQQ"},"values":[{"datetime":"2026-11-23","close":"610.1"}],"status":"ok"}}"""

    // R-39

    @Test
    fun `R-39 FRED의 숫자가 아닌 값의 날은 뺀다`() {
        val body = """{"observations":[{"date":"2026-11-19","value":"0.31"},{"date":"2026-11-20","value":"."},{"date":"2026-11-23","value":"0.29"}]}"""
        assertEquals(listOf(value("2026-11-19", 0.31), value("2026-11-23", 0.29)), mapper.mapFred(body))
    }

    // R-42 읽을 수 없는 응답

    @Test
    fun `R-42 종목 하나의 응답을 날짜 오름차순으로 읽는다`() {
        val body = """{"meta":{"symbol":"SPY","interval":"1day"},"values":[{"datetime":"2026-11-23","open":"720.10","high":"723.00","low":"719.50","close":"722.28998","volume":"1000"},{"datetime":"2026-11-20","open":"715.00","high":"719.00","low":"714.00","close":"718.5","volume":"900"}],"status":"ok"}"""
        assertEquals(
            mapOf("SPY" to listOf(value("2026-11-20", 718.5), value("2026-11-23", 722.28998))),
            mapper.mapTwelveData(body, listOf("SPY")),
        )
    }

    @Test
    fun `R-42 종목 여럿의 응답을 읽는다`() {
        assertEquals(
            mapOf(
                "SPY" to listOf(value("2026-11-23", 722.28998)),
                "QQQ" to listOf(value("2026-11-23", 610.1)),
            ),
            mapper.mapTwelveData(twoSymbols, listOf("SPY", "QQQ")),
        )
    }

    @Test
    fun `R-42 한 종목이 오류면 null`() {
        val body = """{"SPY":{"meta":{"symbol":"SPY"},"values":[{"datetime":"2026-11-23","close":"722.28998"}],"status":"ok"},"QQQ":{"code":400,"message":"symbol not found","status":"error"}}"""
        assertNull(mapper.mapTwelveData(body, listOf("SPY", "QQQ")))
    }

    @Test
    fun `R-42 요청한 종목이 없으면 null`() {
        assertNull(mapper.mapTwelveData(twoSymbols, listOf("SPY", "QQQ", "XLK")))
    }

    @Test
    fun `R-42 JSON이 아닌 본문은 둘 다 null`() {
        val body = "<html>Service Unavailable</html>"
        assertNull(mapper.mapTwelveData(body, listOf("SPY")))
        assertNull(mapper.mapFred(body))
    }

    @Test
    fun `R-42 FRED 오류 본문은 null`() {
        assertNull(mapper.mapFred("""{"error_code":400,"error_message":"Bad Request.  The series does not exist."}"""))
    }
}
