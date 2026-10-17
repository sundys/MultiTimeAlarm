package com.example.multitimealarm

import com.example.multitimealarm.data.Converters
import com.example.multitimealarm.data.TaskType
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `known type round trips`() {
        TaskType.entries.forEach { type ->
            assertEquals(type, converters.stringToTaskType(converters.taskTypeToString(type)))
        }
    }

    @Test
    fun `unknown type falls back to daily instead of throwing`() {
        // 旧版本读到新版本写入的类型时，不能让整个列表点击即崩
        assertEquals(TaskType.DAILY, converters.stringToTaskType("SOME_FUTURE_TYPE"))
        assertEquals(TaskType.DAILY, converters.stringToTaskType(""))
    }
}
