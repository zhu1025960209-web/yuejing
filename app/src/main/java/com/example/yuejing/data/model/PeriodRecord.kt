package com.example.yuejing.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PeriodRecord(
    val id: String? = null,
    val type: RecordType,
    val startDate: String? = null,
    val endDate: String? = null,
    val date: String? = null,
    val mood: String? = null,
    val symptoms: List<String> = emptyList(),
    val intimacyType: String? = null,
    val note: String? = "",
    val timestamp: String? = null
)