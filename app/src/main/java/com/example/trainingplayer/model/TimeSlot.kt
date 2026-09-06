package com.example.trainingplayer.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_slots")
data class TimeSlot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,          // 所属模板ID
    val time: String,              // "08:00"
    val label: String = "",        // 时段标签（如"早晨"）
    val sortOrder: Int = 0         // 排序序号
)
