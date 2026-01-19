package com.example.yuejing

import android.content.Context
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.io.File
import com.example.yuejing.BuildConfig
import com.example.yuejing.utils.GistSync
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.yuejing.utils.LocationManager
import com.example.yuejing.data.model.PeriodRecord
import com.example.yuejing.data.model.RecordType
import com.example.yuejing.domain.predictor.CyclePredictor
import com.example.yuejing.ui.components.CycleChart
import com.example.yuejing.ui.components.SymptomChart
import com.example.yuejing.ui.theme.YuejingTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.unit.IntOffset
import com.example.yuejing.ui.components.CustomDatePicker
import java.time.Instant
import java.time.ZoneId

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextAlign
import com.example.yuejing.data.database.SQLiteDatabaseHelper
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.yuejing.ReminderScheduler
import com.example.yuejing.CycleWidgetProvider
import com.example.yuejing.utils.SyncManager
import com.example.yuejing.utils.PartnerManager
import com.example.yuejing.utils.LocationDataManager
import com.example.yuejing.ui.screens.PartnerViewScreen
import com.example.yuejing.ui.screens.PartnerChatScreen
import com.example.yuejing.ui.screens.PregnancyPreparationScreen
import androidx.compose.ui.platform.LocalContext
import com.example.yuejing.data.model.LocationData
import com.example.yuejing.data.model.PartnerLocationState

private const val TAG = "YueJingDB"

class MainActivity : ComponentActivity() {
    // 导出日志功能
    companion object {
        private const val TAG = "MainActivity"
        
        // 导出日志功能 - 静态方法，便于在Composable中调用
        fun exportLogs(context: Context): Boolean {
            return try {
                // 创建日志文件
                val logFile = File(context.getExternalFilesDir(null), "period_tracker_logs.txt")
                
                // 收集日志信息
                val logBuilder = StringBuilder()
                logBuilder.append("=== 月经跟踪应用日志 ===\n")
                logBuilder.append("导出时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")
                logBuilder.append("应用版本: ${BuildConfig.VERSION_NAME}\n")
                logBuilder.append("设备信息: ${Build.MODEL} (${Build.VERSION.RELEASE})\n")
                logBuilder.append("\n")
                
                // 获取应用数据目录下的文件信息
                logBuilder.append("=== 应用数据文件 ===\n")
                val dataDir = context.filesDir
                dataDir.listFiles()?.forEach { file ->
                    logBuilder.append("${file.name}: ${file.length()} bytes\n")
                }
                logBuilder.append("\n")
                
                // 获取SharedPreferences信息
                logBuilder.append("=== SharedPreferences ===\n")
                val prefsNames = listOf("app_prefs", "partner_prefs", "location_prefs")
                prefsNames.forEach { prefName ->
                    val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    logBuilder.append("--- $prefName ---\n")
                    prefs.all.forEach { (key, value) ->
                        // 脱敏处理，避免敏感信息泄露
                        val safeValue = when (key) {
                            "token", "password", "auth" -> "[REDACTED]"
                            else -> value.toString()
                        }
                        logBuilder.append("$key: $safeValue\n")
                    }
                }
                logBuilder.append("\n")
                
                // 获取当前Gist ID
                val syncManager = SyncManager(context)
                val gistId = syncManager.getGistId()
                logBuilder.append("=== Gist信息 ===\n")
                logBuilder.append("Gist ID: $gistId\n")
                
                // 获取令牌状态
                val gistSync = GistSync()
                logBuilder.append("令牌有效: ${gistSync.isTokenValid()}\n")
                logBuilder.append("\n")
                
                // 获取本地位置状态
                val locationManager = LocationDataManager(context)
                val locationState = locationManager.getPartnerLocationState()
                logBuilder.append("=== 位置状态 ===\n")
                logBuilder.append("女性位置: ${locationState.femaleLocation?.address ?: "无"}\n")
                logBuilder.append("男性位置: ${locationState.maleLocation?.address ?: "无"}\n")
                logBuilder.append("\n")
                
                // 获取伴侣消息数量
                val partnerManager = PartnerManager(context)
                val messages = partnerManager.getPartnerMessages()
                logBuilder.append("=== 伴侣消息 ===\n")
                logBuilder.append("消息数量: ${messages.size}\n")
                
                // 写入日志文件
                logFile.writeText(logBuilder.toString())
                
                // 提示用户日志已保存
                Log.d(TAG, "日志已保存到: ${logFile.absolutePath}")
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "导出日志失败: ${e.message}", e)
                false
            }
        }
    }

    // 权限请求注册
    private val requestScheduleExactAlarmPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        Log.d(TAG, "SCHEDULE_EXACT_ALARM permission granted: $it")
    }
    
    // 定位权限请求启动器
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            Log.d(TAG, "Location permission granted")
            // 用户授予权限后，立即启动位置监听
            LocationManager.getInstance().startLocationUpdates(
                this,
                minTimeMs = 30000, // 每30秒更新一次位置
                minDistanceM = 10f // 移动10米就更新
            ) {
                // 位置变化回调
                Log.d(TAG, "位置更新: ${it.latitude}, ${it.longitude}")
            }
        } else {
            Log.d(TAG, "Location permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查并请求权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestScheduleExactAlarmPermissionLauncher.launch(
                android.Manifest.permission.SCHEDULE_EXACT_ALARM
            )
        }
        
        // 初始化定位服务
        LocationManager.getInstance().init(this)
        
        // 请求定位权限
        if (!LocationManager.getInstance().hasLocationPermission(this)) {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        
        // 启动后台自动同步
        CoroutineScope(Dispatchers.IO).launch {
            // 每1分钟自动同步一次数据，确保位置信息及时更新
            while (true) {
                try {
                    val syncManager = SyncManager(this@MainActivity)
                    // 检查是否有Gist ID，只有有Gist ID时才尝试同步
                    if (syncManager.getGistId() != null) {
                        // 1. 下载记录
                        syncManager.downloadRecords()
                        // 2. 同步伴侣消息
                        syncManager.syncPartnerSharingState()
                        // 3. 同步位置数据
                        syncManager.syncLocationState()
                    }
                } catch (e: Exception) {
                    // 忽略同步错误，继续下一次同步
                    Log.e(TAG, "后台同步失败: ${e.message}", e)
                }
                // 每1分钟同步一次（60000毫秒）
                delay(60000)
            }
        }
        
        // 如果有定位权限，启动位置监听
        if (LocationManager.getInstance().hasLocationPermission(this)) {
            Log.d(TAG, "启动位置监听")
            LocationManager.getInstance().startLocationUpdates(
                this,
                minTimeMs = 30000, // 每30秒更新一次位置
                minDistanceM = 10f // 移动10米就更新
            ) {
                // 位置变化回调
                Log.d(TAG, "位置更新: ${it.latitude}, ${it.longitude}")
            }
        }
        
        setContent {
            YuejingTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                
                // 检查是否首次启动，需要输入匹配码
                val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val hasEnteredMatchCode = sharedPreferences.getBoolean("has_entered_match_code", false)
                val userGender = sharedPreferences.getString("user_gender", "")
                
                NavHost(
                    navController = navController,
                    startDestination = if (hasEnteredMatchCode) "home" else "match_code",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("match_code") {
                        MatchCodeScreen(
                            onMatchCodeEntered = {
                                // 标记已输入匹配码
                                sharedPreferences.edit().putBoolean("has_entered_match_code", true).apply()
                                navController.navigate("home") {
                                    popUpTo("match_code") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("home") {
                        HomeScreen(navController)
                    }
                    composable("calendar") {
                        CalendarScreen(navController)
                    }
                    composable("settings") {
                        SettingsScreen(navController, userGender)
                    }
                    composable("charts") {
                        ChartsScreen(navController)
                    }
                    composable("reminders") {
                        RemindersScreen(navController)
                    }
                    composable("stats") {
                        StatsScreen(navController)
                    }
                    composable("advice") {
                        AdviceScreen(navController)
                    }
                    composable("widget_settings") {
                        WidgetSettingsScreen(navController)
                    }
                    // 伴侣共享相关目的地
                    composable("partner_view") {
                        PartnerViewScreen(
                            partnerManager = PartnerManager(LocalContext.current),
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable("partner_chat") {
                        PartnerChatScreen(
                            partnerManager = PartnerManager(LocalContext.current),
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable("pregnancy_preparation") {
                        PregnancyPreparationScreen(
                            partnerManager = PartnerManager(LocalContext.current),
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable("location_sharing") {
                        LocationSharingScreen(navController)
                    }
                }
            }
        }
    }
}

// 显示Toast提示
fun showToast(context: Context, message: String) {
    android.os.Handler(context.mainLooper).post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

// 计算两点之间的距离（单位：米）
fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371e3 // 地球半径，单位：米
    val φ1 = lat1 * Math.PI / 180 // 纬度转换为弧度
    val φ2 = lat2 * Math.PI / 180
    val Δφ = (lat2 - lat1) * Math.PI / 180
    val Δλ = (lon2 - lon1) * Math.PI / 180
    
    val a = Math.sin(Δφ/2) * Math.sin(Δφ/2) +
            Math.cos(φ1) * Math.cos(φ2) *
            Math.sin(Δλ/2) * Math.sin(Δλ/2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
    
    return R * c // 距离，单位：米
}

// 格式化距离显示
fun formatDistance(distance: Double): String {
    return when {
        distance < 1000 -> "${String.format("%.0f", distance)}米"
        else -> "${String.format("%.1f", distance / 1000)}公里"
    }
}

// 位置共享屏幕
@Composable
fun LocationSharingScreen(navController: NavHostController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 状态管理
    val femaleLocation = remember { mutableStateOf<LocationData?>(null) }
    val maleLocation = remember { mutableStateOf<LocationData?>(null) }
    val distance = remember { mutableStateOf<String>("--") }
    val isLoading = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    
    // 获取用户性别
    val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val userGender = sharedPreferences.getString("user_gender", "")
    
    // 初始化位置数据管理器
    val locationManager = remember { LocationDataManager(context) }
    
    // 计算距离
    fun updateDistance() {
        if (femaleLocation.value != null && maleLocation.value != null) {
            val lat1 = femaleLocation.value!!.latitude
            val lon1 = femaleLocation.value!!.longitude
            val lat2 = maleLocation.value!!.latitude
            val lon2 = maleLocation.value!!.longitude
            
            val calculatedDistance = calculateDistance(lat1, lon1, lat2, lon2)
            distance.value = formatDistance(calculatedDistance)
        } else {
            distance.value = "--"
        }
    }
    
    // 更新UI状态
    fun updateUI(locationState: PartnerLocationState) {
        femaleLocation.value = locationState.femaleLocation
        maleLocation.value = locationState.maleLocation
        updateDistance()
    }
    
    // 刷新位置信息
    fun refreshLocation() {
        coroutineScope.launch {
            isLoading.value = true
            errorMessage.value = null
            
            try {
                val syncManager = SyncManager(context)
                
                // 1. 获取当前位置
                LocationManager.getInstance().getCurrentLocation(context) { currentLocation ->
                    if (currentLocation != null) {
                        // 2. 获取地址信息
                        LocationManager.getInstance().getAddressFromLocation(context, currentLocation) { address ->
                            coroutineScope.launch {
                                // 3. 创建LocationData对象
                                // 确保gender是有效的值（female或male）
                                val gender = if (userGender == "female" || userGender == "male") {
                                    userGender
                                } else {
                                    "female" // 默认值
                                }
                                val locationData = LocationData(
                                    currentLocation.latitude,
                                    currentLocation.longitude,
                                    address,
                                    System.currentTimeMillis().toString(),
                                    gender
                                )
                                
                                // 4. 上传位置数据
                                val uploadSuccess = syncManager.uploadLocationData(locationData)
                                if (uploadSuccess) {
                                    // 5. 同步位置状态
                                    val syncSuccess = syncManager.syncLocationState()
                                    if (syncSuccess) {
                                        // 6. 加载本地位置状态
                                        val locationState = locationManager.getPartnerLocationState()
                                        // 7. 更新UI
                                        updateUI(locationState)
                                    } else {
                                        errorMessage.value = "位置同步失败"
                                    }
                                } else {
                                    errorMessage.value = "位置上传失败"
                                }
                                
                                isLoading.value = false
                            }
                        }
                    } else {
                        // 无法获取当前位置，直接同步并加载本地状态
                        coroutineScope.launch {
                            // 尝试同步位置状态
                            syncManager.syncLocationState()
                            // 加载本地位置状态
                            val locationState = locationManager.getPartnerLocationState()
                            updateUI(locationState)
                            isLoading.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                errorMessage.value = "刷新位置失败: ${e.message}"
                isLoading.value = false
            }
        }
    }
    
    // 初始化加载位置
    LaunchedEffect(Unit) {
        // 先加载本地位置状态
        val locationState = locationManager.getPartnerLocationState()
        updateUI(locationState)
        // 然后刷新位置
        refreshLocation()
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF9F4F7)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部导航栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFF5D8E4),
                        contentColor = Color(0xFF7D5260)
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "位置共享 📍",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFED9EBC)
                )
            }
            
            // 距离显示
            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .background(Color(0xFFED9EBC), shape = RoundedCornerShape(16.dp))
                    .padding(32.dp)
            ) {
                Text(
                    text = "我们相距 ${distance.value}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
            
            // 刷新按钮
            Button(
                onClick = { refreshLocation() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFED9EBC),
                    contentColor = Color.White
                ),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                if (isLoading.value) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = if (isLoading.value) "刷新中..." else "刷新位置")
            }
            
            // 错误信息
            errorMessage.value?.let {
                Text(
                    text = it,
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
            
            // 位置信息卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // 女性位置
                LocationCard(
                    title = "她的位置",
                    locationData = femaleLocation.value,
                    color = Color(0xFFED9EBC)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 男性位置
                LocationCard(
                    title = "他的位置",
                    locationData = maleLocation.value,
                    color = Color(0xFFA8DADC)
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// 位置信息卡片
@Composable
fun LocationCard(title: String, locationData: LocationData?, color: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(color, shape = CircleShape)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "位置",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (locationData != null) {
                // 地址信息
                Text(
                    text = locationData.address,
                    fontSize = 16.sp,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // 经纬度
                Text(
                    text = "${locationData.latitude}, ${locationData.longitude}",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // 时间
                val time = remember {
                    val date = Date(locationData.timestamp.toLong())
                    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    formatter.format(date)
                }
                Text(
                    text = "更新时间: $time",
                    fontSize = 12.sp,
                    color = Color(0xFF999999)
                )
            } else {
                Text(
                    text = "暂无位置信息",
                    fontSize = 16.sp,
                    color = Color(0xFF999999),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// 匹配码输入屏幕
@Composable
fun MatchCodeScreen(onMatchCodeEntered: () -> Unit) {
    val context = LocalContext.current
    val matchCode = remember { mutableStateOf("") }
    val errorMessage = remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "欢迎使用月经记录应用",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFED9EBC),
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        Text(
            text = "请输入匹配码",
            fontSize = 18.sp,
            color = Color(0xFF7D5260),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = matchCode.value,
            onValueChange = { 
                matchCode.value = it
                errorMessage.value = ""
            },
            label = { Text("匹配码") },
            placeholder = { Text("女性: 520, 男性: 1314") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )
        
        if (errorMessage.value.isNotEmpty()) {
            Text(
                text = errorMessage.value,
                color = Color.Red,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        Button(
            onClick = {
                if (matchCode.value == "520" || matchCode.value == "1314") {
                    isLoading.value = true
                    
                    // 保存用户性别
                    val gender = if (matchCode.value == "520") "female" else "male"
                    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("user_gender", gender)
                        .apply()
                    
                    // 延迟一下，模拟加载
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(1000)
                        withContext(Dispatchers.Main) {
                            isLoading.value = false
                            onMatchCodeEntered()
                        }
                    }
                } else {
                    errorMessage.value = "请输入正确的匹配码: 女性(520) 或 男性(1314)"
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFED9EBC),
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            enabled = !isLoading.value
        ) {
            if (isLoading.value) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = if (isLoading.value) "验证中..." else "确认")
        }
        
        Text(
            text = "匹配码说明:\n女性用户: 520 (可上传和下载数据)\n男性用户: 1314 (仅可下载数据)",
            fontSize = 14.sp,
            color = Color(0xFF7D5260),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp)
        )
    }
}

// 数据库实例
private val databaseLock = Any()
private var sqliteDatabaseHelper: SQLiteDatabaseHelper? = null

fun getSQLiteDatabaseHelper(context: Context): SQLiteDatabaseHelper {
    return sqliteDatabaseHelper ?: synchronized(databaseLock) {
        sqliteDatabaseHelper ?: SQLiteDatabaseHelper(context).also {
            sqliteDatabaseHelper = it
        }
    }
}

// 数据持久化相关函数 - 使用SQLite数据库
suspend fun saveRecords(context: Context, records: List<PeriodRecord>): Boolean {
    return try {
        Log.d(TAG, "开始保存记录到SQLite数据库，记录数量: ${records.size}")
        val helper = getSQLiteDatabaseHelper(context)
        
        // 使用事务保存所有记录（原子操作），确保即使协程被取消也能完成
        val success = withContext(NonCancellable) {
            helper.saveAllRecords(records)
        }
        
        if (success) {
            Log.i(TAG, "保存记录成功，记录数量: ${records.size}")
        } else {
            Log.e(TAG, "保存记录失败")
        }
        success
    } catch (e: Exception) {
        // 错误处理
        Log.e(TAG, "保存记录异常", e)
        false
    }
}

suspend fun loadRecords(context: Context): List<PeriodRecord> {
    return try {
        Log.d(TAG, "开始从SQLite数据库加载记录")
        val helper = getSQLiteDatabaseHelper(context)
        val records = helper.getAllRecords()
        Log.i(TAG, "加载记录成功，数量: ${records.size}")
        records
    } catch (e: Exception) {
        // 错误处理，返回空列表
        Log.e(TAG, "加载记录异常", e)
        emptyList()
    }
}

suspend fun clearRecords(context: Context): Boolean {
    return try {
        Log.d(TAG, "开始清空SQLite数据库中的所有记录")
        val helper = getSQLiteDatabaseHelper(context)
        val deletedCount = helper.deleteAllRecords()
        Log.i(TAG, "清空记录成功，删除了" + deletedCount + "条记录")
        true
    } catch (e: Exception) {
        // 错误处理
        Log.e(TAG, "清空记录异常", e)
        false
    }
}

// 全局记录管理对象（不使用缓存，避免应用被回收后数据丢失）
object RecordManager {
    suspend fun saveRecords(context: Context, records: List<PeriodRecord>): Boolean {
        // 直接调用持久化函数，不缓存数据
        return com.example.yuejing.saveRecords(context, records)
    }
    
    suspend fun loadRecords(context: Context): List<PeriodRecord> {
        // 每次都从持久化存储加载，不使用缓存，确保数据最新
        return com.example.yuejing.loadRecords(context)
    }
    
    suspend fun clearRecords(context: Context): Boolean {
        // 清空所有记录
        return com.example.yuejing.clearRecords(context)
    }
}

// 提醒相关函数
fun saveReminderSettings(context: Context, periodReminder: Boolean, ovulationReminder: Boolean, fertileReminder: Boolean) {
    try {
        println("开始保存提醒设置")
        // 使用文件存储替代SharedPreferences
        val file = File(context.filesDir, "reminder_settings.json")
        val settings = mapOf(
            "period_reminder" to periodReminder,
            "ovulation_reminder" to ovulationReminder,
            "fertile_reminder" to fertileReminder
        )
        val settingsJson = Json.encodeToString(settings)
        file.writeText(settingsJson)
        println("保存提醒设置成功")
        
        // 保存后自动同步到云端，让男生能获取到最新设置
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val syncManager = SyncManager(context)
                syncManager.syncPartnerSharingState()
            } catch (e: Exception) {
                println("同步提醒设置到云端失败: ${e.message}")
                e.printStackTrace()
            }
        }
    } catch (e: Exception) {
        println("保存提醒设置异常: ${e.message}")
        e.printStackTrace()
    }
}

fun loadReminderSettings(context: Context): Triple<Boolean, Boolean, Boolean> {
    try {
        println("开始加载提醒设置")
        // 使用文件存储替代SharedPreferences
        val file = File(context.filesDir, "reminder_settings.json")
        if (!file.exists()) {
            println("提醒设置文件不存在，返回默认值")
            return Triple(true, true, true)
        }
        val settingsJson = file.readText()
        if (settingsJson.isBlank()) {
            println("提醒设置JSON为空，返回默认值")
            return Triple(true, true, true)
        }
        val settings = Json.decodeFromString<Map<String, Boolean>>(settingsJson)
        val periodReminder = settings["period_reminder"] ?: true
        val ovulationReminder = settings["ovulation_reminder"] ?: true
        val fertileReminder = settings["fertile_reminder"] ?: true
        println("加载提醒设置成功")
        return Triple(periodReminder, ovulationReminder, fertileReminder)
    } catch (e: Exception) {
        println("加载提醒设置异常: ${e.message}")
        e.printStackTrace()
        return Triple(true, true, true)
    }
}

@Composable
fun HomeScreen(navController: NavHostController) {
    val context = LocalContext.current
    val location = remember { mutableStateOf("正在获取位置...") }
    val coroutineScope = rememberCoroutineScope()
    
    // 刷新位置信息
    fun refreshLocation() {
        coroutineScope.launch {
            if (!LocationManager.getInstance().hasLocationPermission(context)) {
                location.value = "需要定位权限才能显示位置"
            } else {
                LocationManager.getInstance().getCurrentLocation(context) { currentLocation ->
                    if (currentLocation != null) {
                        // 将经纬度转换为实际地址
                        LocationManager.getInstance().getAddressFromLocation(context, currentLocation) { address ->
                            location.value = "当前位置: $address"
                        }
                    } else {
                        location.value = "无法获取位置，请确保定位服务已开启"
                    }
                }
            }
        }
    }
    
    // 初始化位置
    LaunchedEffect(Unit) {
        refreshLocation()
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF9F4F7) // 柔和的背景色
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp), // 增加内边距
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题部分
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 40.dp) // 增加标题与按钮的间距
            ) {
                Text(
                    text = "写给我的宝宝的应用 💖",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFED9EBC),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "爱你哦 😘",
                    fontSize = 18.sp,
                    color = Color(0xFF7D5260)
                )
                // 实时位置显示
                Text(
                    text = location.value,
                    fontSize = 14.sp,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(top = 16.dp)
                )
                // 权限请求和刷新按钮
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 刷新按钮
                    Button(
                        onClick = { refreshLocation() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA8DADC),
                            contentColor = Color(0xFF7D5260)
                        ),
                        modifier = Modifier.width(120.dp)
                    ) {
                        Text(text = "刷新位置")
                    }
                    
                    // 权限设置按钮
                    if (!LocationManager.getInstance().hasLocationPermission(context)) {
                        Button(
                            onClick = { 
                                // 跳转到应用设置页面，让用户手动开启权限
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null)
                                )
                                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFED9EBC),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.width(120.dp)
                        ) {
                            Text(text = "开启权限")
                        }
                    }
                }
            }
            
            // 主功能区 - 核心功能
            Text(
                text = "核心功能",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7D5260),
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 32.dp, bottom = 16.dp)
            )
            
            // 添加可交互按钮
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(500)) + slideInVertically(animationSpec = tween(300), initialOffsetY = { 50 })
            ) {
                Button(
                    onClick = { navController.navigate("calendar") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFED9EBC),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(0.8f)
                        .height(56.dp) // 统一按钮高度
                        .animateContentSize()
                ) {
                    Text(text = "进入日历 📅", fontSize = 16.sp)
                }
            }
            
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 100)) + slideInVertically(animationSpec = tween(300, delayMillis = 100), initialOffsetY = { 50 })
            ) {
                Button(
                    onClick = { navController.navigate("advice") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFA8DADC),
                        contentColor = Color(0xFF7D5260)
                    ),
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(0.8f)
                        .height(56.dp)
                ) {
                    Text(text = "智能建议 💡", fontSize = 16.sp)
                }
            }
            
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 200)) + slideInVertically(animationSpec = tween(300, delayMillis = 200), initialOffsetY = { 50 })
            ) {
                Button(
                    onClick = { navController.navigate("stats") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD6A5),
                        contentColor = Color(0xFF7D5260)
                    ),
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(0.8f)
                        .height(56.dp)
                ) {
                    Text(text = "统计分析 📊", fontSize = 16.sp)
                }
            }
            
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 300)) + slideInVertically(animationSpec = tween(300, delayMillis = 300), initialOffsetY = { 50 })
            ) {
                Button(
                    onClick = { navController.navigate("settings") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF5D8E4),
                        contentColor = Color(0xFF7D5260)
                    ),
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(0.8f)
                        .height(56.dp)
                ) {
                    Text(text = "设置 ⚙️", fontSize = 16.sp)
                }
            }
            
            // 伴侣功能区 - 分组显示
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "伴侣功能",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7D5260),
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 32.dp, bottom = 16.dp)
            )
            
            // 伴侣共享相关按钮
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 400)) + slideInVertically(animationSpec = tween(300, delayMillis = 400), initialOffsetY = { 50 })
            ) {
                Button(
                    onClick = { navController.navigate("partner_view") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF8E1EB),
                        contentColor = Color(0xFF7D5260)
                    ),
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(0.8f)
                        .height(56.dp)
                ) {
                    Text(text = "伴侣视图 👥", fontSize = 16.sp)
                }
            }
            
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 500)) + slideInVertically(animationSpec = tween(300, delayMillis = 500), initialOffsetY = { 50 })
            ) {
                Button(
                    onClick = { navController.navigate("partner_chat") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF8E1EB),
                        contentColor = Color(0xFF7D5260)
                    ),
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(0.8f)
                        .height(56.dp)
                ) {
                    Text(text = "伴侣聊天 💬", fontSize = 16.sp)
                }
            }
            
            // 孕期准备 - 单独分组
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "特殊功能",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7D5260),
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 32.dp, bottom = 16.dp)
            )
            
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 600)) + slideInVertically(animationSpec = tween(300, delayMillis = 600), initialOffsetY = { 50 })
            ) {
                Button(
                    onClick = { navController.navigate("pregnancy_preparation") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF8E1EB),
                        contentColor = Color(0xFF7D5260)
                    ),
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(0.8f)
                        .height(56.dp)
                ) {
                    Text(text = "孕期准备 🍼", fontSize = 16.sp)
                }
            }
            
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 700)) + slideInVertically(animationSpec = tween(300, delayMillis = 700), initialOffsetY = { 50 })
            ) {
                Button(
                    onClick = { navController.navigate("location_sharing") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFA8DADC),
                        contentColor = Color(0xFF7D5260)
                    ),
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(0.8f)
                        .height(56.dp)
                ) {
                    Text(text = "位置共享 📍", fontSize = 16.sp)
                }
            }
            
            // 底部留白
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 状态管理
    val currentDate = remember { mutableStateOf(LocalDate.now()) }
    val selectedDate = remember { mutableStateOf<LocalDate?>(null) }
    val showAddRecordDialog = remember { mutableStateOf(false) }
    val showViewRecordDialog = remember { mutableStateOf(false) }
    val showDateDialog = remember { mutableStateOf(false) }
    
    // 记录数据，使用数据持久化
    val records = remember {
        mutableStateListOf<PeriodRecord>()
    }
    
    // 加载记录的函数
    suspend fun loadRecordsFromStorage() {
        Log.d(TAG, "开始加载记录...")
        val savedRecords = RecordManager.loadRecords(context)
        Log.d(TAG, "加载到${savedRecords.size}条记录")
        records.clear()
        records.addAll(savedRecords)
        Log.d(TAG, "记录列表已更新，当前${records.size}条记录")
    }
    
    // 初始加载记录
    LaunchedEffect(Unit) {
        loadRecordsFromStorage()
    }
    
    // 监听导航回退，当从设置界面返回时重新加载数据
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect {
            if (it.destination.route == "calendar") {
                // 当导航到日历界面时重新加载数据
                loadRecordsFromStorage()
            }
        }
    }
    
    // 手动保存记录的函数
    suspend fun saveRecordsToStorage() {
        Log.d(TAG, "手动保存${records.size}条记录")
        val saveSuccess = RecordManager.saveRecords(context, records)
        Log.d(TAG, "保存结果: $saveSuccess")
    }
    

    
    // AI预测状态持久化
    val sharedPreferences = remember {
        context.getSharedPreferences("ai_prediction_prefs", Context.MODE_PRIVATE)
    }
    
    // 从SharedPreferences加载AI预测状态
    val useAIPrediction = remember {
        mutableStateOf(sharedPreferences.getBoolean("useAIPrediction", false))
    }
    val aiPredictionResult = remember {
        mutableStateOf(sharedPreferences.getString("aiPredictionResult", null))
    }
    val isLoadingAIPrediction = remember {
        mutableStateOf(false)
    }
    val aiPredictionError = remember {
        mutableStateOf<String?>(null)
    }
    
    // 协程作用域
    val coroutineScope = rememberCoroutineScope()
    
    // 智能预测
    val predictions = remember {
        derivedStateOf {
            Log.d(TAG, "predictions: starting calculation, total records=${records.size}")
            
            // 调试：统计记录类型
            val periodCount = records.count { it.type == RecordType.PERIOD }
            val moodCount = records.count { it.type == RecordType.MOOD_SYMPTOM }
            val intimacyCount = records.count { it.type == RecordType.INTIMACY }
            Log.d(TAG, "record type stats - PERIOD: $periodCount, MOOD_SYMPTOM: $moodCount, INTIMACY: $intimacyCount")
            
            // 调试：检查PERIOD记录的startDate
            val periodRecords = records.filter { it.type == RecordType.PERIOD }
            periodRecords.forEachIndexed { index, record ->
                Log.d(TAG, "PERIOD record[$index]: id=${record.id}, date=${record.date}, startDate=${record.startDate}, endDate=${record.endDate}")
            }
            
            val predictor = CyclePredictor(records)
            val periodStarts = predictor.extractPeriodStarts()
            Log.d(TAG, "extractPeriodStarts() returned ${periodStarts.size} period start dates")
            
            if (periodStarts.isEmpty()) {
                // 没有经期记录，显示提示信息
                Log.d(TAG, "no valid period start dates found, showing hint message")
                
                if (periodCount == 0 && records.isNotEmpty()) {
                    // 有其他类型记录但没有经期记录
                    mutableStateListOf(
                        "预测功能需要经期记录",
                        "您已添加${records.size}条记录，但都不是经期类型",
                        "添加记录时请选择'经期'类型"
                    )
                } else if (periodCount > 0 && periodStarts.isEmpty()) {
                    // 有PERIOD记录但没有有效的startDate
                    mutableStateListOf(
                        "经期记录缺少开始日期",
                        "请编辑经期记录，填写开始日期",
                        "格式应为: yyyy-MM-dd (如: 2026-01-15)"
                    )
                } else {
                    // 完全没有记录
                    mutableStateListOf(
                        "预测功能需要至少一条经期记录",
                        "请添加经期记录以启用智能预测",
                        "点击日历中的日期添加记录"
                    )
                }
            } else if (useAIPrediction.value && aiPredictionResult.value != null) {
                // 使用AI预测结果
                val parsedDates = predictor.parseAIPredictionResult(aiPredictionResult.value!!)
                if (parsedDates != null) {
                    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    
                    // 调度提醒 - 在后台线程中执行
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val scheduler = ReminderScheduler(context)
                            scheduler.scheduleReminders(
                                periodStartDate = parsedDates[0],
                                ovulationDate = parsedDates[2],
                                fertileStartDate = parsedDates[3]
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "调度提醒异常: ${e.message}", e)
                        }
                    }
                    
                    mutableStateListOf(
                        "AI预测 - 下次经期: ${parsedDates[0].format(dateFormatter)} 至 ${parsedDates[1].format(dateFormatter)}",
                        "AI预测 - 排卵期: ${parsedDates[2].format(dateFormatter)}",
                        "AI预测 - 易孕期: ${parsedDates[3].format(dateFormatter)} 至 ${parsedDates[4].format(dateFormatter)}"
                    )
                } else {
                    // AI预测结果解析失败，回退到传统预测
                    val predictionDates = predictor.predictNextPeriod()
                    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    
                    // 根据数据量添加可靠性提示
                    val reliabilityHint = if (periodStarts.size == 1) {
                        "（基于单次记录预测，准确性较低）"
                    } else if (periodStarts.size == 2) {
                        "（基于2次记录预测）"
                    } else {
                        "（基于${periodStarts.size}次记录预测）"
                    }
                    
                    // 调度提醒 - 在后台线程中执行
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val scheduler = ReminderScheduler(context)
                            scheduler.scheduleReminders(
                                periodStartDate = predictionDates[0],
                                ovulationDate = predictionDates[2],
                                fertileStartDate = predictionDates[3]
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "调度提醒异常: ${e.message}", e)
                        }
                    }
                    
                    mutableStateListOf(
                        "传统预测 - 下次经期${reliabilityHint}: ${predictionDates[0].format(dateFormatter)} 至 ${predictionDates[1].format(dateFormatter)}",
                        "传统预测 - 排卵期: ${predictionDates[2].format(dateFormatter)}",
                        "传统预测 - 易孕期: ${predictionDates[3].format(dateFormatter)} 至 ${predictionDates[4].format(dateFormatter)}",
                        "AI预测结果解析失败，已回退到传统预测"
                    )
                }
            } else {
                // 使用传统预测
                val predictionDates = predictor.predictNextPeriod()
                val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                
                // 根据数据量添加可靠性提示
                val reliabilityHint = if (periodStarts.size == 1) {
                    "（基于单次记录预测，准确性较低）"
                } else if (periodStarts.size == 2) {
                    "（基于2次记录预测）"
                } else {
                    "（基于${periodStarts.size}次记录预测）"
                }
                
                // 调度提醒 - 在后台线程中执行
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val scheduler = ReminderScheduler(context)
                        scheduler.scheduleReminders(
                            periodStartDate = predictionDates[0],
                            ovulationDate = predictionDates[2],
                            fertileStartDate = predictionDates[3]
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "调度提醒异常: ${e.message}", e)
                    }
                }
                
                mutableStateListOf(
                    "下次经期${reliabilityHint}: ${predictionDates[0].format(dateFormatter)} 至 ${predictionDates[1].format(dateFormatter)}",
                    "排卵期: ${predictionDates[2].format(dateFormatter)}",
                    "易孕期: ${predictionDates[3].format(dateFormatter)} 至 ${predictionDates[4].format(dateFormatter)}"
                )
            }
        }
    }.value
    
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // 顶部导航栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFF5D8E4),
                        contentColor = Color(0xFF7D5260)
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "日历 📅",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFED9EBC)
                )
            }
            
            // 月份导航
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { currentDate.value = currentDate.value.minusMonths(1) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFF5D8E4),
                        contentColor = Color(0xFF7D5260)
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一月")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentDate.value.format(DateTimeFormatter.ofPattern("yyyy年MM月")),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFED9EBC),
                        modifier = Modifier.clickable {
                            showDateDialog.value = true
                        }
                    )
                }
                Row {
                    Button(
                        onClick = { currentDate.value = LocalDate.now() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF5D8E4),
                            contentColor = Color(0xFF7D5260)
                        ),
                        modifier = Modifier.size(width = 60.dp, height = 36.dp)
                    ) {
                        Text(text = "今天", fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = { currentDate.value = currentDate.value.plusMonths(1) },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFFF5D8E4),
                            contentColor = Color(0xFF7D5260)
                        )
                    ) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "下一月")
                    }
                }
            }
            
            // 日历图例
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFFED9EBC), shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "经期", fontSize = 12.sp, color = Color(0xFF7D5260))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFFF5D8E4), shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "排卵期", fontSize = 12.sp, color = Color(0xFF7D5260))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFF7D5260), shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "易孕期", fontSize = 12.sp, color = Color(0xFF7D5260))
                }
            }
            
            // 星期标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val weekdays = listOf("日", "一", "二", "三", "四", "五", "六")
                weekdays.forEach {
                    Text(
                        text = it,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7D5260),
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            
            // 日历网格
            val firstDayOfMonth = currentDate.value.withDayOfMonth(1)
            val dayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // 0-6，0表示周日
            val daysInMonth = currentDate.value.lengthOfMonth()
            val totalCells = dayOfWeek + daysInMonth
            val rows = (totalCells + 6) / 7
            
            val predictor = CyclePredictor(records)
            // 获取预测日期，优先使用AI预测
            val predictionDates = if (useAIPrediction.value && aiPredictionResult.value != null) {
                predictor.parseAIPredictionResult(aiPredictionResult.value!!) ?: predictor.predictNextPeriod()
            } else {
                predictor.predictNextPeriod()
            }
            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            
            // AI预测日期判断
            val isUsingAIPrediction = useAIPrediction.value && aiPredictionResult.value != null
            
            // 添加左右滑动功能
            val totalDragDistance = remember { mutableStateOf(0f) }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                totalDragDistance.value = 0f
                            },
                            onDragEnd = {
                                if (totalDragDistance.value > 100f) {
                                    currentDate.value = currentDate.value.minusMonths(1)
                                } else if (totalDragDistance.value < -100f) {
                                    currentDate.value = currentDate.value.plusMonths(1)
                                }
                            }
                        ) { change, dragAmount ->
                            totalDragDistance.value += dragAmount
                        }
                    }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                for (row in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (col in 0 until 7) {
                            val dayIndex = row * 7 + col
                            val day = dayIndex - dayOfWeek + 1
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                            ) {
                                if (day in 1..daysInMonth) {
                                    val currentDay = currentDate.value.withDayOfMonth(day)
                                    val dateStr = currentDay.format(dateFormatter)
                                    
                                    // 检查是否有记录及记录类型
                                    val periodRecord = records.find { 
                                        (it.type == RecordType.PERIOD && it.startDate != null && it.endDate != null && 
                                        dateStr >= it.startDate && dateStr <= it.endDate) ||
                                        (it.type == RecordType.PERIOD && it.date == dateStr)
                                    }
                                    
                                    val moodRecord = records.find { 
                                        it.type == RecordType.MOOD_SYMPTOM && it.date == dateStr
                                    }
                                    
                                    val intimacyRecord = records.find { 
                                        it.type == RecordType.INTIMACY && it.date == dateStr
                                    }
                                    
                                    // 统计当天的记录类型数量
                                    val recordTypesCount = listOf(periodRecord, moodRecord, intimacyRecord).count { it != null }
                                    
                                    // 检查是否是排卵期或易孕期
            val ovulationDateStr = predictionDates[2].format(dateFormatter)
            val fertileStartStr = predictionDates[3].format(dateFormatter)
            val fertileEndStr = predictionDates[4].format(dateFormatter)
            
            val isOvulation = dateStr == ovulationDateStr
            val isFertile = dateStr >= fertileStartStr && dateStr <= fertileEndStr
            
            // 检查是否是预测的经期
            val nextPeriodStartStr = predictionDates[0].format(dateFormatter)
            val nextPeriodEndStr = predictionDates[1].format(dateFormatter)
            val isPredictedPeriod = dateStr >= nextPeriodStartStr && dateStr <= nextPeriodEndStr
                                    
                                    // 确定显示颜色（优先级：经期 > 心情 > 亲密 > 预测经期 > 排卵期 > 易孕期）
                                    val backgroundColor = when {
                                        periodRecord != null -> Color(0xFFED9EBC) // 经期 - 粉色
                                        moodRecord != null -> Color(0xFFA8DADC) // 心情症状 - 蓝色
                                        intimacyRecord != null -> Color(0xFFFFD6A5) // 亲密 - 橙色
                                        isPredictedPeriod -> Color(0xFFF8BBD0) // 预测经期 - 浅粉色
                                        isOvulation -> Color(0xFFF5D8E4) // 排卵期 - 浅粉色
                                        isFertile -> Color(0xFF7D5260) // 易孕期 - 深紫色
                                        else -> Color.Transparent
                                    }
                                    
                                    // 确定文字颜色
                                    val textColor = when {
                                        periodRecord != null || moodRecord != null || intimacyRecord != null || 
                                        isPredictedPeriod || isOvulation || isFertile -> Color.White
                                        else -> Color(0xFF7D5260)
                                    }
                                    
                                    // 检查是否是今天
                                    val isToday = currentDay == LocalDate.now()
                                    
                                    Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    backgroundColor,
                                                    shape = CircleShape
                                                )
                                                .then(if (isToday) Modifier.border(2.dp, Color(0xFF4A90E2), CircleShape) else Modifier)
                                                .clickable {
                                                    selectedDate.value = currentDay
                                                    
                                                    // 检查是否是未来日期
                                                    val today = LocalDate.now()
                                                    if (currentDay.isAfter(today)) {
                                                        // 显示未来日期提示
                                                        Toast.makeText(context, "宝宝，未来的日子记录不了哦，但是我会陪着你到未来呢！", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        // 检查当天是否有记录
                                                        val dayRecords = records.filter {
                                                            (it.type == RecordType.PERIOD && it.startDate != null && it.endDate != null && 
                                                            dateStr >= it.startDate && dateStr <= it.endDate) ||
                                                            (it.date == dateStr)
                                                        }
                                                        if (dayRecords.isNotEmpty()) {
                                                            // 显示查看记录对话框
                                                            showViewRecordDialog.value = true
                                                        } else {
                                                            // 显示添加记录对话框
                                                            showAddRecordDialog.value = true
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = day.toString(),
                                                    fontSize = 14.sp,
                                                    color = textColor
                                                )
                                                // 如果有多种记录类型，显示一个小点作为指示器
                                                if (recordTypesCount > 1) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .background(Color.White, shape = CircleShape)
                                                    )
                                                }
                                                // AI预测日期显示绿色下划线
                                                if (isUsingAIPrediction && (isPredictedPeriod || isOvulation || isFertile)) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .width(16.dp)
                                                            .height(2.dp)
                                                            .background(Color(0xFF4CAF50)) // 绿色下划线
                                                    )
                                                }
                                            }
                                        }
                                }
                            }
                        }
                    }
                }
            }
            }
            
            // AI预测按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isLoadingAIPrediction.value = true
                            aiPredictionError.value = null
                            try {
                                val predictor = CyclePredictor(records)
                                val result = predictor.predictCycleWithAI(context)
                                aiPredictionResult.value = result
                                if (result != null) {
                                    useAIPrediction.value = true // 自动启用AI预测
                                    // 保存AI预测结果到SharedPreferences
                                    sharedPreferences.edit()
                                        .putBoolean("useAIPrediction", true)
                                        .putString("aiPredictionResult", result)
                                        .apply()
                                } else {
                                    // AI预测失败，继续使用传统预测
                                    Log.i(TAG, "AI预测失败，继续使用传统预测")
                                    useAIPrediction.value = false
                                    aiPredictionError.value = "AI预测失败，已切换到传统预测"
                                    // 保存失败状态
                                    sharedPreferences.edit()
                                        .putBoolean("useAIPrediction", false)
                                        .apply()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "AI预测异常: ${e.message}", e)
                                // 异常情况下，继续使用传统预测
                                useAIPrediction.value = false
                                aiPredictionError.value = "AI预测异常，已切换到传统预测"
                                // 保存失败状态
                                sharedPreferences.edit()
                                    .putBoolean("useAIPrediction", false)
                                    .apply()
                            } finally {
                                isLoadingAIPrediction.value = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFA8DADC),
                        contentColor = Color(0xFF7D5260)
                    )
                ) {
                    if (isLoadingAIPrediction.value) {
                        CircularProgressIndicator(
                            color = Color(0xFF7D5260),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "AI预测中...")
                    } else {
                        Text(text = "获取AI智能预测")
                    }
                }
            }
            
            // AI预测状态显示
            if (aiPredictionError.value != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = aiPredictionError.value!!,
                        color = Color(0xFFF44336),
                        fontSize = 12.sp
                    )
                }
            }
            
            // AI预测结果预览
            if (aiPredictionResult.value != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .background(Color(0xFFF0F8F8), shape = MaterialTheme.shapes.medium)
                    ) {
                        Text(
                            text = "AI预测结果",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7D5260),
                            modifier = Modifier.padding(8.dp)
                        )
                        Text(
                            text = aiPredictionResult.value!!,
                            fontSize = 14.sp,
                            color = Color(0xFF7D5260),
                            modifier = Modifier.padding(8.dp)
                        )
                        
                        // AI预测开关
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "使用AI预测结果",
                                color = Color(0xFF7D5260)
                            )
                            Switch(
                                checked = useAIPrediction.value,
                                onCheckedChange = {
                                    useAIPrediction.value = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFA8DADC),
                                    checkedTrackColor = Color(0xFFF5D8E4),
                                    uncheckedThumbColor = Color(0xFFF5D8E4),
                                    uncheckedTrackColor = Color(0xFFED9EBC)
                                )
                            )
                        }
                    }
                }
            }
            
            // 预测信息
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(Color(0xFFF5D8E4), shape = MaterialTheme.shapes.medium)
            ) {
                Text(
                    text = "智能预测",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(8.dp)
                )
                predictions.forEach {
                    Text(
                        text = it,
                        fontSize = 14.sp,
                        color = Color(0xFF7D5260),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            // 添加记录按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Button(
                    onClick = {
                        selectedDate.value = LocalDate.now()
                        showAddRecordDialog.value = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFED9EBC),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "添加记录")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "添加记录")
                }
            }
        }
    }
    
    // 添加记录对话框
    if (showAddRecordDialog.value && selectedDate.value != null) {
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val selectedDateStr = selectedDate.value!!.format(dateFormatter)
        val recordTypes = listOf("经期", "心情症状", "亲密")
        val selectedType = remember { mutableStateOf(recordTypes[0]) }
        
        // 经期相关状态
        val selectedStartDate = remember { mutableStateOf(selectedDateStr) }
        val selectedEndDate = remember { mutableStateOf(selectedDateStr) }
        val showStartDatePicker = remember { mutableStateOf(false) }
        val showEndDatePicker = remember { mutableStateOf(false) }
        
        // 心情症状相关状态
        val moodOptions = listOf("开心", "平静", "烦躁", "焦虑", "抑郁")
        val selectedMood = remember { mutableStateOf(moodOptions[0]) }
        val symptomOptions = listOf("腹痛", "头痛", "乳房胀痛", "疲劳", "恶心", "腰痛", "头晕", "失眠", "情绪波动", "腹胀", "便秘", "腹泻", "食欲不振", "食欲旺盛", "皮肤问题")
        val selectedSymptoms = remember { mutableStateListOf<String>() }
        
        // 亲密相关状态
        val intimacyOptions = listOf("戴套", "体外", "体内", "口服避孕药")
        val selectedIntimacy = remember { mutableStateOf(intimacyOptions[0]) }
        
        AlertDialog(
            onDismissRequest = { showAddRecordDialog.value = false },
            title = { Text(text = "添加记录") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = "日期: $selectedDateStr")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "记录类型:")
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        recordTypes.forEach {
                            Button(
                                onClick = { selectedType.value = it },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedType.value == it) Color(0xFFED9EBC) else Color(0xFFF5D8E4),
                                    contentColor = if (selectedType.value == it) Color.White else Color(0xFF7D5260)
                                ),
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text(text = it)
                            }
                        }
                    }
                    
                    when (selectedType.value) {
                        "经期" -> {
                            // 经期记录表单
                            Text(text = "开始日期:")
                            Button(
                                onClick = { showStartDatePicker.value = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF5D8E4),
                                    contentColor = Color(0xFF7D5260)
                                )
                            ) {
                                Text(text = selectedStartDate.value)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "结束日期:")
                            Button(
                                onClick = { showEndDatePicker.value = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF5D8E4),
                                    contentColor = Color(0xFF7D5260)
                                )
                            ) {
                                Text(text = selectedEndDate.value)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "提示: 可以先记录开始日期，结束日期可后续编辑更新",
                                fontSize = 12.sp,
                                color = Color(0xFF7D5260)
                            )
                        }
                        "心情症状" -> {
                            // 心情症状记录表单
                            Text(text = "心情:")
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                moodOptions.forEach {
                                    Button(
                                        onClick = { selectedMood.value = it },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selectedMood.value == it) Color(0xFFED9EBC) else Color(0xFFF5D8E4),
                                            contentColor = if (selectedMood.value == it) Color.White else Color(0xFF7D5260)
                                        ),
                                        modifier = Modifier.padding(2.dp)
                                    ) {
                                        Text(text = it, fontSize = 12.sp)
                                    }
                                }
                            }
                            
                            Text(text = "症状:")
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                symptomOptions.forEach {
                                    Button(
                                        onClick = {
                                            if (selectedSymptoms.contains(it)) {
                                                selectedSymptoms.remove(it)
                                            } else {
                                                selectedSymptoms.add(it)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selectedSymptoms.contains(it)) Color(0xFFED9EBC) else Color(0xFFF5D8E4),
                                            contentColor = if (selectedSymptoms.contains(it)) Color.White else Color(0xFF7D5260)
                                        ),
                                        modifier = Modifier.padding(2.dp)
                                    ) {
                                        Text(text = it, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        "亲密" -> {
                            // 亲密记录表单
                            Text(text = "亲密类型:")
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                intimacyOptions.forEach {
                                    Button(
                                        onClick = { selectedIntimacy.value = it },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selectedIntimacy.value == it) Color(0xFFED9EBC) else Color(0xFFF5D8E4),
                                            contentColor = if (selectedIntimacy.value == it) Color.White else Color(0xFF7D5260)
                                        ),
                                        modifier = Modifier.padding(2.dp)
                                    ) {
                                        Text(text = it, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val recordType = when (selectedType.value) {
                            "经期" -> RecordType.PERIOD
                            "心情症状" -> RecordType.MOOD_SYMPTOM
                            "亲密" -> RecordType.INTIMACY
                            else -> RecordType.PERIOD
                        }
                        
                        val newRecord = when (recordType) {
                            RecordType.PERIOD -> {
                                val newStart = LocalDate.parse(selectedStartDate.value, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                val newEnd = LocalDate.parse(selectedEndDate.value, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                
                                // Check for existing period records with overlapping or consecutive dates
                                val existingPeriodRecords = records.filter {
                                    it.type == RecordType.PERIOD &&
                                    it.startDate != null &&
                                    it.endDate != null
                                }
                                
                                // Check for existing period records with overlapping or consecutive dates
                                val recordsToMerge = mutableListOf<PeriodRecord>()
                                
                                existingPeriodRecords.forEach { existingRecord ->
                                    val existingStart = LocalDate.parse(existingRecord.startDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                    val existingEnd = LocalDate.parse(existingRecord.endDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                    
                                    // Check if there's any overlap or if dates are consecutive
                                    val hasOverlap = !(newEnd.isBefore(existingStart) || newStart.isAfter(existingEnd))
                                    val isConsecutive = newStart.minusDays(1) == existingEnd || newEnd.plusDays(1) == existingStart
                                    
                                    if (hasOverlap || isConsecutive) {
                                        recordsToMerge.add(existingRecord)
                                    }
                                }
                                
                                if (recordsToMerge.isNotEmpty()) {
                                    // Merge all consecutive/overlapping records
                                    var mergedStart = newStart
                                    var mergedEnd = newEnd
                                    
                                    recordsToMerge.forEach { record ->
                                        val recordStart = LocalDate.parse(record.startDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                        val recordEnd = LocalDate.parse(record.endDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                        
                                        if (recordStart.isBefore(mergedStart)) mergedStart = recordStart
                                        if (recordEnd.isAfter(mergedEnd)) mergedEnd = recordEnd
                                    }
                                    
                                    // Create merged record
                                    val mergedRecord = PeriodRecord(
                                        id = System.currentTimeMillis().toString(),
                                        type = RecordType.PERIOD,
                                        startDate = mergedStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                                        endDate = mergedEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                                        date = selectedDateStr,
                                        timestamp = System.currentTimeMillis().toString()
                                    )
                                    
                                    // Remove old records and add merged record
                                    recordsToMerge.forEach { records.remove(it) }
                                    records.add(mergedRecord)
                                    Log.d(TAG, "合并${recordsToMerge.size + 1}条经期记录，当前共${records.size}条记录")
                                    
                                    // 立即保存记录（在协程中执行）
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val saveSuccess = RecordManager.saveRecords(context, records)
                                        Log.d(TAG, "合并记录后保存结果: $saveSuccess")
                                        // 更新小部件
                                        withContext(Dispatchers.Main) {
                                            CycleWidgetProvider.updateWidgets(context)
                                        }
                                        
                                        // 如果是女生用户，立即上传数据
                                        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                        val userGender = sharedPreferences.getString("user_gender", "")
                                        if (userGender == "female") {
                                            Log.d(TAG, "女生用户，自动上传数据")
                                            val syncManager = SyncManager(context)
                                            syncManager.uploadRecords()
                                        }
                                    }
                                    
                                    showAddRecordDialog.value = false
                                    return@Button
                                }
                                
                                PeriodRecord(
                                    id = System.currentTimeMillis().toString(),
                                    type = recordType,
                                    startDate = selectedStartDate.value,
                                    endDate = selectedEndDate.value,
                                    date = selectedDateStr,
                                    timestamp = System.currentTimeMillis().toString()
                                )
                            }
                            RecordType.MOOD_SYMPTOM -> {
                                PeriodRecord(
                                    id = System.currentTimeMillis().toString(),
                                    type = recordType,
                                    date = selectedDateStr,
                                    mood = selectedMood.value,
                                    symptoms = selectedSymptoms,
                                    timestamp = System.currentTimeMillis().toString()
                                )
                            }
                            RecordType.INTIMACY -> {
                                PeriodRecord(
                                    id = System.currentTimeMillis().toString(),
                                    type = recordType,
                                    date = selectedDateStr,
                                    intimacyType = selectedIntimacy.value,
                                    timestamp = System.currentTimeMillis().toString()
                                )
                            }
                            else -> {
                                PeriodRecord(
                                    id = System.currentTimeMillis().toString(),
                                    type = recordType,
                                    date = selectedDateStr,
                                    timestamp = System.currentTimeMillis().toString()
                                )
                            }
                        }
                        records.add(newRecord)
                        Log.d(TAG, "添加新记录，当前共${records.size}条记录")
                        // 立即保存记录（在协程中执行）
                        CoroutineScope(Dispatchers.IO).launch {
                            val saveSuccess = RecordManager.saveRecords(context, records)
                            Log.d(TAG, "添加记录后保存结果: $saveSuccess")
                            // 更新小部件
                            withContext(Dispatchers.Main) {
                                CycleWidgetProvider.updateWidgets(context)
                                
                                // 如果添加的是经期记录，清除AI预测结果
                                if (recordType == RecordType.PERIOD) {
                                    // 清除AI预测状态
                                    sharedPreferences.edit()
                                        .putBoolean("useAIPrediction", false)
                                        .putString("aiPredictionResult", null)
                                        .apply()
                                    
                                    // 更新本地状态
                                    useAIPrediction.value = false
                                    aiPredictionResult.value = null
                                    aiPredictionError.value = "已添加新的经期记录，请重新获取AI预测"
                                    
                                    // 显示提示
                                    Toast.makeText(context, "已添加新的经期记录，请重新获取AI预测", Toast.LENGTH_SHORT).show()
                                }
                            }
                            
                            // 如果是女生用户，立即上传数据
                            val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            val userGender = sharedPreferences.getString("user_gender", "")
                            if (userGender == "female") {
                                Log.d(TAG, "女生用户，自动上传数据")
                                val syncManager = SyncManager(context)
                                syncManager.uploadRecords()
                            }
                        }
                        showAddRecordDialog.value = false
                    }
                ) {
                    Text(text = "保存")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showAddRecordDialog.value = false }
                ) {
                    Text(text = "取消")
                }
            }
        )
        
        // 开始日期选择器
        if (showStartDatePicker.value) {
            val currentDate = LocalDate.parse(selectedStartDate.value, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            CustomDatePicker(
                initialDate = currentDate,
                onDateSelected = {
                    selectedStartDate.value = it.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                },
                onDismiss = { showStartDatePicker.value = false }
            )
        }
        
        // 结束日期选择器
        if (showEndDatePicker.value) {
            val currentDate = LocalDate.parse(selectedEndDate.value, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            CustomDatePicker(
                initialDate = currentDate,
                onDateSelected = {
                    selectedEndDate.value = it.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                },
                onDismiss = { showEndDatePicker.value = false }
            )
        }
    }
    
    // 查看记录对话框
    if (showViewRecordDialog.value && selectedDate.value != null) {
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val selectedDateStr = selectedDate.value!!.format(dateFormatter)
        val showEditDialog = remember { mutableStateOf(false) }
        val selectedRecord = remember { mutableStateOf<PeriodRecord?>(null) }
        
        // 过滤当天的记录
        val dayRecords = records.filter {
            (it.type == RecordType.PERIOD && it.startDate != null && it.endDate != null && 
            selectedDateStr >= it.startDate && selectedDateStr <= it.endDate) ||
            (it.date == selectedDateStr)
        }
        
        AlertDialog(
            onDismissRequest = { showViewRecordDialog.value = false },
            title = { Text(text = "查看记录") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = "日期: $selectedDateStr")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (dayRecords.isEmpty()) {
                        Text(text = "当天没有记录")
                    } else {
                        // Group records by type for better organization
                        val recordsByType = dayRecords.groupBy { it.type }
                        
                        recordsByType.forEach { (type, typeRecords) ->
                            val typeName = when (type) {
                                RecordType.PERIOD -> "经期记录"
                                RecordType.MOOD_SYMPTOM -> "心情症状记录"
                                RecordType.INTIMACY -> "亲密记录"
                                else -> "其他记录"
                            }
                            
                            // For period records, show only unique records based on start/end dates
                            val displayRecords = if (type == RecordType.PERIOD) {
                                typeRecords.distinctBy { "${it.startDate}-${it.endDate}" }
                            } else {
                                typeRecords
                            }
                            
                            // Collapsible section state
                            val isExpanded = remember { mutableStateOf(true) }
                            
                            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                // Section header with collapse/expand functionality
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isExpanded.value = !isExpanded.value
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$typeName (${displayRecords.size})",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFED9EBC)
                                    )
                                    Text(
                                        text = if (isExpanded.value) "▼" else "▶",
                                        fontSize = 14.sp,
                                        color = Color(0xFF7D5260)
                                    )
                                }
                                
                                // Expanded content
                                if (isExpanded.value) {
                                    displayRecords.forEachIndexed { index, record ->
                                        Column(modifier = Modifier.padding(bottom = 10.dp, start = 16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (type == RecordType.PERIOD) {
                                                        "${record.startDate} 至 ${record.endDate}"
                                                    } else {
                                                        "记录 ${index + 1}"
                                                    },
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF7D5260)
                                                )
                                                Row {
                                                    Button(
                                                        onClick = {
                                                            // Delete record
                                                            val index = records.indexOfFirst { it.id == record.id }
                                                            if (index != -1) {
                                                                records.removeAt(index)
                                                                Log.d(TAG, "删除记录，当前共${records.size}条记录")
                                                                // 立即保存记录（在协程中执行）
                                                                CoroutineScope(Dispatchers.IO).launch {
                                                                    val saveSuccess = RecordManager.saveRecords(context, records)
                                                                    Log.d(TAG, "删除记录后保存结果: $saveSuccess")
                                                                    // 更新小部件
                                                                    withContext(Dispatchers.Main) {
                                                                        CycleWidgetProvider.updateWidgets(context)
                                                                    }
                                                                }
                                                                // Refresh the dialog
                                                                showViewRecordDialog.value = false
                                                                showViewRecordDialog.value = true
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = Color(0xFFFFE0E0),
                                                            contentColor = Color(0xFFD32F2F)
                                                        ),
                                                        modifier = Modifier.size(width = 60.dp, height = 32.dp).padding(end = 4.dp)
                                                    ) {
                                                        Text(text = "删除", fontSize = 12.sp)
                                                    }
                                                    Button(
                                                        onClick = {
                                                            selectedRecord.value = record
                                                            showEditDialog.value = true
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = Color(0xFFF5D8E4),
                                                            contentColor = Color(0xFF7D5260)
                                                        ),
                                                        modifier = Modifier.size(width = 60.dp, height = 32.dp)
                                                    ) {
                                                        Text(text = "编辑", fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                            
                                            when (record.type) {
                                                RecordType.PERIOD -> {
                                                    // Period details are already shown in the title
                                                }
                                                RecordType.MOOD_SYMPTOM -> {
                                                    Text(text = "心情: ${record.mood}", fontSize = 12.sp)
                                                    Text(text = "症状: ${record.symptoms.joinToString(", ")}", fontSize = 12.sp)
                                                }
                                                RecordType.INTIMACY -> {
                                                    Text(text = "类型: ${record.intimacyType}", fontSize = 12.sp)
                                                }
                                                else -> {
                                                    // Handle other record types
                                                }
                                            }
                                            
                                            // 格式化记录时间
                                            val timestampText = try {
                                                val timestamp = record.timestamp?.toLongOrNull()
                                                if (timestamp != null) {
                                                    val date = Date(timestamp)
                                                    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                                    sdf.format(date)
                                                } else {
                                                    "未知"
                                                }
                                            } catch (e: Exception) {
                                                "未知"
                                            }
                                            Text(text = "记录时间: $timestampText", fontSize = 11.sp, color = Color(0xFF999999))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showViewRecordDialog.value = false
                        showAddRecordDialog.value = true
                    }
                ) {
                    Text(text = "添加记录")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showViewRecordDialog.value = false }
                ) {
                    Text(text = "关闭")
                }
            }
        )
        
        // 编辑记录对话框
        if (showEditDialog.value && selectedRecord.value != null) {
            val record = selectedRecord.value!!
            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            
            // 经期相关状态
            val selectedStartDate = remember { mutableStateOf(record.startDate ?: record.date ?: selectedDateStr) }
            val selectedEndDate = remember { mutableStateOf(record.endDate ?: record.date ?: selectedDateStr) }
            val showStartDatePicker = remember { mutableStateOf(false) }
            val showEndDatePicker = remember { mutableStateOf(false) }
            
            // 心情症状相关状态
            val moodOptions = listOf("开心", "平静", "烦躁", "焦虑", "抑郁")
            val selectedMood = remember { mutableStateOf(record.mood ?: moodOptions[0]) }
            val symptomOptions = listOf("腹痛", "头痛", "乳房胀痛", "疲劳", "恶心", "腰痛", "头晕", "失眠", "情绪波动", "腹胀", "便秘", "腹泻", "食欲不振", "食欲旺盛", "皮肤问题")
            val selectedSymptoms = remember {
                val symptoms = mutableStateListOf<String>()
                symptoms.addAll(record.symptoms)
                symptoms
            }
            
            // 亲密相关状态
            val intimacyOptions = listOf("戴套", "体外", "体内", "口服避孕药")
            val selectedIntimacy = remember { mutableStateOf(record.intimacyType ?: intimacyOptions[0]) }
            
            AlertDialog(
                onDismissRequest = { showEditDialog.value = false },
                title = { Text(text = "编辑记录") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(text = "日期: $selectedDateStr")
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        when (record.type) {
                            RecordType.PERIOD -> {
                                // 经期记录表单
                                Text(text = "开始日期:")
                                Button(
                                    onClick = { showStartDatePicker.value = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF5D8E4),
                                        contentColor = Color(0xFF7D5260)
                                    )
                                ) {
                                    Text(text = selectedStartDate.value)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "结束日期:")
                                Button(
                                    onClick = { showEndDatePicker.value = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF5D8E4),
                                        contentColor = Color(0xFF7D5260)
                                    )
                                ) {
                                    Text(text = selectedEndDate.value)
                                }
                            }
                            RecordType.MOOD_SYMPTOM -> {
                                // 心情症状记录表单
                                Text(text = "心情:")
                                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    moodOptions.forEach {
                                        Button(
                                            onClick = { selectedMood.value = it },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (selectedMood.value == it) Color(0xFFED9EBC) else Color(0xFFF5D8E4),
                                                contentColor = if (selectedMood.value == it) Color.White else Color(0xFF7D5260)
                                            ),
                                            modifier = Modifier.padding(2.dp)
                                        ) {
                                            Text(text = it, fontSize = 12.sp)
                                        }
                                    }
                                }
                                
                                Text(text = "症状:")
                                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    symptomOptions.forEach {
                                        Button(
                                            onClick = {
                                                if (selectedSymptoms.contains(it)) {
                                                    selectedSymptoms.remove(it)
                                                } else {
                                                    selectedSymptoms.add(it)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (selectedSymptoms.contains(it)) Color(0xFFED9EBC) else Color(0xFFF5D8E4),
                                                contentColor = if (selectedSymptoms.contains(it)) Color.White else Color(0xFF7D5260)
                                            ),
                                            modifier = Modifier.padding(2.dp)
                                        ) {
                                            Text(text = it, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            RecordType.INTIMACY -> {
                                // 亲密记录表单
                                Text(text = "亲密类型:")
                                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    intimacyOptions.forEach {
                                        Button(
                                            onClick = { selectedIntimacy.value = it },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (selectedIntimacy.value == it) Color(0xFFED9EBC) else Color(0xFFF5D8E4),
                                                contentColor = if (selectedIntimacy.value == it) Color.White else Color(0xFF7D5260)
                                            ),
                                            modifier = Modifier.padding(2.dp)
                                        ) {
                                            Text(text = it, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val updatedRecord = when (record.type) {
                                RecordType.PERIOD -> {
                                    record.copy(
                                        startDate = selectedStartDate.value,
                                        endDate = selectedEndDate.value,
                                        timestamp = System.currentTimeMillis().toString()
                                    )
                                }
                                RecordType.MOOD_SYMPTOM -> {
                                    record.copy(
                                        mood = selectedMood.value,
                                        symptoms = selectedSymptoms,
                                        timestamp = System.currentTimeMillis().toString()
                                    )
                                }
                                RecordType.INTIMACY -> {
                                    record.copy(
                                        intimacyType = selectedIntimacy.value,
                                        timestamp = System.currentTimeMillis().toString()
                                    )
                                }
                                else -> record
                            }
                            
                            // 更新记录
                            val index = records.indexOfFirst { it.id == record.id }
                            if (index != -1) {
                                records[index] = updatedRecord
                                Log.d(TAG, "编辑记录，当前共${records.size}条记录")
                                // 立即保存记录（在协程中执行）
                                CoroutineScope(Dispatchers.IO).launch {
                                    val saveSuccess = RecordManager.saveRecords(context, records)
                                    Log.d(TAG, "编辑记录后保存结果: $saveSuccess")
                                    // 更新小部件
                                    withContext(Dispatchers.Main) {
                                        CycleWidgetProvider.updateWidgets(context)
                                    }
                                }
                            }
                            
                            showEditDialog.value = false
                            showViewRecordDialog.value = false
                            // 重新显示查看记录对话框以更新内容
                            showViewRecordDialog.value = true
                        }
                    ) {
                        Text(text = "保存")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showEditDialog.value = false }
                    ) {
                        Text(text = "取消")
                    }
                }
            )
            
            // 开始日期选择器
            if (showStartDatePicker.value) {
                val currentDate = LocalDate.parse(selectedStartDate.value, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                CustomDatePicker(
                    initialDate = currentDate,
                    onDateSelected = {
                        selectedStartDate.value = it.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    },
                    onDismiss = { showStartDatePicker.value = false }
                )
            }
            
            // 结束日期选择器
            if (showEndDatePicker.value) {
                val currentDate = LocalDate.parse(selectedEndDate.value, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                CustomDatePicker(
                    initialDate = currentDate,
                    onDateSelected = {
                        selectedEndDate.value = it.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    },
                    onDismiss = { showEndDatePicker.value = false }
                )
            }
        }
    }
    
    // 日期跳转对话框
    if (showDateDialog.value) {
        CustomDatePicker(
            initialDate = currentDate.value,
            onDateSelected = {
                currentDate.value = it
            },
            onDismiss = { showDateDialog.value = false }
        )
    }
}



@Composable
fun SettingsScreen(navController: NavHostController, userGender: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val (periodReminder, ovulationReminder, fertileReminder) = loadReminderSettings(context)
    val periodReminderState = remember { mutableStateOf(periodReminder) }
    val ovulationReminderState = remember { mutableStateOf(ovulationReminder) }
    val fertileReminderState = remember { mutableStateOf(fertileReminder) }
    val showClearConfirmDialog = remember { mutableStateOf(false) }
    val isExportingLogs = remember { mutableStateOf(false) }
    val isUploading = remember { mutableStateOf(false) }
    val isDownloading = remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // 顶部导航栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFF5D8E4),
                        contentColor = Color(0xFF7D5260)
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "设置 ⚙️",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFED9EBC)
                )
            }
            
            // 提醒设置 - 只有女生能看到
            if (userGender == "female") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "提醒设置",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7D5260),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "经期提醒 🩸", color = Color(0xFF7D5260))
                        Switch(
                            checked = periodReminderState.value,
                            onCheckedChange = {
                                periodReminderState.value = it
                                saveReminderSettings(context, it, ovulationReminderState.value, fertileReminderState.value)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFED9EBC),
                                checkedTrackColor = Color(0xFFF5D8E4),
                                uncheckedThumbColor = Color(0xFFF5D8E4),
                                uncheckedTrackColor = Color(0xFFD0C4D1)
                            )
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "排卵期提醒 🌸", color = Color(0xFF7D5260))
                        Switch(
                            checked = ovulationReminderState.value,
                            onCheckedChange = {
                                ovulationReminderState.value = it
                                saveReminderSettings(context, periodReminderState.value, it, fertileReminderState.value)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFED9EBC),
                                checkedTrackColor = Color(0xFFF5D8E4),
                                uncheckedThumbColor = Color(0xFFF5D8E4),
                                uncheckedTrackColor = Color(0xFFD0C4D1)
                            )
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "易孕期提醒 💕", color = Color(0xFF7D5260))
                        Switch(
                            checked = fertileReminderState.value,
                            onCheckedChange = {
                                fertileReminderState.value = it
                                saveReminderSettings(context, periodReminderState.value, ovulationReminderState.value, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFED9EBC),
                                checkedTrackColor = Color(0xFFF5D8E4),
                                uncheckedThumbColor = Color(0xFFF5D8E4),
                                uncheckedTrackColor = Color(0xFFD0C4D1)
                            )
                        )
                    }
                }
            }
            
            // 其他设置
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "其他设置",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Button(
                    onClick = {
                        // 显示确认弹窗
                        showClearConfirmDialog.value = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7D5260),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(text = "清空所有记录 🗑️")
                }
                
                Button(
                    onClick = {
                        navController.navigate("widget_settings")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF5D8E4),
                        contentColor = Color(0xFF7D5260)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(text = "小部件设置 🎨")
                }
                
                Button(
                    onClick = {
                        // 清除已保存的匹配码设置
                        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        sharedPreferences.edit()
                            .putBoolean("has_entered_match_code", false)
                            .putString("user_gender", "")
                            .apply()
                        
                        // 重新导航到匹配码输入界面
                        navController.navigate("match_code") {
                            popUpTo("settings") {
                                inclusive = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFA8DADC),
                        contentColor = Color(0xFF7D5260)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(text = "修改匹配码 🔄")
                }
                
                // Gist ID 设置
                val gistIdInput = remember { mutableStateOf("") }
                val syncManager = SyncManager(context)
                val currentGistId = syncManager.getGistId()
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Gist ID 设置",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7D5260),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    OutlinedTextField(
                        value = gistIdInput.value,
                        onValueChange = { gistIdInput.value = it },
                        label = { Text("Gist ID") },
                        placeholder = { Text("输入 GitHub Gist ID") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                if (gistIdInput.value.isNotEmpty()) {
                                    syncManager.saveGistId(gistIdInput.value)
                                    showToast(context, "Gist ID 设置成功！")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFA8DADC),
                                contentColor = Color(0xFF7D5260)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        ) {
                            Text(text = "保存 Gist ID")
                        }
                        
                        Button(
                            onClick = {
                                syncManager.clearGistId()
                                gistIdInput.value = ""
                                showToast(context, "Gist ID 已清除")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7D5260),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        ) {
                            Text(text = "清除 Gist ID")
                        }
                    }
                    
                    if (currentGistId != null) {
                        Text(
                            text = "当前 Gist ID: $currentGistId",
                            fontSize = 12.sp,
                            color = Color(0xFF7D5260),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                
                // 同步功能 - 上传和下载按钮
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(
                        text = "数据同步",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7D5260),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // 上传按钮（仅女性可见）
                    if (userGender == "female") {
                        Button(
                            onClick = {
                                isUploading.value = true
                                // 在协程中执行上传
                                CoroutineScope(Dispatchers.IO).launch {
                                    val syncManager = SyncManager(context)
                                    syncManager.uploadRecords()
                                    withContext(Dispatchers.Main) {
                                        isUploading.value = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFA8DADC),
                                contentColor = Color(0xFF7D5260)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            enabled = !isUploading.value
                        ) {
                            if (isUploading.value) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF7D5260),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(text = if (isUploading.value) "上传中..." else "上传数据 ⬆️")
                        }
                    }
                    
                    // 下载按钮（所有人可见）
                    Button(
                        onClick = {
                            isDownloading.value = true
                            // 在协程中执行下载
                            CoroutineScope(Dispatchers.IO).launch {
                                val syncManager = SyncManager(context)
                                syncManager.downloadRecords(showToast = true)
                                withContext(Dispatchers.Main) {
                                    isDownloading.value = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF5D8E4),
                            contentColor = Color(0xFF7D5260)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        enabled = !isDownloading.value
                    ) {
                        if (isDownloading.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF7D5260),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = if (isDownloading.value) "下载中..." else "下载数据 ⬇️")
                    }
                }
                
                // 日志导出功能
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(
                        text = "开发者工具",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7D5260),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // 导出日志按钮
                    Button(
                        onClick = {
                            isExportingLogs.value = true
                            // 在协程中执行日志导出
                            CoroutineScope(Dispatchers.IO).launch {
                                val success = MainActivity.exportLogs(context)
                                withContext(Dispatchers.Main) {
                                    isExportingLogs.value = false
                                    if (success) {
                                        Toast.makeText(context, "日志导出成功", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "日志导出失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFDFFB6),
                            contentColor = Color(0xFF7D5260)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        enabled = !isExportingLogs.value
                    ) {
                        if (isExportingLogs.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF7D5260),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = if (isExportingLogs.value) "导出中..." else "导出日志 📋")
                    }
                }
            }
            

            
            // 添加作者和版本信息
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "关于",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "作者: 廿巳", color = Color(0xFF7D5260))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "版本号: 爱你1.0", color = Color(0xFF7D5260))
                }
            }
        }
    }
    
    // 清空记录确认弹窗
    if (showClearConfirmDialog.value) {
        AlertDialog(
            onDismissRequest = {
                showClearConfirmDialog.value = false
            },
            title = { Text(text = "确认清空") },
            text = { Text(text = "宝宝，确定要清空所有记录吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog.value = false
                        // 清空所有记录（使用协程执行数据库操作）
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                Log.d(TAG, "用户点击清空所有记录按钮")
                                val success = RecordManager.clearRecords(context)
                                if (success) {
                                                    Log.i(TAG, "清空所有记录成功")
                                                    // 添加UI反馈
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "记录已清空 💕", Toast.LENGTH_SHORT).show()
                                                        // 更新小部件
                                                        CycleWidgetProvider.updateWidgets(context)
                                                    }
                                                } else {
                                                    Log.e(TAG, "清空所有记录失败")
                                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "清空所有记录异常: ${e.message}", e)
                            }
                        }
                    }
                ) {
                    Text(text = "确定")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog.value = false
                    }
                ) {
                    Text(text = "取消")
                }
            }
        )
    }
}

@Composable
fun AdviceScreen(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val records = remember {
        mutableStateListOf<PeriodRecord>()
    }
    
    // AI建议状态
    val aiHealthAdvice = remember {
        mutableStateOf<String?>(null)
    }
    val isLoadingAIAdvice = remember {
        mutableStateOf(false)
    }
    
    // 初始加载记录
    LaunchedEffect(Unit) {
        val savedRecords = RecordManager.loadRecords(context)
        records.clear()
        records.addAll(savedRecords)
    }
    
    // 计算当前周期阶段
    val currentPhase = remember {
        derivedStateOf {
            val predictor = CyclePredictor(records)
            val predictionDates = predictor.predictNextPeriod()
            val today = LocalDate.now()
            
            when {
                today >= predictionDates[0] && today <= predictionDates[1] -> "经期"
                today == predictionDates[2] -> "排卵期"
                today >= predictionDates[3] && today <= predictionDates[4] -> "易孕期"
                today < predictionDates[3] -> "卵泡期"
                else -> "黄体期"
            }
        }
    }.value
    
    // 基于周期阶段的健康建议
    val healthAdvice = when (currentPhase) {
        "经期" -> listOf(
            "💖 保持温暖，避免受凉",
            "💖 多休息，避免剧烈运动",
            "💖 饮食清淡，多吃温热食物",
            "💖 注意个人卫生",
            "💖 可以适当食用一些补血食物"
        )
        "排卵期" -> listOf(
            "🌸 保持心情愉悦",
            "🌸 注意避孕或备孕",
            "🌸 可以增加一些有氧运动",
            "🌸 保持充足的睡眠",
            "🌸 注意营养均衡"
        )
        "易孕期" -> listOf(
            "💕 注意避孕或备孕",
            "💕 保持规律作息",
            "💕 可以适当增加同房频率（如果备孕）",
            "💕 保持心情放松",
            "💕 注意饮食健康"
        )
        "卵泡期" -> listOf(
            "🌱 适合开始新的运动计划",
            "🌱 精力充沛，适合安排重要任务",
            "🌱 皮肤状态好，适合护肤",
            "🌱 可以尝试新的事物",
            "🌱 保持均衡饮食"
        )
        "黄体期" -> listOf(
            "🌙 注意休息，避免熬夜",
            "🌙 饮食清淡，避免辛辣刺激",
            "🌙 可以进行一些舒缓的运动",
            "🌙 保持心情稳定",
            "🌙 注意保暖"
        )
        else -> listOf(
            "💝 保持健康的生活方式",
            "💝 均衡饮食，适量运动",
            "💝 保持充足的睡眠",
            "💝 注意个人卫生",
            "💝 定期记录经期情况"
        )
    }
    
    // 营养建议
    val nutritionAdvice = when (currentPhase) {
        "经期" -> listOf(
            "🍓 富含铁元素的食物：瘦肉、菠菜",
            "🍓 富含维生素C的食物：橙子、猕猴桃",
            "🍓 温热的食物：热汤、热粥",
            "🍓 避免生冷食物"
        )
        "排卵期" -> listOf(
            "🍎 富含蛋白质的食物：鸡蛋、牛奶",
            "🍎 富含维生素E的食物：坚果、橄榄油",
            "🍎 新鲜蔬菜水果",
            "🍎 适量的全谷物"
        )
        "易孕期" -> listOf(
            "🥑 均衡营养，多样化饮食",
            "🥑 富含叶酸的食物：绿叶蔬菜、豆类",
            "🥑 优质蛋白质：鱼类、鸡肉",
            "🥑 适量的健康脂肪"
        )
        "卵泡期" -> listOf(
            "🌽 高纤维食物：全麦面包、燕麦",
            "🌽 富含维生素B的食物：全麦食品、瘦肉",
            "🌽 新鲜蔬菜水果",
            "🌽 适量的优质蛋白质"
        )
        "黄体期" -> listOf(
            "🥕 富含镁的食物：坚果、深绿色蔬菜",
            "🥕 富含钙的食物：奶制品、豆腐",
            "🥕 避免高盐高糖食物",
            "🥕 适量的复合碳水化合物"
        )
        else -> listOf(
            "🍇 均衡饮食，多样化选择",
            "🍇 多吃新鲜蔬菜水果",
            "🍇 适量的优质蛋白质",
            "🍇 健康的脂肪摄入"
        )
    }
    
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        // 获取协程作用域
        val coroutineScope = rememberCoroutineScope()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 顶部导航栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFF5D8E4),
                        contentColor = Color(0xFF7D5260)
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "智能建议 💡",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFED9EBC)
                )
            }
            
            // 当前周期阶段
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFED9EBC)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "当前周期阶段",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentPhase,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                
                // AI健康建议按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isLoadingAIAdvice.value = true
                                val predictor = CyclePredictor(records)
                                val advice = predictor.getAIHealthAdvice(context, currentPhase)
                                aiHealthAdvice.value = advice
                                isLoadingAIAdvice.value = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA8DADC),
                            contentColor = Color(0xFF7D5260)
                        )
                    ) {
                        if (isLoadingAIAdvice.value) {
                            CircularProgressIndicator(
                                color = Color(0xFF7D5260),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "获取中...")
                        } else {
                            Text(text = "获取AI个性化建议")
                        }
                    }
                }
                
                // 显示AI健康建议
                if (aiHealthAdvice.value != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF0F8F8)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "AI个性化健康建议",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7D5260),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = aiHealthAdvice.value!!,
                                color = Color(0xFF7D5260),
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
                
                // 健康建议
                Text(
                    text = "健康建议",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                
                healthAdvice.forEach {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF5D8E4)
                        )
                    ) {
                        Text(
                            text = it,
                            color = Color(0xFF7D5260),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                
                // 营养建议
                Text(
                    text = "营养建议",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                
                nutritionAdvice.forEach {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF5D8E4)
                        )
                    ) {
                        Text(
                            text = it,
                            color = Color(0xFF7D5260),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChartsScreen(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 记录数据，使用数据持久化
    val records = remember {
        mutableStateListOf<PeriodRecord>()
    }
    
    // 初始加载记录
    LaunchedEffect(Unit) {
        val savedRecords = RecordManager.loadRecords(context)
        if (savedRecords.isEmpty()) {
            // 默认数据
            val defaultRecords = mutableStateListOf<PeriodRecord>(
                PeriodRecord(id = "1", type = RecordType.PERIOD, startDate = "2026-01-01", endDate = "2026-01-05"),
                PeriodRecord(id = "2", type = RecordType.MOOD_SYMPTOM, date = "2026-01-03", mood = "开心", symptoms = listOf("腹痛")),
                PeriodRecord(id = "3", type = RecordType.INTIMACY, date = "2026-01-10", intimacyType = "戴套"),
                PeriodRecord(id = "4", type = RecordType.PERIOD, startDate = "2025-11-28", endDate = "2025-12-02"),
                PeriodRecord(id = "5", type = RecordType.PERIOD, startDate = "2025-10-25", endDate = "2025-10-29")
            )
            records.clear()
            records.addAll(defaultRecords)
            Log.d(TAG, "ChartsScreen: 创建默认数据，共${defaultRecords.size}条记录")
            // 保存默认数据
            RecordManager.saveRecords(context, defaultRecords)
        } else {
            records.clear()
            records.addAll(savedRecords)
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 顶部导航栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFF5D8E4),
                        contentColor = Color(0xFF7D5260)
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "图表",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFED9EBC)
                )
            }
            
            // 简单的图表显示
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "周期趋势图", fontSize = 16.sp, color = Color(0xFF7D5260))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "症状分布图", fontSize = 16.sp, color = Color(0xFF7D5260))
            }
        }
    }
}

@Composable
fun RemindersScreen(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val (periodReminder, ovulationReminder, fertileReminder) = loadReminderSettings(context)
    
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 顶部导航栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFF5D8E4),
                        contentColor = Color(0xFF7D5260)
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "提醒",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFED9EBC)
                )
            }
            
            // 提醒设置
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "当前提醒设置",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(if (periodReminder) Color(0xFFED9EBC) else Color(0xFFF5D8E4), shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "经期提醒", color = Color(0xFF7D5260))
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(if (ovulationReminder) Color(0xFFED9EBC) else Color(0xFFF5D8E4), shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "排卵期提醒", color = Color(0xFF7D5260))
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(if (fertileReminder) Color(0xFFED9EBC) else Color(0xFFF5D8E4), shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "易孕期提醒", color = Color(0xFF7D5260))
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "提醒说明",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = "• 经期提醒: 在预测的经期开始前1天发送提醒",
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(8.dp)
                )
                Text(
                    text = "• 排卵期提醒: 在预测的排卵日当天发送提醒",
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(8.dp)
                )
                Text(
                    text = "• 易孕期提醒: 在预测的易孕期开始前1天发送提醒",
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun StatsScreen(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 记录数据，使用数据持久化
    val records = remember {
        mutableStateListOf<PeriodRecord>()
    }
    
    // AI统计分析状态
    val aiStatsAnalysis = remember {
        mutableStateOf<String?>(null)
    }
    val isLoadingAIStats = remember {
        mutableStateOf(false)
    }
    
    // 初始加载记录
    LaunchedEffect(Unit) {
        val savedRecords = RecordManager.loadRecords(context)
        if (savedRecords.isEmpty()) {
            // 默认数据
            val defaultRecords = mutableStateListOf<PeriodRecord>(
                PeriodRecord(id = "1", type = RecordType.PERIOD, startDate = "2026-01-01", endDate = "2026-01-05"),
                PeriodRecord(id = "2", type = RecordType.MOOD_SYMPTOM, date = "2026-01-03", mood = "开心", symptoms = listOf("腹痛")),
                PeriodRecord(id = "3", type = RecordType.INTIMACY, date = "2026-01-10", intimacyType = "戴套"),
                PeriodRecord(id = "4", type = RecordType.PERIOD, startDate = "2025-11-28", endDate = "2025-12-02"),
                PeriodRecord(id = "5", type = RecordType.PERIOD, startDate = "2025-10-25", endDate = "2025-10-29")
            )
            records.clear()
            records.addAll(defaultRecords)
            Log.d(TAG, "StatsScreen: 创建默认数据，共${defaultRecords.size}条记录")
            // 保存默认数据
            RecordManager.saveRecords(context, defaultRecords)
        } else {
            records.clear()
            records.addAll(savedRecords)
        }
    }
    
    // 计算统计数据
    val periodRecords = records.filter { it.type == RecordType.PERIOD && it.startDate != null && it.endDate != null }
    
    // 周期长度统计
    val cycleLengths = mutableListOf<Long>()
    if (periodRecords.size >= 2) {
        for (i in 1 until periodRecords.size) {
            val start1 = LocalDate.parse(periodRecords[i-1].startDate)
            val start2 = LocalDate.parse(periodRecords[i].startDate)
            cycleLengths.add(ChronoUnit.DAYS.between(start1, start2))
        }
    }
    
    val averageCycleLength = if (cycleLengths.isNotEmpty()) cycleLengths.average() else 0.0
    val maxCycleLength = if (cycleLengths.isNotEmpty()) cycleLengths.maxOrNull() else 0
    val minCycleLength = if (cycleLengths.isNotEmpty()) cycleLengths.minOrNull() else 0
    val cycleRegularity = if (cycleLengths.size >= 3) {
        val values = cycleLengths.map { it.toDouble() }
        val mean = values.average()
        val squaredDifferences = values.map { Math.pow(it - mean, 2.0) }
        val stdDev = Math.sqrt(squaredDifferences.average())
        val regularityScore = (100 - stdDev * 5).coerceIn(0.0, 100.0)
        regularityScore
    } else {
        0.0
    }
    
    // 经期长度统计
    val periodLengths = periodRecords.map { 
        val start = LocalDate.parse(it.startDate)
        val end = LocalDate.parse(it.endDate)
        ChronoUnit.DAYS.between(start, end) + 1
    }
    
    val averagePeriodLength = if (periodLengths.isNotEmpty()) periodLengths.average() else 0.0
    val maxPeriodLength = if (periodLengths.isNotEmpty()) periodLengths.maxOrNull() else 0L
    val minPeriodLength = if (periodLengths.isNotEmpty()) periodLengths.minOrNull() else 0L
    
    val moodCounts = records
        .filter { it.type == RecordType.MOOD_SYMPTOM && it.mood != null }
        .groupBy { it.mood }
        .mapValues { it.value.size }
    
    val symptomCounts = records
        .filter { it.type == RecordType.MOOD_SYMPTOM }
        .flatMap { it.symptoms }
        .groupBy { it }
        .mapValues { it.value.size }
    
    // 亲密记录统计
    val intimacyRecords = records.filter { it.type == RecordType.INTIMACY && it.intimacyType != null }
    val intimacyCount = intimacyRecords.size
    val intimacyTypeCounts = intimacyRecords
        .groupBy { it.intimacyType }
        .mapValues { it.value.size }
    val recentIntimacyRecord = intimacyRecords
        .maxByOrNull { it.date ?: "" }
        ?.date
    
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        // 获取协程作用域
        val coroutineScope = rememberCoroutineScope()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 顶部导航栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFF5D8E4),
                        contentColor = Color(0xFF7D5260)
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "统计分析 📊",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFED9EBC)
                )
            }
            
            // 统计数据
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "周期统计",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // AI统计分析按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isLoadingAIStats.value = true
                                val predictor = CyclePredictor(records)
                                val analysis = predictor.getAIStatsAnalysis(context)
                                aiStatsAnalysis.value = analysis
                                isLoadingAIStats.value = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA8DADC),
                            contentColor = Color(0xFF7D5260)
                        )
                    ) {
                        if (isLoadingAIStats.value) {
                            CircularProgressIndicator(
                                color = Color(0xFF7D5260),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "分析中...")
                        } else {
                            Text(text = "获取AI统计分析")
                        }
                    }
                }
                
                // 显示AI统计分析结果
                if (aiStatsAnalysis.value != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF0F8F8)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "AI统计分析结果",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7D5260),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = aiStatsAnalysis.value!!,
                                color = Color(0xFF7D5260),
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF5D8E4)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(text = "平均周期长度: %.1f 天".format(averageCycleLength), color = Color(0xFF7D5260))
                        Text(text = "最长周期: ${maxCycleLength ?: 0} 天", color = Color(0xFF7D5260))
                        Text(text = "最短周期: ${minCycleLength ?: 0} 天", color = Color(0xFF7D5260))
                        Text(text = "周期规律性: %.1f%%".format(cycleRegularity), color = Color(0xFF7D5260))
                        Text(text = "平均经期长度: %.1f 天".format(averagePeriodLength), color = Color(0xFF7D5260))
                        Text(text = "最长经期: ${maxPeriodLength ?: 0} 天", color = Color(0xFF7D5260))
                        Text(text = "最短经期: ${minPeriodLength ?: 0} 天", color = Color(0xFF7D5260))
                        Text(text = "记录次数: ${records.size}", color = Color(0xFF7D5260))
                        Text(text = "经期记录: ${periodRecords.size} 次", color = Color(0xFF7D5260))
                    }
                }
                
                Text(
                    text = "心情统计",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                moodCounts.forEach { (mood, count) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF5D8E4)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(text = "$mood: $count 次", color = Color(0xFF7D5260))
                        }
                    }
                }
                
                Text(
                    text = "症状统计",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                symptomCounts.forEach { (symptom, count) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF5D8E4)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(text = "$symptom: $count 次", color = Color(0xFF7D5260))
                        }
                    }
                }
                
                // 亲密记录统计
                Text(
                    text = "亲密记录统计 💕",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFD6A5)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(text = "总次数: $intimacyCount 次", color = Color(0xFF7D5260))
                        if (recentIntimacyRecord != null) {
                            Text(text = "最近记录: $recentIntimacyRecord", color = Color(0xFF7D5260))
                        } else {
                            Text(text = "最近记录: 无", color = Color(0xFF7D5260))
                        }
                    }
                }
                
                intimacyTypeCounts.forEach { (type, count) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFA8DADC)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(text = "$type: $count 次", color = Color(0xFF7D5260))
                        }
                    }
                }
            }
        }
    }
}
