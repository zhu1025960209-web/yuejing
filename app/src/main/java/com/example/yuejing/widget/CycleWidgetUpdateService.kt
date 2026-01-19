package com.example.yuejing.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.widget.RemoteViews
import com.example.yuejing.MainActivity
import com.example.yuejing.R
import com.example.yuejing.data.database.SQLiteDatabaseHelper
import com.example.yuejing.data.model.PeriodRecord
import com.example.yuejing.domain.predictor.CyclePredictor
import com.example.yuejing.model.WidgetSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 小部件更新服务：负责小部件的数据获取、业务逻辑处理和UI更新
 */
class CycleWidgetUpdateService {
    
    /**
     * 更新小部件
     */
    fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        try {
            // 加载小部件设置
            val widgetSettings = loadWidgetSettings(context)
            val backgroundColor = widgetSettings.second
            val backgroundImagePath = widgetSettings.third
            
            // 创建RemoteViews
            val views = RemoteViews(context.packageName, R.layout.cycle_widget_layout)
            
            // 设置背景
            setupWidgetBackground(context, views, backgroundColor, backgroundImagePath)
            
            // 加载记录数据
            val records = loadRecordsForWidget(context)
            
            // 计算周期预测
            val predictor = CyclePredictor(records)
            val predictionDates = predictor.predictNextPeriod()
            val today = LocalDate.now()
            
            // 计算距离各阶段的天数
            val nextPeriodStart = predictionDates[0]
            val daysToNextPeriod = ChronoUnit.DAYS.between(today, nextPeriodStart)
            val ovulationDate = predictionDates[2]
            val daysToOvulation = ChronoUnit.DAYS.between(today, ovulationDate)
            
            // 确定当前周期阶段
            val currentPhase = determineCurrentPhase(today, predictionDates)
            
            // 格式化日期
            val dateFormatter = DateTimeFormatter.ofPattern("MM-dd")
            
            // 设置小部件内容
            views.setTextViewText(R.id.widget_title, "月经周期")
            views.setTextViewText(R.id.widget_phase, currentPhase)
            views.setTextViewText(R.id.widget_distance_info, getDistanceInfo(daysToNextPeriod, daysToOvulation))
            views.setTextViewText(R.id.widget_next_period, "下次: ${nextPeriodStart.format(dateFormatter)}")
            views.setTextViewText(R.id.widget_ovulation, "排卵: ${ovulationDate.format(dateFormatter)}")
            
            // 设置点击事件
            setupClickEvents(context, views)
            
            // 更新小部件
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 设置小部件背景
     */
    private fun setupWidgetBackground(
        context: Context,
        views: RemoteViews,
        backgroundColor: Int,
        backgroundImagePath: String?
    ) {
        try {
            // 获取小部件的默认尺寸
            val width = context.resources.displayMetrics.widthPixels / 2 // 2列宽度
            val height = (context.resources.displayMetrics.heightPixels / 12 * 1.5).toInt() // 1.5行高度
            val radius = 20f // 适中的圆角半径
            
            if (backgroundImagePath != null && backgroundImagePath.isNotEmpty()) {
                // 使用背景图片
                val bitmap = BitmapFactory.decodeFile(backgroundImagePath)
                if (bitmap != null) {
                    // 缩放图片以适应小部件尺寸
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
                    val roundedBitmap = createRoundedBitmap(scaledBitmap, radius)
                    views.setImageViewBitmap(R.id.widget_background, roundedBitmap)
                } else {
                    // 图片加载失败，使用默认背景色
                    val defaultBitmap = createRoundedColorBitmap(backgroundColor, width, height, radius)
                    views.setImageViewBitmap(R.id.widget_background, defaultBitmap)
                }
            } else {
                // 使用背景颜色
                val colorBitmap = createRoundedColorBitmap(backgroundColor, width, height, radius)
                views.setImageViewBitmap(R.id.widget_background, colorBitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 出错时使用默认背景
            val radius = 20f
            val defaultBitmap = createRoundedColorBitmap(0xFFF5D8E4.toInt(), 240, 120, radius)
            views.setImageViewBitmap(R.id.widget_background, defaultBitmap)
        }
    }
    
    /**
     * 创建圆角矩形图片
     */
    private fun createRoundedBitmap(bitmap: Bitmap, radius: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)
        
        paint.isAntiAlias = true
        canvas.drawRoundRect(rectF, radius, radius, paint)
        
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        
        return output
    }
    
    /**
     * 创建圆角矩形颜色图片
     */
    private fun createRoundedColorBitmap(color: Int, width: Int, height: Int, radius: Float): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val rect = Rect(0, 0, width, height)
        val rectF = RectF(rect)
        
        paint.isAntiAlias = true
        paint.color = color
        canvas.drawRoundRect(rectF, radius, radius, paint)
        
        return output
    }
    
    /**
     * 确定当前周期阶段
     */
    private fun determineCurrentPhase(today: LocalDate, predictionDates: List<LocalDate>): String {
        val nextPeriodStart = predictionDates[0]
        val nextPeriodEnd = predictionDates[1]
        val ovulationDate = predictionDates[2]
        val fertileStart = predictionDates[3]
        val fertileEnd = predictionDates[4]
        
        return when {
            // 经期
            today >= nextPeriodStart && today <= nextPeriodEnd -> "经期"
            // 排卵期
            today == ovulationDate -> "排卵期"
            // 易孕期
            today >= fertileStart && today <= fertileEnd -> "易孕期"
            // 卵泡期（经期结束后到易孕期开始前）
            today > nextPeriodEnd && today < fertileStart -> "卵泡期"
            // 黄体期（易孕期结束后到下次经期开始前）
            today > fertileEnd && today < nextPeriodStart -> "黄体期"
            // 默认情况
            else -> "卵泡期"
        }
    }
    
    /**
     * 获取距离信息
     */
    private fun getDistanceInfo(daysToNextPeriod: Long, daysToOvulation: Long): String {
        return when {
            daysToNextPeriod > 0 -> "距离下次月经还有 ${daysToNextPeriod} 天"
            daysToOvulation > 0 -> "距离排卵还有 ${daysToOvulation} 天"
            daysToOvulation < 0 -> "排卵已过 ${-daysToOvulation} 天"
            else -> "今天排卵日"
        }
    }
    
    /**
     * 设置点击事件
     */
    private fun setupClickEvents(context: Context, views: RemoteViews) {
        // 小部件容器点击事件 - 打开应用
        val appIntent = android.content.Intent(context, MainActivity::class.java)
        val appPendingIntent = android.app.PendingIntent.getActivity(context, 0, appIntent, android.app.PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_container, appPendingIntent)
        
        // 刷新按钮点击事件 - 手动刷新
        val refreshIntent = android.content.Intent(context, CycleWidgetProvider::class.java)
        refreshIntent.action = CycleWidgetProvider.ACTION_REFRESH
        val refreshPendingIntent = android.app.PendingIntent.getBroadcast(context, 0, refreshIntent, android.app.PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)
    }
    
    /**
     * 加载记录数据
     */
    private fun loadRecordsForWidget(context: Context): List<PeriodRecord> {
        return try {
            // 使用runBlocking包装协程调用
            runBlocking {
                val dbHelper = SQLiteDatabaseHelper(context)
                val records = dbHelper.getAllRecords()
                records
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 加载小部件设置
     */
    private fun loadWidgetSettings(context: Context): Triple<String, Int, String?> {
        try {
            val file = File(context.filesDir, "widget_settings.json")
            if (!file.exists()) {
                return Triple("粉色 (默认)", 0xFFF5D8E4.toInt(), null)
            }
            val settingsJson = file.readText()
            val settings = Json.decodeFromString<WidgetSettings>(settingsJson)
            return Triple(settings.background_name, settings.background_color, settings.background_image_path)
        } catch (e: Exception) {
            e.printStackTrace()
            return Triple("粉色 (默认)", 0xFFF5D8E4.toInt(), null)
        }
    }
}
