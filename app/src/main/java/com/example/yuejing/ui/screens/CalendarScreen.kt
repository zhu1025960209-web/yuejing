package com.example.yuejing.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CalendarScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F2F7))
    ) {
        // 顶部标题栏
        AnnualHeader()

        // 月份导航
        MonthlyNavigation()

        // 星期标题
        WeekHeader()

        // 日历网格
        CalendarGrid()
    }
}

@Composable
fun AnnualHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
    ) {
        Text(
            text = "2026年1月",
            color = Color(0xFFED9EBC),
            fontSize = 22.sp
        )
    }
}

@Composable
fun MonthlyNavigation() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(
            text = "月份导航",
            color = Color(0xFF5A2A47),
            fontSize = 16.sp
        )
    }
}

@Composable
fun WeekHeader() {
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
fun CalendarGrid() {
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