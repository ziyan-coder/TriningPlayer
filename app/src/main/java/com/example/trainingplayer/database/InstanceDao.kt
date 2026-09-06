package com.example.trainingplayer.database

import androidx.room.*
import com.example.trainingplayer.model.PlanInstance

@Dao
interface InstanceDao {
    @Insert
    suspend fun insertInstance(instance: PlanInstance): Long

    @Update
    suspend fun updateInstance(instance: PlanInstance)

    @Query("SELECT * FROM plan_instances WHERE status = 'RUNNING' OR status = 'PAUSED' LIMIT 1")
    suspend fun getCurrentRunningInstance(): PlanInstance?

    @Query("SELECT * FROM plan_instances WHERE status != 'RUNNING' AND status != 'PAUSED' ORDER BY endedAt DESC")
    suspend fun getHistoryInstances(): List<PlanInstance>

    @Query("SELECT * FROM plan_instances WHERE id = :instanceId")
    suspend fun getInstanceById(instanceId: Long): PlanInstance?

    @Query("UPDATE plan_instances SET status = 'TERMINATED', endedAt = :endTime WHERE status = 'RUNNING' OR status = 'PAUSED'")
    suspend fun terminateAllRunning(endTime: Long)
}
