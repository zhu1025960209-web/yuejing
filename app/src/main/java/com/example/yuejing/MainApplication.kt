package com.example.yuejing

import android.app.Application
import android.util.Log
import com.example.yuejing.utils.LocationManager
import com.example.yuejing.utils.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("MainApplication", "应用启动，初始化同步服务...")
        
        // 1. 初始化LocationManager
        LocationManager.getInstance().init(this)
        
        // 2. 设置定期同步任务
        val syncManager = SyncManager(this)
        syncManager.setupPeriodicSync()
        Log.d("MainApplication", "定期同步任务已设置")
        
        // 3. 立即执行一次同步，获取最新数据
        GlobalScope.launch(Dispatchers.IO) {
            Log.d("MainApplication", "立即执行一次同步...")
            syncManager.syncPartnerSharingState()
            syncManager.syncLocationState()
            Log.d("MainApplication", "立即同步完成")
        }
    }
}