package com.example.yuejing.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CycleChart(
    cycleLengths: List<Int>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textPaint = Paint().asFrameworkPaint().apply {
        isAntiAlias = true
        textSize = with(density) { 12.sp.toPx() }
        color = android.graphics.Color.parseColor("#4A2A32")
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (cycleLengths.size < 2) {
            Text(
                text = "需要更多数据",
                color = Color(0xFF7D5260),
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
            )
            return
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val padding = 40.dp.toPx()
            val chartWidth = width - 2 * padding
            val chartHeight = height - 2 * padding

            if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

            // 数据范围
            val minVal = cycleLengths.minOrNull() ?: 0
            val maxVal = cycleLengths.maxOrNull() ?: 0
            val valRange = maxVal - minVal

            // 绘制坐标轴
            drawLine(
                start = androidx.compose.ui.geometry.Offset(padding, padding),
                end = androidx.compose.ui.geometry.Offset(padding, height - padding),
                color = Color(0xFFCCCCCC),
                strokeWidth = 2f
            )
            drawLine(
                start = androidx.compose.ui.geometry.Offset(padding, height - padding),
                end = androidx.compose.ui.geometry.Offset(width - padding, height - padding),
                color = Color(0xFFCCCCCC),
                strokeWidth = 2f
            )

            // 绘制网格线
            for (i in 0..4) {
                val y = padding + i * chartHeight / 4
                drawLine(
                    start = androidx.compose.ui.geometry.Offset(padding, y),
                    end = androidx.compose.ui.geometry.Offset(width - padding, y),
                    color = Color(0xFFF0F0F0),
                    strokeWidth = 1f
                )
                val valText = (minVal + i * valRange / 4).toString()
                drawContext.canvas.nativeCanvas.drawText(
                    valText,
                    padding - 30f,
                    y + 4f,
                    textPaint
                )
            }

            // 绘制数据点和折线
            val points = mutableListOf<androidx.compose.ui.geometry.Offset>()
            for (i in cycleLengths.indices) {
                val x = padding + i * chartWidth / (cycleLengths.size - 1)
                val y = padding + chartHeight - ((cycleLengths[i] - minVal).toFloat() / valRange * chartHeight)
                points.add(androidx.compose.ui.geometry.Offset(x, y))

                // 绘制数据点
                drawCircle(
                    center = androidx.compose.ui.geometry.Offset(x, y),
                    radius = 4f,
                    color = Color(0xFFED9EBC)
                )

                // 绘制数值
                drawContext.canvas.nativeCanvas.drawText(
                    cycleLengths[i].toString(),
                    x - 10f,
                    y - 10f,
                    textPaint
                )

                // 绘制周期标记
                drawContext.canvas.nativeCanvas.drawText(
                    "第${i+1}次",
                    x - 15f,
                    height - padding + 15f,
                    textPaint
                )
            }

            // 绘制折线
            if (points.size > 1) {
                drawPath(
                    path = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    },
                    color = Color(0xFFED9EBC),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
            }
        }
    }
}

@Composable
fun SymptomChart(
    symptomData: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textPaint = Paint().asFrameworkPaint().apply {
        isAntiAlias = true
        textSize = with(density) { 12.sp.toPx() }
        color = android.graphics.Color.parseColor("#4A2A32")
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (symptomData.isEmpty()) {
            Text(
                text = "暂无症状数据",
                color = Color(0xFF7D5260),
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
            )
            return
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2
            val centerY = height / 2
            val radius = minOf(width, height) * 0.35f

            val total = symptomData.values.sum()
            if (total == 0) return@Canvas

            val colors = listOf(
                Color(0xFFED9EBC), // 粉色
                Color(0xFF7D5260), // 深粉色
                Color(0xFFF5D8E4), // 浅粉色
                Color(0xFFED9EBC), // 粉色
                Color(0xFF7D5260), // 深粉色
                Color(0xFFF5D8E4)  // 浅粉色
            )

            var startAngle = -90f // 从顶部开始

            symptomData.entries.forEachIndexed { index, entry ->
                val symptom = entry.key
                val count = entry.value
                val angle = 360f * count / total
                val color = colors[index % colors.size]

                // 绘制扇形
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = angle,
                    useCenter = true,
                    topLeft = androidx.compose.ui.geometry.Offset(centerX - radius, centerY - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                )

                // 绘制图例
                val legendX = 40.dp.toPx()
                val legendY = 40.dp.toPx() + index * 30.dp.toPx()
                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(legendX, legendY),
                    size = androidx.compose.ui.geometry.Size(15.dp.toPx(), 15.dp.toPx())
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "$symptom: ${count}次",
                    legendX + 20.dp.toPx(),
                    legendY + 12.dp.toPx(),
                    textPaint
                )

                startAngle += angle
            }
        }
    }
}
