package com.example.trainingplayer.database

import androidx.room.*
import com.example.trainingplayer.model.TimeSlot

@Dao
interface TimeSlotDao {
    @Insert
    suspend fun insertTimeSlot(slot: TimeSlot): Long

    @Insert
    suspend fun insertTimeSlots(slots: List<TimeSlot>)

    @Update
    suspend fun updateTimeSlot(slot: TimeSlot)

    @Delete
    suspend fun deleteTimeSlot(slot: TimeSlot)

    @Query("DELETE FROM time_slots WHERE templateId = :templateId")
    suspend fun deleteAllForTemplate(templateId: Long)

    @Query("SELECT * FROM time_slots WHERE templateId = :templateId ORDER BY sortOrder ASC")
    suspend fun getTimeSlotsForTemplate(templateId: Long): List<TimeSlot>

    @Query("SELECT * FROM time_slots WHERE id = :slotId")
    suspend fun getTimeSlotById(slotId: Long): TimeSlot?
}
