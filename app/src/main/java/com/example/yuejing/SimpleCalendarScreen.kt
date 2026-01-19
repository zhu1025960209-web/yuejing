package com.example.yuejing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

@Composable
fun SimpleCalendarScreen() {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "经期记录应用 - Kotlin 版本",
            fontSize = 20.sp
        )
    }
}