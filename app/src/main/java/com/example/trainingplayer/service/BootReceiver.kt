package com.example.trainingplayer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.trainingplayer.database.AppDatabase
import com.example.trainingplayer.database.DayContentDao
import com.example.trainingplayer.database.InstanceDao
import com.example.trainingplayer.database.TimeSlotDao
import com.example.trainingplayer.model.TimeSlot
import com.example.trainingplayer.utils.AlarmScheduler
import com.example.trainingplayer.utils.TimeHelper
import kotlinx.coroutines.*

class BootReceiver : BroadcastReceiver() {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device rebooted, rescheduling alarms")
            
            scope.launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val instanceDao = db.instanceDao()
                    val timeSlotDao = db.timeSlotDao()
                    val dayContentDao = db.dayContentDao()
                    
                    // 获取当前运行中的实例
                    val runningInstance = instanceDao.getCurrentRunningInstance()
                    if (runningInstance != null && runningInstance.status == "RUNNING") {
                        // 重新计算并设置闹钟
                        val today = TimeHelper.getTodayString()
                        val startDate = runningInstance.startDate
                        val dayIndex = TimeHelper.getDayIndex(startDate, today)
                        
                        if (dayIndex in 1..runningInstance.currentDay) {
                            // 获取该天的所有时段
                            val slots = timeSlotDao.getTimeSlotsForTemplate(runningInstance.templateId)
                            
                            // 获取剩余时段
                            val remainingSlots = slots
                                .map { it.time }
                                .filter { TimeHelper.isTimeInFutureToday(it) }
                            
                            for (slotTime in remainingSlots) {
                                val slot = slots.find { it.time == slotTime }
                                slot?.let {
                                    val alarmTime = TimeHelper.getTodayAtTime(slotTime)
                                    if (alarmTime > System.currentTimeMillis()) {
                                        AlarmScheduler.scheduleAlarm(
                                            context,
                                            alarmTime,
                                            it.id,
                                            dayIndex,
                                            runningInstance.id
                                        )
                                        Log.d("BootReceiver", "Rescheduled alarm for ${it.time}")
                                    }
                                }
                            }
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error rescheduling alarms", e)
                }
            }
        }
    }
}
