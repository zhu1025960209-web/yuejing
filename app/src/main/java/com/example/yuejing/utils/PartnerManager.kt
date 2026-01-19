package com.example.yuejing.utils

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import com.example.yuejing.data.model.*
import com.google.gson.Gson
import com.example.yuejing.utils.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PartnerManager(private val context: Context) {
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("partner_prefs", Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    
    private val KEY_PARTNER_INFO = "partner_info"
    private val KEY_SHARING_SETTINGS = "sharing_settings"
    private val KEY_PREGNANCY_PREP = "pregnancy_preparation"
    private val KEY_PARTNER_MESSAGES = "partner_messages"
    
    // 保存伴侣信息
    fun savePartnerInfo(partnerInfo: PartnerInfo) {
        val json = gson.toJson(partnerInfo)
        sharedPreferences.edit().putString(KEY_PARTNER_INFO, json).apply()
    }
    
    // 获取伴侣信息
    fun getPartnerInfo(): PartnerInfo? {
        val json = sharedPreferences.getString(KEY_PARTNER_INFO, null)
        return if (json != null) {
            try {
                gson.fromJson(json, PartnerInfo::class.java)
            } catch (e: Exception) {
                // 如果反序列化失败，返回null
                null
            }
        } else null
    }
    
    // 保存共享设置
    fun saveSharingSettings(settings: SharingSettings) {
        val json = gson.toJson(settings)
        sharedPreferences.edit().putString(KEY_SHARING_SETTINGS, json).apply()
    }
    
    // 获取共享设置
    fun getSharingSettings(): SharingSettings {
        // 从女生的提醒设置文件中读取设置
        val reminderSettingsFile = File(context.filesDir, "reminder_settings.json")
        var periodReminder = true
        var ovulationReminder = true
        var fertileReminder = true
        
        try {
            if (reminderSettingsFile.exists()) {
                val settingsJson = reminderSettingsFile.readText()
                if (settingsJson.isNotBlank()) {
                    val settings = kotlinx.serialization.json.Json.decodeFromString<Map<String, Boolean>>(settingsJson)
                    periodReminder = settings["period_reminder"] ?: true
                    ovulationReminder = settings["ovulation_reminder"] ?: true
                    fertileReminder = settings["fertile_reminder"] ?: true
                }
            }
        } catch (e: Exception) {
            // 读取失败时使用默认值
        }
        
        // 返回基于女生提醒设置的共享设置
        return SharingSettings(
            sharePeriodReminders = periodReminder,
            shareOvulationReminders = ovulationReminder,
            shareFertileReminders = fertileReminder,
            shareMoodSymptoms = false, // 这些设置目前未被使用
            shareIntimacy = false,      // 这些设置目前未被使用
            partnerViewEnabled = true
        )
    }
    
    // 保存孕期准备信息
    fun savePregnancyPreparation(prep: PregnancyPreparation) {
        val json = gson.toJson(prep)
        sharedPreferences.edit().putString(KEY_PREGNANCY_PREP, json).apply()
    }
    
    // 获取孕期准备信息
    fun getPregnancyPreparation(): PregnancyPreparation? {
        val json = sharedPreferences.getString(KEY_PREGNANCY_PREP, null)
        return if (json != null) {
            try {
                gson.fromJson(json, PregnancyPreparation::class.java)
            } catch (e: Exception) {
                // 如果反序列化失败，返回null
                null
            }
        } else null
    }
    
    // 保存伴侣消息
    fun savePartnerMessages(messages: List<PartnerMessage>) {
        val json = gson.toJson(messages)
        sharedPreferences.edit().putString(KEY_PARTNER_MESSAGES, json).apply()
    }
    
    // 获取伴侣消息
    fun getPartnerMessages(): List<PartnerMessage> {
        val json = sharedPreferences.getString(KEY_PARTNER_MESSAGES, null)
        return if (json != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<PartnerMessage>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                // 如果反序列化失败，返回空列表
                emptyList()
            }
        } else emptyList()
    }
    
    // 发送伴侣消息
    fun sendPartnerMessage(content: String): PartnerMessage {
        // 获取当前用户性别
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val userGender = sharedPreferences.getString("user_gender", "female") ?: "female" // 默认值为female
        
        // 根据用户性别设置senderId和senderGender
        val senderId = if (userGender == "female") "female" else "male"
        val receiverId = if (userGender == "female") "male" else "female"
        val senderGender = userGender // 直接使用用户性别作为发送者性别
        
        val messages = getPartnerMessages()
        val newMessage = PartnerMessage(
            id = "msg_${System.currentTimeMillis()}",
            senderId = senderId,
            receiverId = receiverId,
            content = content,
            timestamp = System.currentTimeMillis().toString(),
            senderGender = senderGender // 添加发送者性别字段
        )
        val updatedMessages = messages + newMessage
        savePartnerMessages(updatedMessages)
        
        // 自动同步到云端
        CoroutineScope(Dispatchers.IO).launch {
            val syncManager = SyncManager(context)
            syncManager.syncPartnerSharingState()
        }
        
        return newMessage
    }
    
    // 标记消息为已读
    fun markMessageAsRead(messageId: String) {
        val messages = getPartnerMessages()
        val updatedMessages = messages.map {
            if (it.id == messageId) it.copy(isRead = true) else it
        }
        savePartnerMessages(updatedMessages)
    }
    
    // 连接伴侣
    fun connectPartner(partnerName: String): PartnerInfo {
        val partnerInfo = PartnerInfo(
            id = "partner_${System.currentTimeMillis()}",
            name = partnerName,
            isConnected = true,
            lastSyncTime = System.currentTimeMillis().toString()
        )
        savePartnerInfo(partnerInfo)
        return partnerInfo
    }
    
    // 断开伴侣连接
    fun disconnectPartner() {
        val partnerInfo = getPartnerInfo()?.copy(
            isConnected = false,
            lastSyncTime = System.currentTimeMillis().toString()
        )
        if (partnerInfo != null) {
            savePartnerInfo(partnerInfo)
        }
    }
    
    // 检查是否已连接伴侣
    fun isPartnerConnected(): Boolean {
        // 从app_prefs获取用户性别（匹配码）
        val appSharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val userGender = appSharedPreferences.getString("user_gender", null)
        
        // 如果用户已输入匹配码（性别已设置），则认为已绑定
        return userGender != null && userGender.isNotEmpty()
    }
    
    // 重置所有伴侣数据
    fun resetAllPartnerData() {
        sharedPreferences.edit().clear().apply()
    }
    
    // 获取伴侣共享状态
    fun getPartnerSharingState(): PartnerSharingState {
        return PartnerSharingState(
            partnerInfo = getPartnerInfo(),
            sharingSettings = getSharingSettings(),
            messages = getPartnerMessages(),
            pregnancyPreparation = getPregnancyPreparation()
        )
    }
}
