package com.example.yuejing.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.yuejing.data.model.LocationData
import com.example.yuejing.utils.SyncManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale

/**
 * 定位管理类：封装定位相关的逻辑
 */
class LocationManager private constructor() {
    private var locationListener: LocationListener? = null
    private var androidLocationManager: AndroidLocationManager? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    
    companion object {
        @Volatile
        private var instance: LocationManager? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(): LocationManager {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = LocationManager()
                    }
                }
            }
            return instance!!
        }
    }
    
    /**
     * 初始化定位服务
     */
    fun init(context: Context) {
        androidLocationManager = context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    }
    
    /**
     * 检查定位权限
     */
    fun hasLocationPermission(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 检查Google Play Services是否可用
     */
    fun isGooglePlayServicesAvailable(context: Context): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        return resultCode == ConnectionResult.SUCCESS
    }
    
    /**
     * 请求定位权限
     */
    fun requestLocationPermission(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            requestCode
        )
    }
    
    /**
     * 获取当前位置
     */
    fun getCurrentLocation(context: Context, callback: (Location?) -> Unit) {
        try {
            // 检查权限
            if (!hasLocationPermission(context)) {
                Log.e("LocationManager", "Location permission not granted")
                callback(null)
                return
            }
            
            // 检查定位服务是否可用
            val gpsEnabled = androidLocationManager!!.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)
            val networkEnabled = androidLocationManager!!.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)
            
            if (!gpsEnabled && !networkEnabled) {
                Log.e("LocationManager", "Location providers disabled")
                callback(null)
                return
            }
            
            // 检查Google Play Services是否可用
            if (isGooglePlayServicesAvailable(context)) {
                Log.d("LocationManager", "Google Play Services可用，使用FusedLocationProviderClient获取当前位置")
                // 使用FusedLocationProviderClient获取当前位置
                try {
                    fusedLocationClient?.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY, // 高优先级，获取最准确的位置
                        null
                    )?.addOnSuccessListener {location ->
                        if (location != null) {
                            callback(location)
                            Log.d("LocationManager", "成功获取当前位置: ${location.latitude}, ${location.longitude}")
                        } else {
                            // 如果FusedLocationProviderClient获取不到位置，尝试使用传统方法
                            Log.d("LocationManager", "FusedLocationProviderClient获取不到位置，尝试使用传统方法")
                            getLocationUsingTraditionalMethod(context, callback)
                        }
                    }?.addOnFailureListener {exception ->
                        Log.e("LocationManager", "FusedLocationProviderClient获取位置失败: ${exception.message}")
                        // 如果FusedLocationProviderClient获取失败，尝试使用传统方法
                        getLocationUsingTraditionalMethod(context, callback)
                    }
                } catch (e: Exception) {
                    Log.e("LocationManager", "FusedLocationProviderClient获取位置异常: ${e.message}")
                    // 如果FusedLocationProviderClient发生异常，尝试使用传统方法
                    getLocationUsingTraditionalMethod(context, callback)
                }
            } else {
                Log.d("LocationManager", "Google Play Services不可用，使用传统方法获取当前位置")
                // Google Play Services不可用，直接使用传统方法获取位置
                getLocationUsingTraditionalMethod(context, callback)
            }
            
        } catch (e: SecurityException) {
            Log.e("LocationManager", "Location permission not granted: ${e.message}")
            callback(null)
        } catch (e: Exception) {
            Log.e("LocationManager", "Failed to get location: ${e.message}")
            callback(null)
        }
    }
    
    /**
     * 使用传统方法获取位置，作为FusedLocationProviderClient的备选方案
     */
    private fun getLocationUsingTraditionalMethod(context: Context, callback: (Location?) -> Unit) {
        try {
            // 尝试从GPS获取位置
            var location: Location? = androidLocationManager!!.getLastKnownLocation(AndroidLocationManager.GPS_PROVIDER)
            
            // 如果GPS位置不可用，尝试从网络获取位置
            if (location == null) {
                location = androidLocationManager!!.getLastKnownLocation(AndroidLocationManager.NETWORK_PROVIDER)
            }
            
            // 如果网络位置不可用，尝试从被动位置提供者获取位置
            if (location == null) {
                location = androidLocationManager!!.getLastKnownLocation(AndroidLocationManager.PASSIVE_PROVIDER)
            }
            
            // 如果有位置，直接返回
            if (location != null) {
                callback(location)
                return
            }
            
            // 如果没有上次已知位置，注册监听器获取实时位置
            val tempListener = object : LocationListener {
                override fun onLocationChanged(newLocation: Location) {
                    // 获取到位置后立即取消监听
                    androidLocationManager?.removeUpdates(this)
                    callback(newLocation)
                }
                
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                    // 位置提供器状态变化
                }
                
                override fun onProviderEnabled(provider: String) {
                    // 位置提供器启用
                }
                
                override fun onProviderDisabled(provider: String) {
                    // 位置提供器禁用
                }
            }
            
            // 注册位置监听器，获取实时位置
            androidLocationManager?.requestSingleUpdate(AndroidLocationManager.GPS_PROVIDER, tempListener, null)
            androidLocationManager?.requestSingleUpdate(AndroidLocationManager.NETWORK_PROVIDER, tempListener, null)
            
            // 设置超时，防止长时间获取不到位置
            android.os.Handler().postDelayed({
                androidLocationManager?.removeUpdates(tempListener)
                callback(null)
            }, 5000)
            
        } catch (e: SecurityException) {
            Log.e("LocationManager", "Location permission not granted: ${e.message}")
            callback(null)
        } catch (e: Exception) {
            Log.e("LocationManager", "Failed to get location using traditional method: ${e.message}")
            callback(null)
        }
    }
    
    /**
     * 开始监听位置变化
     */
    fun startLocationUpdates(context: Context, minTimeMs: Long = 5000, minDistanceM: Float = 10f, callback: (Location) -> Unit) {
        if (!hasLocationPermission(context)) {
            Log.e("LocationManager", "Location permission not granted")
            return
        }
        
        // 停止之前的位置更新
        stopLocationUpdates()
        
        // 检查Google Play Services是否可用
        if (isGooglePlayServicesAvailable(context)) {
            Log.d("LocationManager", "Google Play Services可用，使用FusedLocationProviderClient")
            // 使用FusedLocationProviderClient获取位置
            try {
                // 创建位置请求，使用高优先级和更短的更新间隔
                val locationRequest = LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, // 高优先级，获取最准确的位置
                    minTimeMs // 更新间隔
                )
                    .setMinUpdateDistanceMeters(minDistanceM) // 最小更新距离
                    .setWaitForAccurateLocation(true) // 等待准确位置
                    .build()
                
                // 创建位置回调
                locationCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        for (location in locationResult.locations) {
                            callback(location)
                            
                            // 自动上传位置数据到云端
                            uploadLocationToCloud(context, location)
                        }
                    }
                }
                
                // 请求位置更新
                fusedLocationClient?.requestLocationUpdates(
                    locationRequest,
                    locationCallback!!,
                    null
                )
                Log.d("LocationManager", "已启动FusedLocationProviderClient位置监听")
            } catch (e: SecurityException) {
                Log.e("LocationManager", "位置权限未授予: ${e.message}")
            } catch (e: Exception) {
                Log.e("LocationManager", "FusedLocationProviderClient启动失败: ${e.message}")
                // 如果FusedLocationProviderClient启动失败，回退到传统方式
                startTraditionalLocationUpdates(context, minTimeMs, minDistanceM, callback)
            }
        } else {
            Log.d("LocationManager", "Google Play Services不可用，使用传统LocationManager")
            // Google Play Services不可用，使用传统LocationManager
            startTraditionalLocationUpdates(context, minTimeMs, minDistanceM, callback)
        }
    }
    
    /**
     * 使用传统LocationManager方式开始监听位置变化
     */
    private fun startTraditionalLocationUpdates(context: Context, minTimeMs: Long, minDistanceM: Float, callback: (Location) -> Unit) {
        // 停止之前的位置更新
        stopLocationUpdates()
        
        // 创建传统的LocationListener
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                callback(location)
                
                // 自动上传位置数据到云端
                uploadLocationToCloud(context, location)
            }
            
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                // 位置提供器状态变化
            }
            
            override fun onProviderEnabled(provider: String) {
                // 位置提供器启用
            }
            
            override fun onProviderDisabled(provider: String) {
                // 位置提供器禁用
            }
        }
        
        // 注册位置监听器
        locationListener?.let {listener ->
            try {
                // 注册GPS位置监听器
                androidLocationManager?.requestLocationUpdates(
                    AndroidLocationManager.GPS_PROVIDER,
                    minTimeMs,
                    minDistanceM,
                    listener
                )
                Log.d("LocationManager", "已启动GPS位置监听")
            } catch (e: SecurityException) {
                Log.e("LocationManager", "GPS位置权限未授予: ${e.message}")
            }
            
            try {
                // 注册网络位置监听器
                androidLocationManager?.requestLocationUpdates(
                    AndroidLocationManager.NETWORK_PROVIDER,
                    minTimeMs,
                    minDistanceM,
                    listener
                )
                Log.d("LocationManager", "已启动网络位置监听")
            } catch (e: SecurityException) {
                Log.e("LocationManager", "网络位置权限未授予: ${e.message}")
            }
        }
    }
    
    /**
     * 自动上传位置数据到云端
     */
    private fun uploadLocationToCloud(context: Context, location: Location) {
        Log.d("LocationManager", "自动上传位置数据到云端")
        
        // 获取当前用户性别
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val userGender = sharedPreferences.getString("user_gender", "female") ?: "female"
        
        // 先获取地址信息，再上传位置数据
        getAddressFromLocation(context, location) {
            val address = it
            
            // 创建LocationData对象，包含地址信息
            val locationData = LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                address = address,
                timestamp = System.currentTimeMillis().toString(),
                gender = userGender
            )
            
            // 在后台协程中上传位置数据
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val syncManager = SyncManager(context)
                    syncManager.uploadLocationData(locationData)
                    Log.d("LocationManager", "位置数据上传成功")
                } catch (e: Exception) {
                    Log.e("LocationManager", "位置数据上传失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * 将经纬度转换为实际地址
     */
    fun getAddressFromLocation(context: Context, location: Location, callback: (String) -> Unit) {
        // 在IO线程中执行地址转换，避免阻塞主线程
        Thread {
            val geocoder = Geocoder(context, Locale.getDefault())
            
            try {
                // 异步获取地址
                val addresses: List<Address>? = geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1 // 只获取一个结果
                )
                
                val result = if (addresses != null && addresses.isNotEmpty()) {
                    val address = addresses[0]
                    // 构建地址字符串
                    buildString {
                        // 国家
                        if (address.countryName != null) append(address.countryName)
                        // 省/州
                        if (address.adminArea != null) append(" " + address.adminArea)
                        // 城市
                        if (address.locality != null) append(" " + address.locality)
                        // 区/县
                        if (address.subLocality != null) append(" " + address.subLocality)
                        // 街道
                        if (address.thoroughfare != null) append(" " + address.thoroughfare)
                        // 门牌号
                        if (address.featureName != null) append(" " + address.featureName)
                    }.trim()
                } else {
                    "无法获取地址信息"
                }
                
                // 切换回主线程调用回调
                android.os.Handler(context.mainLooper).post {
                    callback(result)
                }
            } catch (e: IOException) {
                Log.e("LocationManager", "Geocoder服务不可用: ${e.message}")
                android.os.Handler(context.mainLooper).post {
                    callback("位置服务不可用")
                }
            } catch (e: IllegalArgumentException) {
                Log.e("LocationManager", "无效的经纬度: ${e.message}")
                android.os.Handler(context.mainLooper).post {
                    callback("无效的位置信息")
                }
            } catch (e: Exception) {
                Log.e("LocationManager", "获取地址失败: ${e.message}")
                android.os.Handler(context.mainLooper).post {
                    callback("地址获取失败")
                }
            }
        }.start()
    }
    
    /**
     * 停止监听位置变化
     */
    fun stopLocationUpdates() {
        // 停止传统LocationManager的位置更新
        locationListener?.let {listener ->
            androidLocationManager?.removeUpdates(listener)
            locationListener = null
        }
        
        // 停止FusedLocationProviderClient的位置更新
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
            locationCallback = null
        }
        
        Log.d("LocationManager", "已停止位置监听")
    }
}
