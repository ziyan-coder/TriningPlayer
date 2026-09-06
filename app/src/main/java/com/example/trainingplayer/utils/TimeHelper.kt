package com.example.trainingplayer.utils

import java.text.SimpleDateFormat
import java.util.*

object TimeHelper {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())

    fun getTodayString(): String {
        return dateFormat.format(Date())
    }

    fun getCurrentTimeString(): String {
        return timeFormat.format(Date())
    }

    fun getCurrentHour(): Int {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }

    fun getCurrentMinute(): Int {
        return Calendar.getInstance().get(Calendar.MINUTE)
    }

    fun getCurrentTimeInMillis(): Long {
        return System.currentTimeMillis()
    }

    fun parseDate(dateString: String): Date? {
        return try {
            dateFormat.parse(dateString)
        } catch (e: Exception) {
            null
        }
    }

    fun formatDateForDisplay(dateString: String): String {
        return try {
            val date = dateFormat.parse(dateString)
            displayDateFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    fun getDayIndex(startDate: String, targetDate: String): Int {
        // 计算从起始日期到目标日期是第几天 (从1开始)
        return try {
            val start = dateFormat.parse(startDate) ?: return -1
            val target = dateFormat.parse(targetDate) ?: return -1
            val diff = target.time - start.time
            val days = (diff / (24 * 60 * 60 * 1000)).toInt()
            days + 1 // 第1天 = 起始日
        } catch (e: Exception) {
            -1
        }
    }

    fun getDayIndexFromStart(startDate: String): Int {
        // 计算今天是第几天
        return getDayIndex(startDate, getTodayString())
    }

    fun getNextDate(dateString: String): String {
        // 获取下一天的日期
        return try {
            val date = dateFormat.parse(dateString) ?: return dateString
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            dateFormat.format(calendar.time)
        } catch (e: Exception) {
            dateString
        }
    }

    fun isSameDay(date1: String, date2: String): Boolean {
        return date1 == date2
    }

    fun parseTimeToMillis(timeString: String): Long {
        // 将 "HH:mm" 转换为当天的毫秒数
        return try {
            val parts = timeString.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        } catch (e: Exception) {
            0L
        }
    }

    fun getTodayAtTime(timeString: String): Long {
        // 获取今天指定时间的毫秒数
        val calendar = Calendar.getInstance()
        try {
            val parts = timeString.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        } catch (e: Exception) {
            // 默认设为当前时间+1分钟
            calendar.add(Calendar.MINUTE, 1)
        }
        return calendar.timeInMillis
    }

    fun getTomorrowAtTime(timeString: String): Long {
        // 获取明天指定时间的毫秒数
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        try {
            val parts = timeString.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        } catch (e: Exception) {
            // 默认设为明天同一时间
        }
        return calendar.timeInMillis
    }

    fun getDateAtDayIndex(startDate: String, dayIndex: Int): String {
        // 获取起始日期第N天对应的日期字符串
        return try {
            val start = dateFormat.parse(startDate) ?: return startDate
            val calendar = Calendar.getInstance()
            calendar.time = start
            calendar.add(Calendar.DAY_OF_YEAR, dayIndex - 1)
            dateFormat.format(calendar.time)
        } catch (e: Exception) {
            startDate
        }
    }

    fun getTimeRemainingForNextSlot(timeSlots: List<String>): Pair<String, Long>? {
        // 返回下一个时段的时间和剩余毫秒数
        val now = Calendar.getInstance()
        val currentTime = now.timeInMillis
        
        // 按时间排序
        val sortedSlots = timeSlots.sortedBy { it }
        
        for (slot in sortedSlots) {
            val slotMillis = getTodayAtTime(slot)
            if (slotMillis > currentTime) {
                return Pair(slot, slotMillis - currentTime)
            }
        }
        
        // 当天所有时段已过，返回明天第一个时段
        if (sortedSlots.isNotEmpty()) {
            val firstSlot = sortedSlots.first()
            val tomorrowMillis = getTomorrowAtTime(firstSlot)
            return Pair(firstSlot, tomorrowMillis - currentTime)
        }
        
        return null
    }

    fun isTimePassedToday(timeString: String): Boolean {
        // 判断今天指定时间是否已过
        val now = Calendar.getInstance().timeInMillis
        val slotTime = getTodayAtTime(timeString)
        return now > slotTime
    }

    fun isTimeInFutureToday(timeString: String): Boolean {
        // 判断今天指定时间是否在未来
        return !isTimePassedToday(timeString)
    }

    fun getRemainingSlotsToday(timeSlots: List<String>): List<String> {
        // 获取今天还未到来的时段
        val now = Calendar.getInstance().timeInMillis
        return timeSlots
            .sorted()
            .filter { getTodayAtTime(it) > now }
    }

    fun formatTimeDifference(millis: Long): String {
        val seconds = millis / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return when {
            hours > 0 -> "${hours}小时${minutes}分钟"
            minutes > 0 -> "${minutes}分钟${secs}秒"
            else -> "${secs}秒"
        }
    }

    fun formatTimeShort(millis: Long): String {
        val seconds = millis / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        
        return when {
            hours > 0 -> "${hours}h${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "0m"
        }
    }

    fun getDayNameSuffix(day: Int): String {
        return when (day) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }
}
