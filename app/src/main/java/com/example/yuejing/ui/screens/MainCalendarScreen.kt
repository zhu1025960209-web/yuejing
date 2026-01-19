package com.example.yuejing.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainCalendarScreen() {
    var currentDate by remember { mutableStateOf("2026-01-14") }
    
    // 日历界面
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F2F7))
    ) {
        // 顶部标题栏
        CalendarHeader(
            currentDate = currentDate,
            onPrevious = { /* 处理上一个月 */ },
            onNext = { /* 处理下一个月 */ }
        )
        
        // 星期标题
        WeekdaysHeader()
        
        // 日历网格
        CalendarGrid(
            currentDate = currentDate,
            onDateSelected = { date -> /* 处理日期选择 */ }
        )
        
        // 底部状态
        CalendarStatus()
    }
}

@Composable
fun CalendarHeader(
    currentDate: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        // 月份和年份显示
        Text(
            text = "2026年1月",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFED9EBC)
        )
    }
}

@Composable
fun WeekdaysHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        Text(
            text = "日 一 二 三 四 五 六",
            color = Color(0xFF5A2A47),
            fontSize = 14.sp
        )
    }
}

@Composable
fun CalendarGrid(
    currentDate: String,
    onDateSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5D8E4))
    ) {
        Text(
            text = "日历网格",
            color = Color(0xFF5A2A47),
            fontSize = 14.sp
        )
    }
}

@Composable
fun CalendarStatus() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(
            text = "日历状态",
            color = Color(0xFF5A2A47),
            fontSize = 14.sp
        )
    }
}