package com.example.yuejing

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.example.yuejing.widget.CycleWidgetProvider
import com.example.yuejing.widget.CycleWidgetUpdateService

class CycleWidgetWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        try {
            // 使用小部件更新服务更新所有小部件
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            val componentName = ComponentName(applicationContext, CycleWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            val updateService = CycleWidgetUpdateService()
            appWidgetIds.forEach { appWidgetId ->
                updateService.updateWidget(applicationContext, appWidgetManager, appWidgetId)
            }
            
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
}
