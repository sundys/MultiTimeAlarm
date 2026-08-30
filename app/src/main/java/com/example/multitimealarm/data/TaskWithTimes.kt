package com.example.multitimealarm.data

import androidx.room.Embedded
import androidx.room.Relation

data class TaskWithTimes(
    @Embedded val task: AlarmTaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val times: List<AlarmTimeEntity>,
)
