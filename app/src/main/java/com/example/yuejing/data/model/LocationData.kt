package com.example.yuejing.data.model

// 位置数据
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val address: String = "", // 默认值为空字符串
    val timestamp: String,
    val gender: String // "female" or "male"
)

// 双方位置信息
data class PartnerLocationState(
    val femaleLocation: LocationData? = null,
    val maleLocation: LocationData? = null
)