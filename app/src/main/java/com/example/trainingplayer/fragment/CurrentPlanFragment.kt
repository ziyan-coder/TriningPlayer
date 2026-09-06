package com.example.trainingplayer.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.trainingplayer.R
import com.example.trainingplayer.database.AppDatabase
import com.example.trainingplayer.database.DayContentDao
import com.example.trainingplayer.database.DayNameDao
import com.example.trainingplayer.database.InstanceDao
import com.example.trainingplayer.database.TimeSlotDao
import com.example.trainingplayer.model.PlanInstance
import com.example.trainingplayer.service.PlaybackService
import com.example.trainingplayer.utils.AlarmScheduler
import com.example.trainingplayer.utils.TimeHelper
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*

class CurrentPlanFragment : Fragment() {

    private lateinit var layoutEmpty: View
    private lateinit var layoutRunning: View
    private lateinit var tvDayName: TextView
    private lateinit var tvClock: TextView
    private lateinit var tvPlayingIcon: TextView
    private lateinit var tvCurrentlyPlaying: TextView
    private lateinit var tvNextAudio: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvNextPlay: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnPause: MaterialButton
    private lateinit var btnTerminate: MaterialButton

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var clockRunnable: Runnable? = null

    private var currentInstance: PlanInstance? = null
    private var isPaused = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_current_plan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutEmpty = view.findViewById(R.id.layoutEmpty)
        layoutRunning = view.findViewById(R.id.layoutRunning)
        tvDayName = view.findViewById(R.id.tvDayName)
        tvClock = view.findViewById(R.id.tvClock)
        tvPlayingIcon = view.findViewById(R.id.tvPlayingIcon)
        tvCurrentlyPlaying = view.findViewById(R.id.tvCurrentlyPlaying)
        tvNextAudio = view.findViewById(R.id.tvNextAudio)
        tvProgress = view.findViewById(R.id.tvProgress)
        tvNextPlay = view.findViewById(R.id.tvNextPlay)
        tvStatus = view.findViewById(R.id.tvStatus)
        btnPause = view.findViewById(R.id.btnPause)
        btnTerminate = view.findViewById(R.id.btnTerminate)

        btnPause.setOnClickListener { togglePause() }
        btnTerminate.setOnClickListener { showTerminateDialog() }

        refresh()
        startClock()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        startClock()
    }

    override fun onPause() {
        super.onPause()
        stopClock()
    }

    fun refresh() {
        scope.launch {
            try {
                val db = AppDatabase.getInstance(requireContext())
                val instanceDao = db.instanceDao()
                val instance = instanceDao.getCurrentRunningInstance()

                if (instance != null) {
                    // 加载完整数据
                    val timeSlotDao = db.timeSlotDao()
                    val dayContentDao = db.dayContentDao()
                    val dayNameDao = db.dayNameDao()

                    val slots = timeSlotDao.getTimeSlotsForTemplate(instance.templateId)
                    val dayNames = dayNameDao.getDayNamesForTemplate(instance.templateId)
                        .associate { it.dayIndex to it.name }

                    instance.timeSlots = slots
                    instance.dayNames = dayNames

                    // 加载当前天的内容
                    val today = TimeHelper.getTodayString()
                    val dayIndex = TimeHelper.getDayIndex(instance.startDate, today)
                    if (dayIndex in 1..instance.currentDay) {
                        val contents = dayContentDao.getDayContents(instance.templateId, dayIndex)
                        instance.dayContents = mapOf(dayIndex to contents.associate { it.slotId to it.audioPath })
                    }

                    withContext(Dispatchers.Main) {
                        currentInstance = instance
                        updateUI(instance)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showEmptyState()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateUI(instance: PlanInstance) {
        layoutEmpty.visibility = View.GONE
        layoutRunning.visibility = View.VISIBLE

        val today = TimeHelper.getTodayString()
        val startDate = instance.startDate
        val dayIndex = TimeHelper.getDayIndex(startDate, today)

        // 日期名称
        val dayName = instance.dayNames[dayIndex] ?: "第${dayIndex}天"
        tvDayName.text = dayName

        // 当前时间和状态
        val currentTime = TimeHelper.getCurrentTimeString()
        tvClock.text = currentTime

        // 进度
        tvProgress.text = "第 ${dayIndex} 天 / 共 ${instance.currentDay} 天"

        // 播放状态
        val isPlaying = PlaybackService.isPlaying()
        val currentAudio = PlaybackService.getCurrentAudioName()

        if (isPlaying) {
            tvPlayingIcon.text = "●"
            tvPlayingIcon.setTextColor(resources.getColor(R.color.green, null))
            tvCurrentlyPlaying.text = "${currentAudio}（播放中）"
        } else if (currentAudio.isNotEmpty()) {
            tvPlayingIcon.text = "▶"
            tvPlayingIcon.setTextColor(resources.getColor(R.color.text_secondary, null))
            tvCurrentlyPlaying.text = currentAudio
        } else {
            tvPlayingIcon.text = "⏸"
            tvPlayingIcon.setTextColor(resources.getColor(R.color.text_secondary, null))
            tvCurrentlyPlaying.text = "等待中..."
        }

        // 下一个音频
        val slots = instance.timeSlots.sortedBy { it.sortOrder }
        val remainingSlots = slots.filter { TimeHelper.isTimeInFutureToday(it.time) }
        if (remainingSlots.isNotEmpty()) {
            val nextSlot = remainingSlots.first()
            // 获取该时段的音频
            val dayContents = instance.dayContents[dayIndex] ?: emptyMap()
            val audioPath = dayContents[nextSlot.id] ?: ""
            val audioName = if (audioPath.isNotEmpty()) {
                audioPath.substringAfterLast("/")
            } else {
                "未设置"
            }
            tvNextAudio.text = "下一个：${nextSlot.time} $audioName"
            tvNextPlay.text = "下次播放：${nextSlot.time} $audioName"
        } else {
            tvNextAudio.text = "✅ 今日已全部完成"
            tvNextPlay.text = "今日已无播放时段"
        }

        // 状态
        when (instance.status) {
            "RUNNING" -> {
                tvStatus.text = "● 运行中"
                tvStatus.setTextColor(resources.getColor(R.color.green, null))
                btnPause.text = "暂停"
                isPaused = false
            }
            "PAUSED" -> {
                tvStatus.text = "⏸ 已暂停"
                tvStatus.setTextColor(resources.getColor(R.color.yellow, null))
                btnPause.text = "恢复"
                isPaused = true
            }
            else -> {
                tvStatus.text = "已结束"
                tvStatus.setTextColor(resources.getColor(R.color.text_secondary, null))
            }
        }
    }

    private fun showEmptyState() {
        layoutEmpty.visibility = View.VISIBLE
        layoutRunning.visibility = View.GONE
        currentInstance = null
    }

    private fun togglePause() {
        val instance = currentInstance ?: return

        if (isPaused) {
            // 恢复
            resumePlan(instance)
        } else {
            // 暂停
            pausePlan(instance)
        }
    }

    private fun pausePlan(instance: PlanInstance) {
        scope.launch {
            try {
                val db = AppDatabase.getInstance(requireContext())
                val instanceDao = db.instanceDao()

                // 取消所有闹钟
                AlarmScheduler.cancelAllAlarmsForInstance(requireContext(), instance.id)

                // 停止当前播放
                val serviceIntent = android.content.Intent(requireContext(), PlaybackService::class.java)
                serviceIntent.putExtra("action", "STOP")
                requireContext().stopService(serviceIntent)

                // 更新状态
                instance.status = "PAUSED"
                instance.pausedAt = System.currentTimeMillis()
                instanceDao.updateInstance(instance)

                withContext(Dispatchers.Main) {
                    isPaused = true
                    btnPause.text = "恢复"
                    tvStatus.text = "⏸ 已暂停"
                    tvStatus.setTextColor(resources.getColor(R.color.yellow, null))
                    Toast.makeText(requireContext(), "已暂停", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "暂停失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun resumePlan(instance: PlanInstance) {
        scope.launch {
            try {
                val db = AppDatabase.getInstance(requireContext())
                val instanceDao = db.instanceDao()
                val timeSlotDao = db.timeSlotDao()
                val dayContentDao = db.dayContentDao()

                // 获取当前日期和天数
                val today = TimeHelper.getTodayString()
                val startDate = instance.startDate
                val dayIndex = TimeHelper.getDayIndex(startDate, today)

                if (dayIndex in 1..instance.currentDay) {
                    // 获取该天的时段
                    val slots = timeSlotDao.getTimeSlotsForTemplate(instance.templateId)
                    val remainingSlots = slots
                        .filter { TimeHelper.isTimeInFutureToday(it.time) }

                    for (slot in remainingSlots) {
                        // 检查该时段是否已有内容
                        val content = dayContentDao.getDayContent(instance.templateId, dayIndex, slot.id)
                        if (content?.audioPath?.isNotEmpty() == true) {
                            val alarmTime = TimeHelper.getTodayAtTime(slot.time)
                            if (alarmTime > System.currentTimeMillis()) {
                                AlarmScheduler.scheduleAlarm(
                                    requireContext(),
                                    alarmTime,
                                    slot.id,
                                    dayIndex,
                                    instance.id
                                )
                            }
                        }
                    }

                    // 更新状态
                    instance.status = "RUNNING"
                    instance.pausedAt = 0
                    instanceDao.updateInstance(instance)

                    withContext(Dispatchers.Main) {
                        isPaused = false
                        btnPause.text = "暂停"
                        tvStatus.text = "● 运行中"
                        tvStatus.setTextColor(resources.getColor(R.color.green, null))
                        Toast.makeText(requireContext(), "已恢复", Toast.LENGTH_SHORT).show()
                        refresh()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "计划已结束或尚未开始", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "恢复失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showTerminateDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("终止计划")
            .setMessage("确定要终止当前计划吗？此操作不可撤销。")
            .setPositiveButton("确定") { _, _ ->
                terminatePlan()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun terminatePlan() {
        val instance = currentInstance ?: return

        scope.launch {
            try {
                val db = AppDatabase.getInstance(requireContext())
                val instanceDao = db.instanceDao()

                // 取消所有闹钟
                AlarmScheduler.cancelAllAlarmsForInstance(requireContext(), instance.id)

                // 停止播放
                val serviceIntent = android.content.Intent(requireContext(), PlaybackService::class.java)
                serviceIntent.putExtra("action", "STOP")
                requireContext().stopService(serviceIntent)

                // 更新状态
                instance.status = "TERMINATED"
                instance.endedAt = System.currentTimeMillis()
                instanceDao.updateInstance(instance)

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "计划已终止", Toast.LENGTH_SHORT).show()
                    showEmptyState()
                    refresh()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "终止失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateInstance(instance: PlanInstance) {
        currentInstance = instance
        updateUI(instance)
    }

    private fun startClock() {
        stopClock()
        clockRunnable = object : Runnable {
            override fun run() {
                val currentTime = TimeHelper.getCurrentTimeString()
                tvClock.text = currentTime

                // 每秒更新一次，精确到秒
                mainHandler.postDelayed(this, 1000)
            }
        }
        clockRunnable?.let { mainHandler.post(it) }
    }

    private fun stopClock() {
        clockRunnable?.let { mainHandler.removeCallbacks(it) }
        clockRunnable = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopClock()
        scope.cancel()
    }
}
