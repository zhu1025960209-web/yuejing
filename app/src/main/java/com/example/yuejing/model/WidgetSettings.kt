package com.example.yuejing.model

import kotlinx.serialization.Serializable

// 小部件设置数据类
@Serializable
data class WidgetSettings(
    val background_name: String = "粉色 (默认)",
    val background_color: Int = 0xFFF5D8E4.toInt(),
    val background_image_path: String? = null
)
