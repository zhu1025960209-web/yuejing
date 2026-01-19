package com.example.yuejing.utils

import android.content.Context
import android.util.Log
import com.example.yuejing.ReminderScheduler
import com.example.yuejing.data.model.SharingSettings
import java.time.LocalDate

class PartnerReminderManager(private val context: Context) {
    companion object {
        private const val TAG = "PartnerReminderManager"
    }
    
    private val partnerManager by lazy { PartnerManager(context) }
    private val reminderScheduler by lazy { ReminderScheduler(context) }
    
    // 为伴侣设置共享提醒
    fun schedulePartnerReminders(
        periodStartDate: LocalDate?, 
        ovulationDate: LocalDate?, 
        fertileStartDate: LocalDate?
    ) {
        // 检查伴侣是否已连接
        if (!partnerManager.isPartnerConnected()) {
            Log.i(TAG, "Partner not connected, skipping partner reminders")
            return
        }
        
        // 获取共享设置
        val sharingSettings = partnerManager.getSharingSettings()
        
        // 检查是否启用了伴侣视图
        if (!sharingSettings.partnerViewEnabled) {
            Log.i(TAG, "Partner view disabled, skipping partner reminders")
            return
        }
        
        // 根据共享设置为伴侣设置提醒
        if (sharingSettings.sharePeriodReminders && periodStartDate != null) {
            schedulePartnerPeriodReminder(periodStartDate)
        }
        
        if (sharingSettings.shareOvulationReminders && ovulationDate != null) {
            schedulePartnerOvulationReminder(ovulationDate)
        }
        
        if (sharingSettings.shareFertileReminders && fertileStartDate != null) {
            schedulePartnerFertileReminder(fertileStartDate)
        }
    }
    
    // 为伴侣设置经期提醒
    private fun schedulePartnerPeriodReminder(periodStartDate: LocalDate) {
        // 这里应该实现伴侣专用的经期提醒
        // 简化版本：使用现有的提醒系统
        Log.i(TAG, "Scheduling partner period reminder for date: $periodStartDate")
        
        // 发送伴侣消息通知
        partnerManager.sendPartnerMessage(
            "亲爱的，她的经期预计在 ${periodStartDate} 开始，请提前做好准备。"
        )
    }
    
    // 为伴侣设置排卵期提醒
    private fun schedulePartnerOvulationReminder(ovulationDate: LocalDate) {
        // 这里应该实现伴侣专用的排卵期提醒
        Log.i(TAG, "Scheduling partner ovulation reminder for date: $ovulationDate")
        
        // 发送伴侣消息通知
        partnerManager.sendPartnerMessage(
            "亲爱的，她的排卵期预计在 ${ovulationDate}，这是最佳受孕时期。"
        )
    }
    
    // 为伴侣设置易孕期提醒
    private fun schedulePartnerFertileReminder(fertileStartDate: LocalDate) {
        // 这里应该实现伴侣专用的易孕期提醒
        Log.i(TAG, "Scheduling partner fertile reminder for date: $fertileStartDate")
        
        // 发送伴侣消息通知
        partnerManager.sendPartnerMessage(
            "亲爱的，她的易孕期预计从 ${fertileStartDate} 开始，请关注她的身体变化。"
        )
    }
    
    // 检查是否需要为伴侣设置提醒
    fun shouldSchedulePartnerReminders(): Boolean {
        return partnerManager.isPartnerConnected() && 
               partnerManager.getSharingSettings().partnerViewEnabled
    }
    
    // 获取当前的共享设置
    fun getSharingSettings(): SharingSettings {
        return partnerManager.getSharingSettings()
    }
    
    // 更新共享设置
    fun updateSharingSettings(settings: SharingSettings) {
        partnerManager.saveSharingSettings(settings)
        Log.i(TAG, "Updated partner sharing settings: $settings")
    }
}
