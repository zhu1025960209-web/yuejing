package com.example.yuejing.data.model

data class MenstrualCycle(
    val id: String? = null,
    val type: PeriodEventType,
    val startDate: String? = null,
    val endDate: String? = null,
    val date: String? = null,
    val mood: String? = null,
    val symptoms: List<String> = emptyList(),
    val intimacyType: String? = null,
    val note: String? = "",
    val timestamp: String? = null
)

enum class PeriodEventType {
    PERIOD,
    MOOD_SYMPTOM,
    INTIMACY
}