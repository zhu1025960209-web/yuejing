package com.example.yuejing.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.yuejing.CycleWidgetWorker

/**
 * 小部件提供者：负责接收系统广播事件，触发小部件更新
 */
class CycleWidgetProvider : AppWidgetProvider() {
    companion object {
        // 广播动作常量
        const val ACTION_UPDATE = "com.example.yuejing.UPDATE_WIDGET"
        const val ACTION_REFRESH = "com.example.yuejing.REFRESH_WIDGET"
        private const val WORKER_NAME = "CycleWidgetWorker"
        
        /**
         * 更新所有小部件
         */
        fun updateWidgets(context: Context) {
            val intent = Intent(context, CycleWidgetProvider::class.java)
            intent.action = ACTION_UPDATE
            context.sendBroadcast(intent)
        }
        
        /**
         * 启动定期更新任务
         */
        private fun setupPeriodicUpdate(context: Context) {
            val periodicWorkRequest = PeriodicWorkRequestBuilder<CycleWidgetWorker>(15, TimeUnit.MINUTES)
                .setInitialDelay(0, TimeUnit.MINUTES)
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORKER_NAME,
                    androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                    periodicWorkRequest
                )
        }
        
        /**
         * 执行单次更新任务
         */
        private fun performSingleUpdate(context: Context) {
            val oneTimeWorkRequest = OneTimeWorkRequestBuilder<CycleWidgetWorker>()
                .build()
            
            WorkManager.getInstance(context)
                .enqueue(oneTimeWorkRequest)
        }
    }

    /**
     * 小部件第一次添加到桌面时调用
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // 启动定期更新任务
        setupPeriodicUpdate(context)
    }

    /**
     * 所有小部件都被移除时调用
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // 取消定期更新任务
        WorkManager.getInstance(context).cancelUniqueWork(WORKER_NAME)
    }

    /**
     * 小部件更新时调用
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 使用更新服务更新所有小部件
        val updateService = CycleWidgetUpdateService()
        appWidgetIds.forEach { appWidgetId ->
            updateService.updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    /**
     * 接收广播事件
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_UPDATE -> {
                // 更新所有小部件
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, CycleWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
            ACTION_REFRESH -> {
                // 执行单次更新
                performSingleUpdate(context)
            }
        }
    }
}
