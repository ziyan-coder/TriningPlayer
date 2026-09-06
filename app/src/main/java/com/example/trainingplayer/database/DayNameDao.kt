package com.example.trainingplayer.database

import androidx.room.*
import com.example.trainingplayer.model.DayName

@Dao
interface DayNameDao {
    @Insert
    suspend fun insertDayName(dayName: DayName): Long

    @Insert
    suspend fun insertDayNames(dayNames: List<DayName>)

    @Update
    suspend fun updateDayName(dayName: DayName)

    @Query("DELETE FROM day_names WHERE templateId = :templateId")
    suspend fun deleteAllForTemplate(templateId: Long)

    @Query("SELECT * FROM day_names WHERE templateId = :templateId ORDER BY dayIndex ASC")
    suspend fun getDayNamesForTemplate(templateId: Long): List<DayName>

    @Query("SELECT * FROM day_names WHERE templateId = :templateId AND dayIndex = :dayIndex")
    suspend fun getDayName(templateId: Long, dayIndex: Int): DayName?
}
