package com.example.trainingplayer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.trainingplayer.R
import com.example.trainingplayer.database.AppDatabase
import com.example.trainingplayer.model.PlanInstance
import com.example.trainingplayer.utils.TimeHelper
import kotlinx.coroutines.*

class HistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HistoryAdapter
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var history: List<PlanInstance> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rvHistory)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = HistoryAdapter(emptyList())
        recyclerView.adapter = adapter

        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        scope.launch {
            try {
                val db = AppDatabase.getInstance(requireContext())
                val instanceDao = db.instanceDao()
                history = instanceDao.getHistoryInstances()

                withContext(Dispatchers.Main) {
                    adapter.updateData(history)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    inner class HistoryAdapter(
        private var history: List<PlanInstance>
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        fun updateData(newHistory: List<PlanInstance>) {
            history = newHistory
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val instance = history[position]
            holder.bind(instance)
        }

        override fun getItemCount(): Int = history.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvHistoryName)
            val tvInfo: TextView = itemView.findViewById(R.id.tvHistoryInfo)
            val tvStatus: TextView = itemView.findViewById(R.id.tvHistoryStatus)

            fun bind(instance: PlanInstance) {
                tvName.text = instance.templateName

                val startDate = TimeHelper.formatDateForDisplay(instance.startDate)
                val endDate = if (instance.endedAt > 0) {
                    TimeHelper.formatDateForDisplay(
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            .format(java.util.Date(instance.endedAt))
                    )
                } else {
                    "进行中"
                }

                tvInfo.text = "$startDate → $endDate · ${instance.currentDay}天"

                val statusText = when (instance.status) {
                    "COMPLETED" -> "已完成"
                    "TERMINATED" -> "已终止"
                    "PAUSED" -> "已暂停"
                    else -> instance.status
                }
                tvStatus.text = statusText

                // 设置状态颜色
                val color = when (instance.status) {
                    "COMPLETED" -> R.color.green
                    "TERMINATED" -> R.color.red
                    "PAUSED" -> R.color.yellow
                    else -> R.color.text_secondary
                }
                tvStatus.setTextColor(resources.getColor(color, null))
                tvStatus.setBackgroundResource(R.drawable.bg_status_badge)
            }
        }
    }
}
