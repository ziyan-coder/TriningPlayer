package com.example.trainingplayer

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.trainingplayer.database.AppDatabase
import com.example.trainingplayer.database.DayContentDao
import com.example.trainingplayer.database.DayNameDao
import com.example.trainingplayer.database.TimeSlotDao
import com.example.trainingplayer.model.DayContent
import com.example.trainingplayer.model.DayName
import com.example.trainingplayer.model.TimeSlot
import com.example.trainingplayer.utils.TimeHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*

class CreateTemplateActivity : AppCompatActivity() {

    private lateinit var etTemplateName: TextInputEditText
    private lateinit var etTotalDays: TextInputEditText
    private lateinit var layoutTimeSlots: LinearLayout
    private lateinit var layoutDayContents: LinearLayout
    private lateinit var btnAddTimeSlot: MaterialButton
    private lateinit var btnSave: TextView
    private lateinit var btnBack: ImageView

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var timeSlots: MutableList<TimeSlot> = mutableListOf()
    private var dayContents: MutableMap<Int, MutableMap<Long, String>> = mutableMapOf()
    private var dayNames: MutableMap<Int, String> = mutableMapOf()
    private var slotCounter = 0L

    private val audioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                // 获取文件路径和名称
                val audioName = getFileName(uri) ?: "未知音频"
                val audioPath = uri.toString()
                
                // 找到当前正在编辑的slot
                val tag = result.data?.getStringExtra("slotTag") ?: ""
                val parts = tag.split(":")
                if (parts.size == 2) {
                    val dayIndex = parts[0].toIntOrNull() ?: 0
                    val slotId = parts[1].toLongOrNull() ?: 0L
                    
                    if (dayIndex > 0 && slotId > 0L) {
                        // 保存音频到对应的dayContent
                        val dayContent = dayContents.getOrPut(dayIndex) { mutableMapOf() }
                        dayContent[slotId] = audioPath
                        
                        // 更新UI
                        updateDayContentUI(dayIndex)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_template)

        etTemplateName = findViewById(R.id.etTemplateName)
        etTotalDays = findViewById(R.id.etTotalDays)
        layoutTimeSlots = findViewById(R.id.layoutTimeSlots)
        layoutDayContents = findViewById(R.id.layoutDayContents)
        btnAddTimeSlot = findViewById(R.id.btnAddTimeSlot)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)

        btnAddTimeSlot.setOnClickListener { addTimeSlot() }
        btnSave.setOnClickListener { saveTemplate() }
        btnBack.setOnClickListener { finish() }

        // 添加默认时段
        addTimeSlot("08:00")
        addTimeSlot("15:00")
        addTimeSlot("21:00")
    }

    private fun addTimeSlot(time: String = "") {
        val slotId = ++slotCounter
        val slot = TimeSlot(
            id = slotId,
            templateId = 0,
            time = time,
            sortOrder = timeSlots.size
        )
        timeSlots.add(slot)

        // 更新UI
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.item_time_slot, layoutTimeSlots, false)
        val etSlotTime = view.findViewById<TextInputEditText>(R.id.etSlotTime)
        val tvSlotAudio = view.findViewById<TextView>(R.id.tvSlotAudio)
        val btnSelectAudio = view.findViewById<ImageView>(R.id.btnSelectAudio)
        val btnRemoveSlot = view.findViewById<ImageView>(R.id.btnRemoveSlot)

        etSlotTime.setText(time)
        etSlotTime.setTag("slot_${slotId}")
        
        btnSelectAudio.setOnClickListener {
            // 选择音频
            selectAudioForSlot(0, slotId)
        }

        btnRemoveSlot.setOnClickListener {
            // 移除时段
            layoutTimeSlots.removeView(view)
            timeSlots.remove(slot)
            // 重新排序
            timeSlots.forEachIndexed { index, it -> it.sortOrder = index }
        }

        layoutTimeSlots.addView(view)
        updateDayContents()
    }

    private fun selectAudioForSlot(dayIndex: Int, slotId: Long) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra("slotTag", "$dayIndex:$slotId")
        }
        audioPickerLauncher.launch(intent)
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        return it.getString(nameIndex)
                    }
                }
            }
            uri.path?.substringAfterLast("/")
        } catch (e: Exception) {
            null
        }
    }

    private fun updateDayContents() {
        val totalDays = etTotalDays.text.toString().toIntOrNull() ?: 0
        if (totalDays <= 0) return

        // 清空现有内容
        layoutDayContents.removeAllViews()
        dayContents.clear()
        dayNames.clear()

        // 为每一天创建内容UI
        for (dayIndex in 1..totalDays) {
            val inflater = LayoutInflater.from(this)
            val view = inflater.inflate(R.layout.item_day_content, layoutDayContents, false)
            val tvDayTitle = view.findViewById<TextView>(R.id.tvDayTitle)
            val etDayName = view.findViewById<TextInputEditText>(R.id.etDayName)
            val layoutDaySlots = view.findViewById<LinearLayout>(R.id.layoutDaySlots)
            val tvInheritHint = view.findViewById<TextView>(R.id.tvInheritHint)

            tvDayTitle.text = "第${dayIndex}天"
            
            // 日期名称
            etDayName.setTag("dayName_$dayIndex")
            etDayName.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val name = etDayName.text.toString().trim()
                    if (name.isNotEmpty()) {
                        dayNames[dayIndex] = name
                    }
                }
            }

            // 如果是第1天，手动设置；否则继承前一天
            if (dayIndex == 1) {
                tvInheritHint.visibility = View.GONE
            } else {
                tvInheritHint.visibility = View.VISIBLE
                // 继承前一天的音频
                val prevDayContents = dayContents[dayIndex - 1] ?: emptyMap()
                dayContents[dayIndex] = prevDayContents.toMutableMap()
            }

            // 为每个时段创建音频选择
            for (slot in timeSlots) {
                val slotView = inflater.inflate(R.layout.item_day_slot, layoutDaySlots, false)
                val tvSlotTimeLabel = slotView.findViewById<TextView>(R.id.tvSlotTimeLabel)
                val tvSlotAudioName = slotView.findViewById<TextView>(R.id.tvSlotAudioName)
                val btnSelectSlotAudio = slotView.findViewById<ImageView>(R.id.btnSelectSlotAudio)

                tvSlotTimeLabel.text = slot.time

                // 获取该天该时段的音频
                val dayContentMap = dayContents[dayIndex] ?: mutableMapOf()
                val audioPath = dayContentMap[slot.id] ?: ""
                val audioName = if (audioPath.isNotEmpty()) {
                    audioPath.substringAfterLast("/")
                } else {
                    "未选择"
                }
                tvSlotAudioName.text = audioName

                btnSelectSlotAudio.setOnClickListener {
                    selectAudioForSlot(dayIndex, slot.id)
                }

                layoutDaySlots.addView(slotView)
            }

            layoutDayContents.addView(view)
        }
    }

    private fun updateDayContentUI(dayIndex: Int) {
        // 重新刷新指定天的UI
        val childCount = layoutDayContents.childCount
        for (i in 0 until childCount) {
            val view = layoutDayContents.getChildAt(i)
            val tvDayTitle = view.findViewById<TextView>(R.id.tvDayTitle)
            if (tvDayTitle.text == "第${dayIndex}天") {
                val layoutDaySlots = view.findViewById<LinearLayout>(R.id.layoutDaySlots)
                val dayContentMap = dayContents[dayIndex] ?: emptyMap()
                
                for (j in 0 until layoutDaySlots.childCount) {
                    val slotView = layoutDaySlots.getChildAt(j)
                    val tvSlotAudioName = slotView.findViewById<TextView>(R.id.tvSlotAudioName)
                    val slotId = timeSlots.getOrNull(j)?.id ?: continue
                    val audioPath = dayContentMap[slotId] ?: ""
                    val audioName = if (audioPath.isNotEmpty()) {
                        audioPath.substringAfterLast("/")
                    } else {
                        "未选择"
                    }
                    tvSlotAudioName.text = audioName
                }
                break
            }
        }
    }

    private fun saveTemplate() {
        val name = etTemplateName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入模板名称", Toast.LENGTH_SHORT).show()
            return
        }

        val totalDays = etTotalDays.text.toString().toIntOrNull()
        if (totalDays == null || totalDays < 1 || totalDays > 60) {
            Toast.makeText(this, "请输入有效的总天数 (1-60)", Toast.LENGTH_SHORT).show()
            return
        }

        if (timeSlots.isEmpty()) {
            Toast.makeText(this, "请至少添加一个播放时段", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查是否所有天的所有时段都已设置音频
        for (dayIndex in 1..totalDays) {
            val dayContent = dayContents[dayIndex] ?: emptyMap()
            for (slot in timeSlots) {
                if (!dayContent.containsKey(slot.id) || dayContent[slot.id].isNullOrEmpty()) {
                    Toast.makeText(this, "第${dayIndex}天 ${slot.time} 未选择音频", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        // 保存模板到数据库
        scope.launch {
            try {
                val db = AppDatabase.getInstance(this@CreateTemplateActivity)
                val templateDao = db.templateDao()
                val timeSlotDao = db.timeSlotDao()
                val dayContentDao = db.dayContentDao()
                val dayNameDao = db.dayNameDao()

                // 创建模板
                val template = com.example.trainingplayer.model.PlanTemplate(
                    name = name,
                    totalDays = totalDays,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                val templateId = templateDao.insertTemplate(template)

                // 保存时段
                for (slot in timeSlots) {
                    val newSlot = slot.copy(templateId = templateId)
                    timeSlotDao.insertTimeSlot(newSlot)
                }

                // 获取插入后的时段ID
                val insertedSlots = timeSlotDao.getTimeSlotsForTemplate(templateId)

                // 保存日期名称
                for ((dayIndex, dayName) in dayNames) {
                    val dayNameEntity = DayName(
                        templateId = templateId,
                        dayIndex = dayIndex,
                        name = dayName
                    )
                    dayNameDao.insertDayName(dayNameEntity)
                }

                // 保存每日内容
                for ((dayIndex, dayContentMap) in dayContents) {
                    for ((slotId, audioPath) in dayContentMap) {
                        // 找到对应的新slotId
                        val slot = insertedSlots.find { it.time == timeSlots.find { it.id == slotId }?.time }
                        val newSlotId = slot?.id ?: slotId
                        
                        val dayContent = DayContent(
                            templateId = templateId,
                            dayIndex = dayIndex,
                            slotId = newSlotId,
                            audioPath = audioPath,
                            audioName = audioPath.substringAfterLast("/"),
                            isManualModified = dayIndex == 1
                        )
                        dayContentDao.insertDayContent(dayContent)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CreateTemplateActivity, "模板「$name」保存成功！", Toast.LENGTH_LONG).show()
                    finish()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CreateTemplateActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
