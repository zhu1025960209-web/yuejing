package com.example.yuejing.data.model

// 伴侣信息
data class PartnerInfo(
    val id: String? = null,
    val name: String = "",
    val height: String? = null,
    val weight: String? = null,
    val isConnected: Boolean = false,
    val lastSyncTime: String? = null
)

// 共享设置
data class SharingSettings(
    val sharePeriodReminders: Boolean = true,
    val shareOvulationReminders: Boolean = true,
    val shareFertileReminders: Boolean = true,
    val shareMoodSymptoms: Boolean = false,
    val shareIntimacy: Boolean = false,
    val partnerViewEnabled: Boolean = true
)

// 伴侣消息
data class PartnerMessage(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: String,
    val senderGender: String, // "female" or "male"
    val isRead: Boolean = false
)

// 孕期准备信息
data class PregnancyPreparation(
    val id: String? = null,
    val preparationItems: List<PreparationItem> = emptyList(),
    val targetConceptionDate: String? = null,
    val notes: String? = ""
)

// 孕期准备项目
data class PreparationItem(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean = false,
    val deadline: String? = null,
    val assignedTo: String? = null // "user" or "partner"
)

// 伴侣共享状态
data class PartnerSharingState(
    val partnerInfo: PartnerInfo? = null,
    val sharingSettings: SharingSettings = SharingSettings(),
    val messages: List<PartnerMessage> = emptyList(),
    val pregnancyPreparation: PregnancyPreparation? = null
)
