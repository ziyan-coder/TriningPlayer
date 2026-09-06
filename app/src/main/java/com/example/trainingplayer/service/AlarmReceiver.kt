package com.example.trainingplayer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.trainingplayer.database.AppDatabase
import com.example.trainingplayer.database.DayContentDao
import com.example.trainingplayer.database.InstanceDao
import com.example.trainingplayer.database.TimeSlotDao
import com.example.trainingplayer.model.PlanInstance
import kotlinx.coroutines.*

class AlarmReceiver : BroadcastReceiver() {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    override fun onReceive(context: Context, intent: Intent) {
        val slotId = intent.getLongExtra("slotId", -1)
        val dayIndex = intent.getIntExtra("dayIndex", -1)
        val instanceId = intent.getLongExtra("instanceId", -1)
        
        if (slotId == -1L || dayIndex == -1 || instanceId == -1L) {
            Log.e("AlarmReceiver", "Invalid alarm data")
            return
        }
        
        Log.d("AlarmReceiver", "Alarm triggered: slotId=$slotId, dayIndex=$dayIndex, instanceId=$instanceId")
        
        scope.launch {
            try {
                val db = AppDatabase.getInstance(context)
                val instanceDao = db.instanceDao()
                val timeSlotDao = db.timeSlotDao()
                val dayContentDao = db.dayContentDao()
                
                // 获取当前实例
                val instance = instanceDao.getInstanceById(instanceId)
                if (instance == null) {
                    Log.e("AlarmReceiver", "Instance not found")
                    return@launch
                }
                
                // 检查实例状态
                if (instance.status != "RUNNING") {
                    Log.d("AlarmReceiver", "Instance is not running, skipping playback")
                    return@launch
                }
                
                // 检查当前日期是否匹配
                val today = com.example.trainingplayer.utils.TimeHelper.getTodayString()
                val startDate = instance.startDate
                val currentDayIndex = com.example.trainingplayer.utils.TimeHelper.getDayIndex(startDate, today)
                
                if (currentDayIndex != dayIndex) {
                    Log.d("AlarmReceiver", "Day mismatch: expected $dayIndex, got $currentDayIndex")
                    return@launch
                }
                
                // 获取时段信息
                val slot = timeSlotDao.getTimeSlotById(slotId)
                if (slot == null) {
                    Log.e("AlarmReceiver", "Slot not found")
                    return@launch
                }
                
                // 获取该天该时段的音频路径
                val dayContent = dayContentDao.getDayContent(instance.templateId, dayIndex, slotId)
                val audioPath = dayContent?.audioPath ?: ""
                
                if (audioPath.isEmpty()) {
                    Log.e("AlarmReceiver", "No audio file set for this slot")
                    return@launch
                }
                
                // 启动播放服务
                val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                    putExtra("action", "PLAY")
                    putExtra("audioPath", audioPath)
                    putExtra("audioName", dayContent.audioName)
                    putExtra("slotId", slotId)
                    putExtra("dayIndex", dayIndex)
                    putExtra("instanceId", instanceId)
                    putExtra("slotTime", slot.time)
                }
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error processing alarm", e)
            }
        }
    }
}
