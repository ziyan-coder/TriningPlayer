package com.example.trainingplayer.database

import androidx.room.*
import com.example.trainingplayer.model.PlanTemplate

@Dao
interface TemplateDao {
    @Insert
    suspend fun insertTemplate(template: PlanTemplate): Long

    @Update
    suspend fun updateTemplate(template: PlanTemplate)

    @Delete
    suspend fun deleteTemplate(template: PlanTemplate)

    @Query("SELECT * FROM plan_templates ORDER BY updatedAt DESC")
    suspend fun getAllTemplates(): List<PlanTemplate>

    @Query("SELECT * FROM plan_templates WHERE id = :templateId")
    suspend fun getTemplateById(templateId: Long): PlanTemplate?
}
