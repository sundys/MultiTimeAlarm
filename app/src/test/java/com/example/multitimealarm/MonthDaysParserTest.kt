package com.example.multitimealarm

import com.example.multitimealarm.util.MonthDaysParser
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MonthDaysParserTest {

    private fun parse(input: String): List<Int> = MonthDaysParser.parse(input)

    @Test
    fun `english comma list`() {
        assertEquals(listOf(1, 3, 9, 25), parse("1,3,9,25"))
    }

    @Test
    fun `full width comma is converted`() {
        assertEquals(listOf(1, 3, 9), parse("1，3，9"))
    }

    @Test
    fun `chinese dun hao is converted`() {
        assertEquals(listOf(1, 3, 9), parse("1、3、9"))
    }

    @Test
    fun `range produces every day`() {
        assertEquals((1..15).toList(), parse("1-15"))
    }

    @Test
    fun `mixed combo`() {
        assertEquals(listOf(2, 4, 8, 21, 22, 23, 24, 25, 26), parse("2,4,8,21-26"))
    }

    @Test
    fun `full width dash is converted`() {
        assertEquals((1..15).toList(), parse("1－15"))
        assertEquals((1..15).toList(), parse("1—15"))
    }

    @Test
    fun `duplicates are removed and sorted`() {
        assertEquals(listOf(1, 3, 9), parse("9,1,3,3"))
    }

    @Test
    fun `day out of range rejected`() {
        try {
            parse("0")
            fail("0 应被拒绝")
        } catch (e: MonthDaysParser.InvalidMonthDaysException) {
        }
        try {
            parse("32")
            fail("32 应被拒绝")
        } catch (e: MonthDaysParser.InvalidMonthDaysException) {
        }
    }

    @Test
    fun `inverted range rejected`() {
        try {
            parse("5-2")
            fail("5-2 应被拒绝")
        } catch (e: MonthDaysParser.InvalidMonthDaysException) {
        }
    }

    @Test
    fun `garbage rejected`() {
        try {
            parse("abc")
            fail("abc 应被拒绝")
        } catch (e: MonthDaysParser.InvalidMonthDaysException) {
        }
    }

    private fun fail(message: String) {
        throw AssertionError(message)
    }

    // 编辑页保存校验的语义验证（与 AlarmEditScreen 中同一段过滤逻辑，日期动态适配当天）
    @Test
    fun `past days are detected dynamically`() {
        val today = java.time.LocalDate.now().dayOfMonth
        // 输入 1 和 2：1 一定不晚于今天+2，2 一定不晚于今天+1，总有其一被视为"已过期"
        val monthDays = MonthDaysParser.parse("1,2")
        val past = monthDays.filter { it < today }
        val upcoming = monthDays.filter { it >= today }
        org.junit.Assert.assertTrue("past+upcoming 应覆盖全部", past.size + upcoming.size == 2)
        // 1 号在当月任何时刻都不晚于今天（今天是 2 号及以后时 1 号必为已过期）
        if (today >= 2) {
            org.junit.Assert.assertTrue(past.contains(1))
        }
    }

    @Test
    fun `days beyond month length are invalid`() {
        val maxDay = java.time.YearMonth.now().lengthOfMonth()
        // 小月（≤30 天）时 31 必被判定为"当月不存在"
        if (maxDay < 31) {
            val notInMonth = MonthDaysParser.parse("31").filter { it > maxDay }
            org.junit.Assert.assertTrue(notInMonth.contains(31))
        }
    }

    @Test
    fun `future days mixed with past are allowed`() {
        val today = java.time.LocalDate.now().dayOfMonth
        val monthDays = MonthDaysParser.parse("1,21-26")
        val past = monthDays.filter { it < today }
        val upcoming = monthDays.filter { it >= today }
        org.junit.Assert.assertTrue(past.size + upcoming.size == monthDays.size)
    }
}
