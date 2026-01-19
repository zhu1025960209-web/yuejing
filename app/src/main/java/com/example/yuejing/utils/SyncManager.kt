package com.example.yuejing.utils

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.example.yuejing.RecordManager
import com.example.yuejing.data.model.LocationData
import com.example.yuejing.data.model.PartnerLocationState
import com.example.yuejing.data.model.PeriodRecord
import com.example.yuejing.data.model.PartnerSharingState
import com.example.yuejing.utils.PartnerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SyncManager(private val context: Context) {
    // 自定义后端不需要Gist ID，返回空字符串
    private val DEFAULT_GIST_ID = ""
    
    // 保存Gist ID - 自定义后端不需要
    fun saveGistId(gistId: String) {
        // 什么也不做
    }
    
    // 获取Gist ID - 自定义后端不需要，返回空字符串
    fun getGistId(): String? {
        return DEFAULT_GIST_ID
    }
    
    // 清除Gist ID - 自定义后端不需要
    fun clearGistId() {
        // 什么也不做
    }
    
    // 同步记录
    suspend fun syncRecords(): Boolean {
        val gistSync = GistSync()
        
        // 检查令牌是否有效
        if (!gistSync.isTokenValid()) {
            showToast("GitHub令牌无效，请检查配置")
            return false
        }
        
        val gistId = getGistId()
        
        try {
            // 1. 加载本地记录
            val localRecords = RecordManager.loadRecords(context)
            
            // 2. 处理同步逻辑
            val finalRecords: List<PeriodRecord>
            val newGistId: String
            
            if (gistId.isNullOrEmpty()) {
                // 首次同步，直接上传本地记录
                newGistId = gistSync.uploadRecords(localRecords)
                saveGistId(newGistId)
                showToast("同步成功，已创建新的Gist")
                finalRecords = localRecords
            } else {
                // 非首次同步，先下载远程记录
                val remoteRecords = gistSync.downloadRecords(gistId)
                
                if (localRecords.isEmpty()) {
                    // 本地无数据，使用远程数据
                    finalRecords = remoteRecords
                    showToast("同步成功，已从服务器获取数据")
                } else {
                    // 本地有数据，合并本地和远程数据
                    finalRecords = mergeRecords(localRecords, remoteRecords)
                    
                    // 上传合并后的数据
                    gistSync.uploadRecords(finalRecords, gistId)
                    showToast("同步成功，已合并本地和服务器数据")
                }
            }
            
            // 3. 保存最终记录到本地
            RecordManager.saveRecords(context, finalRecords)
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("同步失败: ${e.message}")
            return false
        }
    }
    
    // 上传记录
    suspend fun uploadRecords(): Boolean {
        val gistSync = GistSync()
        
        // 检查令牌是否有效
        if (!gistSync.isTokenValid()) {
            showToast("GitHub令牌无效，请检查配置")
            return false
        }
        
        val gistId = getGistId()
        
        try {
            // 加载本地记录
            val localRecords = RecordManager.loadRecords(context)
            
            // 上传记录到Gist
            val newGistId = gistSync.uploadRecords(localRecords, gistId)
            if (gistId.isNullOrEmpty()) {
                // 首次同步，保存Gist ID
                saveGistId(newGistId)
                showToast("上传成功，已创建新的Gist")
            } else {
                showToast("上传成功！")
            }
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("上传失败: ${e.message}")
            return false
        }
    }
    
    // 下载记录
    suspend fun downloadRecords(showToast: Boolean = false): Boolean {
        val gistSync = GistSync()
        
        // 检查令牌是否有效
        if (!gistSync.isTokenValid()) {
            if (showToast) {
                showToast("GitHub令牌无效，请检查配置")
            }
            return false
        }
        
        val gistId = getGistId()
        
        if (gistId.isNullOrEmpty()) {
            if (showToast) {
                showToast("未找到Gist ID，请先上传数据")
            }
            return false
        }
        
        try {
            println("开始下载记录...")
            // 下载最新记录
            val remoteRecords = gistSync.downloadRecords(gistId)
            println("下载完成，获取到 ${remoteRecords.size} 条记录")
            
            // 打印下载的记录
            remoteRecords.forEachIndexed { index, record ->
                println("下载的记录 $index: $record")
            }
            
            // 更新本地记录
            println("开始保存记录到本地数据库...")
            val saveSuccess = RecordManager.saveRecords(context, remoteRecords)
            println("保存记录结果: $saveSuccess")
            
            // 验证保存结果
            val savedRecords = RecordManager.loadRecords(context)
            println("保存后从数据库加载到 ${savedRecords.size} 条记录")
            
            // 同时下载伴侣共享状态
            downloadPartnerSharingState(showToast)
            
            if (showToast) {
                showToast("下载成功！")
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            
            // 检查是否是 404 错误
            if (e.message?.contains("404") == true) {
                // 使用硬编码的Gist ID，不清除本地保存的Gist ID
                if (showToast) {
                    showToast("Gist 不存在，请检查Gist ID是否正确")
                }
            } else if (showToast) {
                // 处理null异常信息
                val errorMessage = e.message ?: "未知错误"
                showToast("下载失败: $errorMessage")
            }
            
            return false
        }
    }
    
    // 同步伴侣共享状态
    suspend fun syncPartnerSharingState(): Boolean {
        val gistSync = GistSync()
        
        // 检查令牌是否有效
        if (!gistSync.isTokenValid()) {
            showToast("GitHub令牌无效，请检查配置")
            return false
        }
        
        val gistId = getGistId()
        
        if (gistId.isNullOrEmpty()) {
            showToast("未找到Gist ID，请先上传月经记录")
            return false
        }
        
        try {
            // 1. 加载本地伴侣共享状态
            val partnerManager = PartnerManager(context)
            val localSharingState = partnerManager.getPartnerSharingState()
            
            // 2. 先下载远程状态
            val remoteSharingState = gistSync.downloadPartnerSharingState(gistId)
            
            // 3. 合并状态（基于时间戳）
            val finalSharingState = mergePartnerSharingState(localSharingState, remoteSharingState)
            
            // 4. 上传合并后的状态到现有的Gist
            gistSync.uploadPartnerSharingState(finalSharingState, gistId)
            showToast("伴侣消息同步成功")
            
            // 5. 保存最终状态到本地
            savePartnerSharingState(finalSharingState)
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("同步失败: ${e.message}")
            return false
        }
    }
    
    // 上传伴侣共享状态
    suspend fun uploadPartnerSharingState(): Boolean {
        val gistSync = GistSync()
        
        // 检查令牌是否有效
        if (!gistSync.isTokenValid()) {
            showToast("GitHub令牌无效，请检查配置")
            return false
        }
        
        val gistId = getGistId()
        
        try {
            // 加载本地伴侣共享状态
            val partnerManager = PartnerManager(context)
            val localSharingState = partnerManager.getPartnerSharingState()
            
            // 上传状态到Gist
            val newGistId = gistSync.uploadPartnerSharingState(localSharingState, gistId)
            if (gistId.isNullOrEmpty()) {
                // 首次同步，保存Gist ID
                saveGistId(newGistId)
                showToast("上传成功，已创建新的Gist")
            } else {
                showToast("上传成功！")
            }
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("上传失败: ${e.message}")
            return false
        }
    }
    
    // 下载伴侣共享状态
    suspend fun downloadPartnerSharingState(showToast: Boolean = false): Boolean {
        val gistSync = GistSync()
        
        // 检查令牌是否有效
        if (!gistSync.isTokenValid()) {
            if (showToast) {
                showToast("GitHub令牌无效，请检查配置")
            }
            return false
        }
        
        val gistId = getGistId()
        
        if (gistId.isNullOrEmpty()) {
            if (showToast) {
                showToast("未找到Gist ID，请先上传数据")
            }
            return false
        }
        
        try {
            println("开始下载伴侣共享状态...")
            // 下载最新状态
            val remoteSharingState = gistSync.downloadPartnerSharingState(gistId)
            println("下载完成")
            
            // 更新本地状态
            savePartnerSharingState(remoteSharingState)
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            
            // 检查是否是 404 错误
            if (e.message?.contains("404") == true) {
                // 使用硬编码的Gist ID，不清除本地保存的Gist ID
                if (showToast) {
                    showToast("Gist 不存在，请检查Gist ID是否正确")
                }
            } else if (showToast) {
                // 处理null异常信息
                val errorMessage = e.message ?: "未知错误"
                showToast("下载失败: $errorMessage")
            }
            
            return false
        }
    }
    
    // 合并本地和远程伴侣共享状态
    private fun mergePartnerSharingState(localState: PartnerSharingState, remoteState: PartnerSharingState): PartnerSharingState {
        // 合并消息（保留所有消息，去重）
        val mergedMessages = (localState.messages + remoteState.messages)
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
        
        // 其他字段可以根据需要合并
        return PartnerSharingState(
            partnerInfo = remoteState.partnerInfo ?: localState.partnerInfo,
            sharingSettings = remoteState.sharingSettings,
            messages = mergedMessages,
            pregnancyPreparation = remoteState.pregnancyPreparation ?: localState.pregnancyPreparation
        )
    }
    
    // 保存伴侣共享状态到本地
    private fun savePartnerSharingState(sharingState: PartnerSharingState) {
        val partnerManager = PartnerManager(context)
        
        // 保存各个部分
        sharingState.partnerInfo?.let { partnerManager.savePartnerInfo(it) }
        partnerManager.saveSharingSettings(sharingState.sharingSettings)
        partnerManager.savePartnerMessages(sharingState.messages)
        sharingState.pregnancyPreparation?.let { partnerManager.savePregnancyPreparation(it) }
    }
    
    // 同步位置状态
    suspend fun syncLocationState(): Boolean {
        val gistSync = GistSync()
        
        // 检查令牌是否有效
        if (!gistSync.isTokenValid()) {
            showToast("GitHub令牌无效，请检查配置")
            return false
        }
        
        val gistId = getGistId()
        
        if (gistId.isNullOrEmpty()) {
            showToast("未找到Gist ID，请先上传月经记录")
            return false
        }
        
        try {
            // 在IO协程中执行网络操作
            return withContext(Dispatchers.IO) {
                try {
                    // 1. 加载本地位置状态
                    val locationManager = LocationDataManager(context)
                    val localLocationState = locationManager.getPartnerLocationState()
                    
                    // 2. 先下载远程状态
                    val remoteLocationState = gistSync.downloadLocationData(gistId)
                    
                    // 3. 合并状态（基于时间戳）
                    val finalLocationState = mergeLocationState(localLocationState, remoteLocationState)
                    
                    // 4. 上传合并后的完整状态到现有的Gist（使用新方法，单次API调用）
                    gistSync.uploadLocationState(finalLocationState, gistId)
                    
                    // 5. 保存最终状态到本地
                    locationManager.savePartnerLocationState(finalLocationState)
                    
                    withContext(Dispatchers.Main) {
                        showToast("位置同步成功")
                    }
                    
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        showToast("位置同步失败: ${e.message}")
                    }
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("位置同步失败: ${e.message}")
            return false
        }
    }
    
    // 上传位置数据
    suspend fun uploadLocationData(locationData: LocationData): Boolean {
        val gistSync = GistSync()
        val locationManager = LocationDataManager(context)
        
        // 检查令牌是否有效
        if (!gistSync.isTokenValid()) {
            showToast("GitHub令牌无效，请检查配置")
            return false
        }
        
        val gistId = getGistId()
        
        if (gistId.isNullOrEmpty()) {
            showToast("未找到Gist ID，请先上传月经记录")
            return false
        }
        
        try {
            // 1. 数据验证
            if (locationData.latitude == 0.0 && locationData.longitude == 0.0) {
                showToast("无效的位置数据，无法上传")
                return false
            }
            
            if (locationData.timestamp.isBlank()) {
                showToast("无效的时间戳，无法上传")
                return false
            }
            
            // 验证gender字段
            if (locationData.gender !in listOf("female", "male")) {
                showToast("无效的性别信息，无法上传")
                return false
            }
            
            // 2. 更新本地位置
            locationManager.updateUserLocation(locationData)
            
            // 3. 在IO协程中执行网络操作
            return withContext(Dispatchers.IO) {
                try {
                    // 上传位置数据到Gist
                    gistSync.uploadLocationData(locationData, gistId)
                    
                    withContext(Dispatchers.Main) {
                        showToast("位置上传成功")
                    }
                    return@withContext true
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        // 改进错误信息处理，避免显示null
                        val errorMessage = e.message ?: "未知错误"
                        showToast("位置上传失败: $errorMessage")
                    }
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 改进错误信息处理，避免显示null
            val errorMessage = e.message ?: "未知错误"
            showToast("位置上传失败: $errorMessage")
            return false
        }
    }
    
    // 下载位置数据
    suspend fun downloadLocationData(): PartnerLocationState? {
        val gistSync = GistSync()
        val locationManager = LocationDataManager(context)
        
        // 检查令牌是否有效
        if (!gistSync.isTokenValid()) {
            showToast("GitHub令牌无效，请检查配置")
            return null
        }
        
        val gistId = getGistId()
        
        if (gistId.isNullOrEmpty()) {
            showToast("未找到Gist ID，请先上传数据")
            return null
        }
        
        try {
            // 1. 下载最新位置数据
            val remoteLocationState = gistSync.downloadLocationData(gistId)
            
            // 2. 加载本地位置状态
            val localLocationState = locationManager.getPartnerLocationState()
            
            // 3. 合并状态
            val finalLocationState = mergeLocationState(localLocationState, remoteLocationState)
            
            // 4. 保存到本地
            locationManager.savePartnerLocationState(finalLocationState)
            
            showToast("位置下载成功")
            return finalLocationState
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("位置下载失败: ${e.message}")
            return null
        }
    }
    
    // 合并位置状态（基于时间戳）
    private fun mergeLocationState(localState: PartnerLocationState, remoteState: PartnerLocationState): PartnerLocationState {
        // 合并女生位置
        val mergedFemaleLocation = when {
            localState.femaleLocation == null -> remoteState.femaleLocation
            remoteState.femaleLocation == null -> localState.femaleLocation
            compareTimestamps(remoteState.femaleLocation.timestamp, localState.femaleLocation.timestamp) -> remoteState.femaleLocation
            else -> localState.femaleLocation
        }
        
        // 合并男生位置
        val mergedMaleLocation = when {
            localState.maleLocation == null -> remoteState.maleLocation
            remoteState.maleLocation == null -> localState.maleLocation
            compareTimestamps(remoteState.maleLocation.timestamp, localState.maleLocation.timestamp) -> remoteState.maleLocation
            else -> localState.maleLocation
        }
        
        return PartnerLocationState(
            femaleLocation = mergedFemaleLocation,
            maleLocation = mergedMaleLocation
        )
    }
    
    // 合并本地和远程记录，基于时间戳
    private fun mergeRecords(localRecords: List<PeriodRecord>, remoteRecords: List<PeriodRecord>): List<PeriodRecord> {
        val mergedMap = mutableMapOf<String, PeriodRecord>()
        
        // 添加所有本地记录
        localRecords.forEach { record ->
            if (record.id != null) {
                mergedMap[record.id] = record
            }
        }
        
        // 添加或更新远程记录（如果远程记录更新）
        remoteRecords.forEach { remoteRecord ->
            if (remoteRecord.id != null) {
                val localRecord = mergedMap[remoteRecord.id]
                if (localRecord == null || compareTimestamps(remoteRecord.timestamp, localRecord.timestamp)) {
                    mergedMap[remoteRecord.id] = remoteRecord
                }
            }
        }
        
        return mergedMap.values.toList().sortedBy { it.timestamp }
    }
    
    // 比较时间戳，返回 true 如果 remoteTimestamp 比 localTimestamp 更新
    private fun compareTimestamps(remoteTimestamp: String?, localTimestamp: String?): Boolean {
        if (remoteTimestamp == null) return false
        if (localTimestamp == null) return true
        
        try {
            val remoteTime = remoteTimestamp.toLong()
            val localTime = localTimestamp.toLong()
            return remoteTime > localTime
        } catch (e: NumberFormatException) {
            return false
        }
    }
    
    // 显示Toast
    private fun showToast(message: String) {
        // 在主线程中显示Toast
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    // 设置定期同步任务
    fun setupPeriodicSync() {
        // 创建定期同步请求，每15分钟执行一次
        val periodicSyncRequest = PeriodicWorkRequest.Builder(
            SyncWorker::class.java,
            15,
            TimeUnit.MINUTES
        )
            .build()
        
        // 确保只有一个同步任务在运行
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync_work",
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicSyncRequest
        )
    }
    
    // 取消定期同步任务
    fun cancelPeriodicSync() {
        WorkManager.getInstance(context).cancelUniqueWork("sync_work")
    }
    
    // 立即执行一次同步
    fun runImmediateSync() {
        val syncRequest = PeriodicWorkRequest.Builder(
            SyncWorker::class.java,
            15,
            TimeUnit.MINUTES
        )
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync_work",
            ExistingPeriodicWorkPolicy.REPLACE,
            syncRequest
        )
    }
}