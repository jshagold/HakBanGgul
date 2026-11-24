package com.hak.hakbanggul.feature.marketmood.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class MoodTextFormatterTest {

    private val formatter = MoodTextFormatter()

    // R-13 부호, 자릿수, 색의 종류

    @Test
    fun `R-13 항목 점수는 소수 한 자리와 부호, 색의 종류`() {
        assertEquals("+1.0", formatter.itemScore(1.0))
        assertEquals(ScoreTone.POSITIVE, formatter.scoreTone(1.0, 1))
        assertEquals("0.0", formatter.itemScore(0.0))
        assertEquals(ScoreTone.ZERO, formatter.scoreTone(0.0, 1))
        assertEquals("-0.5", formatter.itemScore(-0.5))
        assertEquals(ScoreTone.NEGATIVE, formatter.scoreTone(-0.5, 1))
    }

    @Test
    fun `R-13 묶음 점수는 소수 두 자리`() {
        assertEquals("+0.38", formatter.groupScore(0.375))
        assertEquals("+0.41", formatter.groupScore(0.4125))
        assertEquals("-0.85", formatter.groupScore(-0.85))
    }

    @Test
    fun `R-13 반올림해 0이 되면 부호 없이 ZERO`() {
        assertEquals("0.00", formatter.groupScore(-0.004))
        assertEquals(ScoreTone.ZERO, formatter.scoreTone(-0.004, 2))
        assertEquals("0.00", formatter.groupScore(0.004))
        assertEquals(ScoreTone.ZERO, formatter.scoreTone(0.004, 2))
    }

    @Test
    fun `R-13 0_005는 사사오입으로 0_01`() {
        assertEquals("+0.01", formatter.groupScore(0.005))
        assertEquals(ScoreTone.POSITIVE, formatter.scoreTone(0.005, 2))
        assertEquals("-0.01", formatter.groupScore(-0.005))
        assertEquals(ScoreTone.NEGATIVE, formatter.scoreTone(-0.005, 2))
    }

    // R-11 글

    @Test
    fun `R-11 signedPercent`() {
        assertEquals("+4.1%", formatter.signedPercent(0.041))
        assertEquals("-3.2%", formatter.signedPercent(-0.032))
        assertEquals("0.0%", formatter.signedPercent(0.0))
        assertEquals("0.0%", formatter.signedPercent(-0.0004))
    }

    @Test
    fun `R-11 signedPercentPoint`() {
        assertEquals("+0.29%p", formatter.signedPercentPoint(0.29))
        assertEquals("-0.10%p", formatter.signedPercentPoint(-0.1))
        assertEquals("0.00%p", formatter.signedPercentPoint(0.0))
    }

    @Test
    fun `R-11 percent`() {
        assertEquals("3.76%", formatter.percent(3.76))
        assertEquals("3.40%", formatter.percent(3.4))
    }
}
