package com.example.trainingplayer.database

import androidx.room.*
import com.example.trainingplayer.model.DayContent

@Dao
interface DayContentDao {
    @Insert
    suspend fun insertDayContent(content: DayContent): Long

    @Insert
    suspend fun insertDayContents(contents: List<DayContent>)

    @Update
    suspend fun updateDayContent(content: DayContent)

    @Query("DELETE FROM day_contents WHERE templateId = :templateId")
    suspend fun deleteAllForTemplate(templateId: Long)

    @Query("SELECT * FROM day_contents WHERE templateId = :templateId AND dayIndex = :dayIndex")
    suspend fun getDayContents(templateId: Long, dayIndex: Int): List<DayContent>

    @Query("SELECT * FROM day_contents WHERE templateId = :templateId")
    suspend fun getAllDayContents(templateId: Long): List<DayContent>

    @Query("SELECT * FROM day_contents WHERE templateId = :templateId AND dayIndex = :dayIndex AND slotId = :slotId")
    suspend fun getDayContent(templateId: Long, dayIndex: Int, slotId: Long): DayContent?
}
