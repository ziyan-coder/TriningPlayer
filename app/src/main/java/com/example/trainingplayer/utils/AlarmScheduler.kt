package com.example.trainingplayer.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.trainingplayer.service.AlarmReceiver
import java.util.*

object AlarmScheduler {
    
    private const val REQUEST_CODE_BASE = 1000
    
    fun scheduleAlarm(context: Context, timeMillis: Long, slotId: Long, dayIndex: Int, instanceId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("slotId", slotId)
            putExtra("dayIndex", dayIndex)
            putExtra("instanceId", instanceId)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getRequestCode(instanceId, slotId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                timeMillis,
                pendingIntent
            )
        }
    }
    
    fun cancelAlarm(context: Context, slotId: Long, instanceId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getRequestCode(instanceId, slotId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
    
    fun cancelAllAlarmsForInstance(context: Context, instanceId: Long) {
        // 使用一个通用的Intent来取消所有
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 遍历取消所有可能的待定意图（这里简化处理，实际可以用多个requestCode）
        for (i in 0..100) {
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                getRequestCode(instanceId, i.toLong()),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
    
    fun cancelAllAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 取消所有AlarmManager任务（简化处理）
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
    
    private fun getRequestCode(instanceId: Long, slotId: Long): Int {
        return (REQUEST_CODE_BASE + instanceId.toString().hashCode() + slotId.toString().hashCode()).hashCode()
    }
}
