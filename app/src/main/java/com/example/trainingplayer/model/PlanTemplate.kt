package com.example.trainingplayer.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Transient

@Entity(tableName = "plan_templates")
data class PlanTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",          // 模板名称
    val totalDays: Int = 0,         // 总天数
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    @Transient
    var timeSlots: List<TimeSlot> = emptyList()
    
    @Transient
    var dayNames: Map<Int, String> = emptyMap()
    
    @Transient
    var dayContents: Map<Int, Map<Long, DayContent>> = emptyMap()
    // key: dayIndex (1~N), value: Map<slotId, DayContent>
}
