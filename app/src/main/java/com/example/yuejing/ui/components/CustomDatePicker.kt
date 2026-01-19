package com.example.yuejing.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDatePicker(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(initialDate) }
    var currentMonth by remember { mutableStateOf(initialDate) }
    
    val formatter = DateTimeFormatter.ofPattern("yyyy年MM月")
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    
    // 获取当前月份的第一天和最后一天
    val firstDayOfMonth = currentMonth.withDayOfMonth(1)
    val lastDayOfMonth = currentMonth.withDayOfMonth(currentMonth.lengthOfMonth())
    
    // 计算第一天是星期几（1=周一，...，7=周日）
    val dayOfWeekValue = firstDayOfMonth.dayOfWeek.value
    
    // 转换为0=周日，1=周一，...，6=周六
    val firstDayOfWeek = if (dayOfWeekValue == 7) 0 else dayOfWeekValue
    
    // 生成日期列表
    val days = mutableListOf<Int?>()
    
    // 添加月份开始前的空日期
    for (i in 0 until firstDayOfWeek) {
        days.add(null)
    }
    
    // 添加当月日期
    for (i in 1..currentMonth.lengthOfMonth()) {
        days.add(i)
    }
    
    // 星期标题
    val weekdays = listOf("日", "一", "二", "三", "四", "五", "六")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "选择日期") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                // 月份导航
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                        Text(text = "<", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(text = currentMonth.format(formatter), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                        Text(text = ">", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                // 星期标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekdays.forEach {
                        Text(
                            text = it,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7D5260),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                // 日期网格
                Column {
                    for (row in 0 until (days.size + 6) / 7) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 7) {
                                val index = row * 7 + col
                                val day = if (index < days.size) days[index] else null
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (day != null) {
                                        val date = currentMonth.withDayOfMonth(day)
                                        val isSelected = date == selectedDate
                                        val isToday = date == LocalDate.now()
                                        
                                        // 使用点击区域而不是Button，避免样式问题
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    color = if (isSelected) Color(0xFFED9EBC) else if (isToday) Color(0xFFF5D8E4) else Color.Transparent,
                                                    shape = MaterialTheme.shapes.medium
                                                )
                                                .clickable {
                                                    selectedDate = date
                                                    onDateSelected(date)
                                                    onDismiss()
                                                }
                                        ) {
                                            Text(
                                                text = day.toString(),
                                                fontSize = 14.sp,
                                                color = Color(0xFF7D5260),
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onDateSelected(selectedDate)
                onDismiss()
            }) {
                Text(text = "确定")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(text = "取消")
            }
        }
    )
}