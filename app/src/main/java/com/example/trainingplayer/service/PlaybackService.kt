package com.example.trainingplayer.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.trainingplayer.R
import com.example.trainingplayer.database.AppDatabase
import com.example.trainingplayer.database.DayContentDao
import com.example.trainingplayer.database.InstanceDao
import com.example.trainingplayer.database.TimeSlotDao
import com.example.trainingplayer.utils.TimeHelper
import kotlinx.coroutines.*

class PlaybackService : Service() {
    
    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "training_playback_channel"
        private const val ACTION_PLAY = "PLAY"
        private const val ACTION_STOP = "STOP"
        private const val ACTION_PAUSE = "PAUSE"
        
        private var currentInstanceId: Long = -1
        private var currentSlotId: Long = -1
        private var currentDayIndex: Int = -1
        private var currentAudioName: String = ""
        private var currentSlotTime: String = ""
        
        fun isPlaying(): Boolean {
            return currentInstanceId != -1L
        }
        
        fun getCurrentAudioName(): String = currentAudioName
        fun getCurrentSlotTime(): String = currentSlotTime
        fun getCurrentDayIndex(): Int = currentDayIndex
    }
    
    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TrainingPlayer::WakeLock"
        )
        wakeLock.acquire(10 * 60 * 1000L) // 最多持有10分钟
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val action = it.getStringExtra("action") ?: ""
            when (action) {
                ACTION_PLAY -> {
                    val audioPath = it.getStringExtra("audioPath") ?: ""
                    val audioName = it.getStringExtra("audioName") ?: "未知音频"
                    val slotId = it.getLongExtra("slotId", -1)
                    val dayIndex = it.getIntExtra("dayIndex", -1)
                    val instanceId = it.getLongExtra("instanceId", -1)
                    val slotTime = it.getStringExtra("slotTime") ?: ""
                    
                    currentInstanceId = instanceId
                    currentSlotId = slotId
                    currentDayIndex = dayIndex
                    currentAudioName = audioName
                    currentSlotTime = slotTime
                    
                    startForeground(NOTIFICATION_ID, createNotification("正在播放", audioName))
                    playAudio(audioPath, audioName, instanceId, dayIndex, slotId, slotTime)
                }
                ACTION_STOP, ACTION_PAUSE -> {
                    stopPlayback()
                }
            }
        }
        return START_STICKY
    }
    
    private fun playAudio(audioPath: String, audioName: String, instanceId: Long, dayIndex: Int, slotId: Long, slotTime: String) {
        try {
            // 释放旧的播放器
            mediaPlayer?.release()
            mediaPlayer = null
            
            // 请求音频焦点
            requestAudioFocus()
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                prepare()
                setOnCompletionListener {
                    // 播放完成
                    Log.d("PlaybackService", "Playback completed for $audioName")
                    updateNotification("刚刚播放", audioName)
                    releaseWakeLock()
                    
                    // 标记该时段已播放
                    scope.launch {
                        markSlotPlayed(instanceId, dayIndex, slotId, slotTime)
                    }
                    
                    // 检查是否所有时段都已完成
                    scope.launch {
                        checkDayCompletion(instanceId, dayIndex)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("PlaybackService", "MediaPlayer error: what=$what, extra=$extra")
                    releaseWakeLock()
                    false
                }
                start()
            }
            
            // 更新通知
            updateNotification("正在播放", audioName)
            
        } catch (e: Exception) {
            Log.e("PlaybackService", "Error playing audio", e)
            releaseWakeLock()
        }
    }
    
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .build()
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }
    
    private fun stopPlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        releaseWakeLock()
        
        // 更新通知
        updateNotification("已停止", currentAudioName)
        
        // 取消音频焦点
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.abandonAudioFocus(null)
        }
    }
    
    private fun releaseWakeLock() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }
    
    private suspend fun markSlotPlayed(instanceId: Long, dayIndex: Int, slotId: Long, slotTime: String) {
        // 记录该时段已播放（暂不实现完整记录，仅用于状态管理）
        // 实际项目中可用一个表记录播放历史
        Log.d("PlaybackService", "Slot played: day=$dayIndex, slot=$slotTime")
    }
    
    private suspend fun checkDayCompletion(instanceId: Long, dayIndex: Int) {
        try {
            val db = AppDatabase.getInstance(this@PlaybackService)
            val instanceDao = db.instanceDao()
            val timeSlotDao = db.timeSlotDao()
            val instance = instanceDao.getInstanceById(instanceId) ?: return
            
            // 获取该天的所有时段
            val allSlots = timeSlotDao.getTimeSlotsForTemplate(instance.templateId)
            // 这里简化判断，实际需要检查是否所有时段都已播放
            // 由于没有播放历史表，暂时省略详细检查
            
            Log.d("PlaybackService", "Day $dayIndex completion check")
            
        } catch (e: Exception) {
            Log.e("PlaybackService", "Error checking day completion", e)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "训练播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "训练计划播放通知"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(title: String, content: String): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        
        // 不添加任何操作按钮
        
        return builder.build()
    }
    
    private fun updateNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        releaseWakeLock()
        currentInstanceId = -1
        stopForeground(true)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
