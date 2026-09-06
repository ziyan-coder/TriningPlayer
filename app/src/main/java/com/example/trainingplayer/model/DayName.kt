package com.example.trainingplayer.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "day_names")
data class DayName(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,          // 所属模板ID
    val dayIndex: Int,             // 第几天 (1~N)
    val name: String = ""          // 日期名称（如"报到日"）
)
