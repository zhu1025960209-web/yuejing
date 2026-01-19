package com.example.yuejing.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        try {
            Log.d("SyncWorker", "开始自动同步数据...")
            
            // 1. 同步伴侣消息
            val syncManager = SyncManager(applicationContext)
            syncManager.syncPartnerSharingState()
            Log.d("SyncWorker", "伴侣消息同步完成")
            
            // 2. 同步位置数据
            syncManager.syncLocationState()
            Log.d("SyncWorker", "位置数据同步完成")
            
            return Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "自动同步失败: ${e.message}")
            e.printStackTrace()
            return Result.retry()
        }
    }
}