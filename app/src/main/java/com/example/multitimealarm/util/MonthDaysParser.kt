package com.example.multitimealarm.util

/**
 * 每月多日期输入的解析与规范化。
 * 支持格式：`1,3,9,25`、`1-15`、`2,4,8,21-26` 等组合。
 * 自动兼容中文全角符号：`，`（全角逗号）、`、`（顿号）转英文逗号；
 * `－`（全角减号）、`—`（破折号）、`–`（en dash）转英文减号。
 */
object MonthDaysParser {

    /** 输入不合法时抛出，message 为给用户看的提示 */
    class InvalidMonthDaysException(message: String) : Exception(message)

    /** 将全角符号统一转换为英文符号 */
    fun normalize(input: String): String = input
        .replace('，', ',')
        .replace('、', ',')
        .replace('－', '-')
        .replace('—', '-')
        .replace('–', '-')

    /**
     * 解析并校验，返回升序去重后的日期列表。
     * 基础规则：数字需在 1-31 之间；范围写法 a-b 要求 a ≤ b。
     */
    fun parse(inputRaw: String): List<Int> {
        val input = normalize(inputRaw).trim()
        if (input.isEmpty()) throw InvalidMonthDaysException("请输入每月提醒日期")
        val result = sortedSetOf<Int>()
        input.split(',').forEach { part ->
            val p = part.trim()
            if (p.isEmpty()) throw InvalidMonthDaysException("日期格式不正确，请检查是否有多余逗号")
            if (p.contains('-')) {
                val range = p.split('-')
                if (range.size != 2 || range.any { it.isEmpty() }) {
                    throw InvalidMonthDaysException("范围格式不正确：$p")
                }
                val start = range[0].toIntOrNull()
                    ?: throw InvalidMonthDaysException("日期格式不正确：$p")
                val end = range[1].toIntOrNull()
                    ?: throw InvalidMonthDaysException("日期格式不正确：$p")
                if (start !in 1..31 || end !in 1..31) {
                    throw InvalidMonthDaysException("日期需在 1-31 之间：$p")
                }
                if (start > end) {
                    throw InvalidMonthDaysException("范围起点不能大于终点：$p")
                }
                (start..end).forEach { result.add(it) }
            } else {
                val day = p.toIntOrNull()
                    ?: throw InvalidMonthDaysException("日期格式不正确：$p")
                if (day !in 1..31) {
                    throw InvalidMonthDaysException("日期需在 1-31 之间：$p")
                }
                result.add(day)
            }
        }
        return result.sorted()
    }

    /** 供展示使用的规范化字符串（如 "1,3,9,25"） */
    fun toStorageString(days: List<Int>): String = days.sorted().joinToString(",")

    /** 从存储字符串解析日期列表（存储值均合法，解析失败返回空） */
    fun fromStorageString(value: String?): List<Int> =
        value?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 1..31 }
            ?.distinct()?.sorted() ?: emptyList()
}
