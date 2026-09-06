package com.example.trainingplayer.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "day_contents")
data class DayContent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,          // 所属模板ID
    val dayIndex: Int,             // 第几天 (1~N)
    val slotId: Long,              // 对应时段ID
    val audioPath: String = "",    // 音频文件路径
    val audioName: String = "",    // 音频文件名
    val isManualModified: Boolean = false  // 是否手动修改过
)
