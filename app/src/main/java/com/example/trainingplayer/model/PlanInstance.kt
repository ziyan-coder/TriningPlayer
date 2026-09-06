package com.example.trainingplayer.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Transient

@Entity(tableName = "plan_instances")
data class PlanInstance(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,           // 所属模板ID
    val templateName: String = "",  // 模板名称（冗余）
    val startDate: String = "",     // 起始日期 "2026-09-10"
    val status: String = "RUNNING", // RUNNING, PAUSED, COMPLETED, TERMINATED
    val currentDay: Int = 0,        // 当前执行到第几天
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long = 0,          // 结束时间
    val pausedAt: Long = 0          // 暂停时间
) {
    @Transient
    var dayNames: Map<Int, String> = emptyMap()
    
    @Transient
    var dayContents: Map<Int, Map<Long, String>> = emptyMap()
    // key: dayIndex, value: Map<slotId, audioPath>
    
    @Transient
    var timeSlots: List<TimeSlot> = emptyList()
}
