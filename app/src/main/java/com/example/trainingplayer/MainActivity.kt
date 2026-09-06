package com.example.trainingplayer

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.trainingplayer.database.AppDatabase
import com.example.trainingplayer.database.DayContentDao
import com.example.trainingplayer.database.InstanceDao
import com.example.trainingplayer.database.TimeSlotDao
import com.example.trainingplayer.fragment.CurrentPlanFragment
import com.example.trainingplayer.fragment.HistoryFragment
import com.example.trainingplayer.fragment.TemplateLibraryFragment
import com.example.trainingplayer.model.PlanInstance
import com.example.trainingplayer.service.PlaybackService
import com.example.trainingplayer.utils.AlarmScheduler
import com.example.trainingplayer.utils.TimeHelper
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: MainPagerAdapter
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "部分权限被拒绝，定时播放功能可能受限", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)

        adapter = MainPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "模板库"
                1 -> "当前运行"
                else -> "历史记录"
            }
        }.attach()

        // 检查并请求权限
        checkAndRequestPermissions()

        // 检查是否有运行中的实例
        checkRunningInstance()
    }

    override fun onResume() {
        super.onResume()
        // 刷新当前运行状态
        refreshCurrentFragment()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        // 通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // 精确闹钟权限 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // 引导用户去设置
                showExactAlarmPermissionDialog()
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }

        // 引导忽略电池优化
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                showBatteryOptimizationDialog()
            }
        }
    }

    private fun showExactAlarmPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要精确闹钟权限")
            .setMessage("为了确保定时播放准时触发，请允许本应用使用精确闹钟权限。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("建议忽略电池优化")
            .setMessage("为了让定时播放更可靠，建议将本应用加入电池优化白名单。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun checkRunningInstance() {
        scope.launch {
            try {
                val db = AppDatabase.getInstance(this@MainActivity)
                val instanceDao = db.instanceDao()
                val instance = instanceDao.getCurrentRunningInstance()
                if (instance != null) {
                    // 更新当前运行状态
                    withContext(Dispatchers.Main) {
                        (adapter.getFragmentAtPosition(1) as? CurrentPlanFragment)?.updateInstance(instance)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun refreshCurrentFragment() {
        val fragment = adapter.getFragmentAtPosition(1) as? CurrentPlanFragment
        fragment?.refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    inner class MainPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> TemplateLibraryFragment()
                1 -> CurrentPlanFragment()
                else -> HistoryFragment()
            }
        }

        fun getFragmentAtPosition(position: Int): Fragment? {
            return try {
                // 通过Tag获取Fragment（简化处理）
                when (position) {
                    0 -> supportFragmentManager.findFragmentByTag("f${position}")
                    1 -> supportFragmentManager.findFragmentByTag("f${position}")
                    else -> supportFragmentManager.findFragmentByTag("f${position}")
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
