
package com.shiftalarm.app

import java.time.LocalTime

data class ShiftDefinition(
    val code: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val endDayOffset: Int = 0
)

object ShiftDefinitions {

    val all = listOf(
        ShiftDefinition(
            code = "SF",
            startTime = LocalTime.of(12, 0),
            endTime = LocalTime.of(14, 30)
        ),
        ShiftDefinition(
            code = "SO",
            startTime = LocalTime.of(12, 0),
            endTime = LocalTime.of(14, 30)
        ),
        ShiftDefinition(
            code = "PF",
            startTime = LocalTime.of(6, 0),
            endTime = LocalTime.of(14, 30)
        ),
        ShiftDefinition(
            code = "PO",
            startTime = LocalTime.of(6, 0),
            endTime = LocalTime.of(14, 30)
        ),
        ShiftDefinition(
            code = "MF",
            startTime = LocalTime.of(17, 0),
            endTime = LocalTime.of(8, 30),
            endDayOffset = 1
        ),
        ShiftDefinition(
            code = "MO",
            startTime = LocalTime.of(17, 0),
            endTime = LocalTime.of(8, 30),
            endDayOffset = 1
        ),
        ShiftDefinition(
            code = "R",
            startTime = LocalTime.of(6, 30),
            endTime = LocalTime.of(16, 0)
        )
    )

    fun find(code: String): ShiftDefinition? {
        return all.find {
            it.code.equals(code.trim(), ignoreCase = true)
        }
    }
}
