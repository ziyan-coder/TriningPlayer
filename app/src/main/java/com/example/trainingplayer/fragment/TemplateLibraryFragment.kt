package com.example.trainingplayer.fragment

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.trainingplayer.CreateTemplateActivity
import com.example.trainingplayer.R
import com.example.trainingplayer.database.AppDatabase
import com.example.trainingplayer.database.DayContentDao
import com.example.trainingplayer.database.DayNameDao
import com.example.trainingplayer.database.InstanceDao
import com.example.trainingplayer.database.TimeSlotDao
import com.example.trainingplayer.model.PlanInstance
import com.example.trainingplayer.model.PlanTemplate
import com.example.trainingplayer.utils.AlarmScheduler
import com.example.trainingplayer.utils.TimeHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*

class TemplateLibraryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TemplateAdapter
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var templates: List<PlanTemplate> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_template_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rvTemplates)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = TemplateAdapter(emptyList()) { template ->
            showStartPlanDialog(template)
        }
        recyclerView.adapter = adapter

        view.findViewById<MaterialButton>(R.id.btnCreateTemplate).setOnClickListener {
            startActivity(Intent(requireContext(), CreateTemplateActivity::class.java))
        }

        loadTemplates()
    }

    override fun onResume() {
        super.onResume()
        loadTemplates()
    }

    private fun loadTemplates() {
        scope.launch {
            try {
                val db = AppDatabase.getInstance(requireContext())
                templates = db.templateDao().getAllTemplates()

                // 加载每个模板的时段和日期名称
                val enhancedTemplates = templates.map { template ->
                    val timeSlots = db.timeSlotDao().getTimeSlotsForTemplate(template.id)
                    val dayNames = db.dayNameDao().getDayNamesForTemplate(template.id)
                        .associate { it.dayIndex to it.name }
                    template.apply {
                        this.timeSlots = timeSlots
                        this.dayNames = dayNames
                    }
                }

                withContext(Dispatchers.Main) {
                    adapter.updateData(enhancedTemplates)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "加载模板失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showStartPlanDialog(template: PlanTemplate) {
        // 显示日期选择器
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("选择起始日期")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.show(childFragmentManager, "DATE_PICKER")

        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = TimeHelper.formatDateForDisplay(
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(java.util.Date(selection))
            )
            // 解析日期
            val dateFormat = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
            val startDate = try {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(dateFormat.parse(date) ?: java.util.Date())
            } catch (e: Exception) {
                TimeHelper.getTodayString()
            }

            startPlan(template, startDate)
        }
    }

    private fun startPlan(template: PlanTemplate, startDate: String) {
        scope.launch {
            try {
                val db = AppDatabase.getInstance(requireContext())
                val instanceDao = db.instanceDao()

                // 检查是否有运行中的实例
                val runningInstance = instanceDao.getCurrentRunningInstance()
                if (runningInstance != null) {
                    // 弹出确认对话框
                    withContext(Dispatchers.Main) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("替换运行中的计划")
                            .setMessage("已有运行中的计划「${runningInstance.templateName}」，将自动终止并启动新计划。是否继续？")
                            .setPositiveButton("确认") { _, _ ->
                                scope.launch {
                                    terminateAndStartNew(template, startDate, runningInstance)
                                }
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                    return@launch
                }

                // 没有运行中的实例，直接启动
                createAndStartInstance(template, startDate)

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun terminateAndStartNew(template: PlanTemplate, startDate: String, oldInstance: PlanInstance) {
        // 终止旧实例
        val db = AppDatabase.getInstance(requireContext())
        val instanceDao = db.instanceDao()

        // 取消所有闹钟
        AlarmScheduler.cancelAllAlarmsForInstance(requireContext(), oldInstance.id)

        // 更新状态
        oldInstance.apply {
            status = "TERMINATED"
            endedAt = System.currentTimeMillis()
        }
        instanceDao.updateInstance(oldInstance)

        // 创建新实例
        createAndStartInstance(template, startDate)
    }

    private suspend fun createAndStartInstance(template: PlanTemplate, startDate: String) {
        val db = AppDatabase.getInstance(requireContext())
        val instanceDao = db.instanceDao()
        val timeSlotDao = db.timeSlotDao()
        val dayContentDao = db.dayContentDao()
        val dayNameDao = db.dayNameDao()

        // 创建实例
        val instance = PlanInstance(
            templateId = template.id,
            templateName = template.name,
            startDate = startDate,
            status = "RUNNING",
            currentDay = 1,
            startedAt = System.currentTimeMillis()
        )

        val instanceId = instanceDao.insertInstance(instance)

        // 复制模板的日期名称到实例（通过冗余存储，实际可存JSON或关联表）
        // 这里简化：直接使用模板的日期名称
        val dayNames = template.dayNames

        // 设置闹钟：今天剩余时段
        val today = TimeHelper.getTodayString()
        val dayIndex = TimeHelper.getDayIndex(startDate, today)

        if (dayIndex >= 1 && dayIndex <= template.totalDays) {
            val slots = timeSlotDao.getTimeSlotsForTemplate(template.id)
            val remainingSlots = slots
                .filter { TimeHelper.isTimeInFutureToday(it.time) }

            for (slot in remainingSlots) {
                val alarmTime = TimeHelper.getTodayAtTime(slot.time)
                if (alarmTime > System.currentTimeMillis()) {
                    AlarmScheduler.scheduleAlarm(
                        requireContext(),
                        alarmTime,
                        slot.id,
                        dayIndex,
                        instanceId
                    )
                }
            }
        } else {
            // 如果起始日期是今天或未来，但今天已过所有时段，安排明天第一个时段
            val slots = timeSlotDao.getTimeSlotsForTemplate(template.id)
            if (slots.isNotEmpty()) {
                val firstSlot = slots.minByOrNull { it.sortOrder } ?: slots.first()
                val tomorrowTime = TimeHelper.getTomorrowAtTime(firstSlot.time)
                AlarmScheduler.scheduleAlarm(
                    requireContext(),
                    tomorrowTime,
                    firstSlot.id,
                    dayIndex,
                    instanceId
                )
            }
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), "计划「${template.name}」已启动！", Toast.LENGTH_LONG).show()
            // 刷新当前运行Fragment
            (parentFragmentManager.findFragmentByTag("f1") as? CurrentPlanFragment)?.refresh()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    inner class TemplateAdapter(
        private var templates: List<PlanTemplate>,
        private val onStartClick: (PlanTemplate) -> Unit
    ) : RecyclerView.Adapter<TemplateAdapter.ViewHolder>() {

        fun updateData(newTemplates: List<PlanTemplate>) {
            templates = newTemplates
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_template, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val template = templates[position]
            holder.bind(template)
            holder.btnStart.setOnClickListener { onStartClick(template) }
        }

        override fun getItemCount(): Int = templates.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvTemplateName)
            val tvInfo: TextView = itemView.findViewById(R.id.tvTemplateInfo)
            val btnStart: MaterialButton = itemView.findViewById(R.id.btnStartPlan)

            fun bind(template: PlanTemplate) {
                tvName.text = template.name
                val slotCount = template.timeSlots.size
                val dayNames = template.dayNames
                val firstDayName = dayNames[1] ?: "第1天"
                tvInfo.text = "${template.totalDays}天 · ${slotCount}个时段 · 起始日：$firstDayName"
            }
        }
    }
}
