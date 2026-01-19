package com.example.yuejing

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import java.io.File
import kotlinx.serialization.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import com.example.yuejing.widget.CycleWidgetProvider
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore as AndroidMediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale

import com.example.yuejing.model.WidgetSettings

@Composable
fun WidgetSettingsScreen(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // 背景颜色选项
    val backgroundOptions = listOf(
        "粉色 (默认)" to Color(0xFFF5D8E4),
        "深粉色" to Color(0xFFED9EBC),
        "蓝色" to Color(0xFFA8DADC),
        "橙色" to Color(0xFFFFD6A5),
        "紫色" to Color(0xFFC9A0DC),
        "绿色" to Color(0xFFA8E6CF),
        "黄色" to Color(0xFFFFF9C4),
        "白色" to Color(0xFFFFFFFF)
    )
    
    // 加载当前设置
    val widgetSettings = loadWidgetSettings(context)
    val selectedBackground = remember {
        mutableStateOf(widgetSettings.second)
    }
    val selectedBackgroundName = remember {
        mutableStateOf(widgetSettings.first)
    }
    val selectedBackgroundImagePath = remember {
        mutableStateOf(widgetSettings.third)
    }
    
    // 处理照片选择
    val selectPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data: Intent? = result.data
            data?.data?.let { uri ->
                // 保存图片到应用内部存储
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    
                    // 创建应用内部存储目录
                    val directory = File(context.filesDir, "widget_backgrounds")
                    if (!directory.exists()) {
                        directory.mkdirs()
                    }
                    
                    // 保存图片文件
                    val file = File(directory, "background_${System.currentTimeMillis()}.jpg")
                    val outputStream = file.outputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    outputStream.close()
                    
                    // 更新状态
                    selectedBackgroundImagePath.value = file.absolutePath
                    
                    // 保存设置
                    saveWidgetSettings(
                        context,
                        selectedBackgroundName.value,
                        selectedBackground.value,
                        file.absolutePath
                    )
                    
                    // 更新小部件
                    CycleWidgetProvider.updateWidgets(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
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
                    text = "小部件设置 🎨",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFED9EBC)
                )
            }
            
            // 设置内容
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 背景图片设置
                Text(
                    text = "背景图片",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_PICK, AndroidMediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                            selectPhotoLauncher.launch(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA8DADC),
                            contentColor = Color(0xFF7D5260)
                        )
                    ) {
                        Text(text = "从相册选择")
                    }
                    
                    if (selectedBackgroundImagePath.value != null) {
                        Button(
                            onClick = {
                                // 删除背景图片
                                val file = File(selectedBackgroundImagePath.value!!)
                                if (file.exists()) {
                                    file.delete()
                                }
                                selectedBackgroundImagePath.value = null
                                
                                // 保存设置
                                saveWidgetSettings(
                                    context,
                                    selectedBackgroundName.value,
                                    selectedBackground.value,
                                    null
                                )
                                
                                // 更新小部件
                                CycleWidgetProvider.updateWidgets(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF44336),
                                contentColor = Color.White
                            )
                        ) {
                            Text(text = "移除背景图片")
                        }
                    }
                }
                
                // 当前背景图片预览
                if (selectedBackgroundImagePath.value != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation()
                    ) {
                        androidx.compose.foundation.Image(
                            painter = BitmapPainter(BitmapFactory.decodeFile(selectedBackgroundImagePath.value!!).asImageBitmap()),
                            contentDescription = "当前背景图片",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "背景颜色",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 背景颜色选择网格
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    backgroundOptions.forEachIndexed { index, (name, color) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .background(
                                    if (selectedBackground.value == color) Color(0xFFF0F0F0) else Color.Transparent,
                                    shape = MaterialTheme.shapes.medium
                                )
                                .clickable {
                                    selectedBackground.value = color
                                    selectedBackgroundName.value = name
                                    // 清除背景图片，使用颜色背景
                                selectedBackgroundImagePath.value = null
                                saveWidgetSettings(
                                    context,
                                    name,
                                    color,
                                    null
                                )
                                    // 更新小部件
                                    CycleWidgetProvider.updateWidgets(context)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(color, shape = MaterialTheme.shapes.small)
                                    .border(
                                        2.dp,
                                        if (selectedBackground.value == color) Color(0xFF7D5260) else Color.Transparent,
                                        shape = MaterialTheme.shapes.small
                                    )
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = name, color = Color(0xFF7D5260))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
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
                        Text(
                            text = "提示",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7D5260)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• 选择背景后，小部件会自动更新",
                            fontSize = 12.sp,
                            color = Color(0xFF7D5260)
                        )
                        Text(
                            text = "• 如果小部件没有更新，请先删除再重新添加",
                            fontSize = 12.sp,
                            color = Color(0xFF7D5260)
                        )
                        Text(
                            text = "• 背景图片会优先于背景颜色显示",
                            fontSize = 12.sp,
                            color = Color(0xFF7D5260)
                        )
                        Text(
                            text = "• 建议使用尺寸合适的图片以获得最佳效果",
                            fontSize = 12.sp,
                            color = Color(0xFF7D5260)
                        )
                    }
                }
            }
        }
    }
}

// 保存小部件设置
fun saveWidgetSettings(context: android.content.Context, backgroundName: String, backgroundColor: Color, backgroundImagePath: String? = null) {
    try {
        val file = File(context.filesDir, "widget_settings.json")
        // 手动计算ARGB值
        val argb = (backgroundColor.alpha * 255).toInt() shl 24 or
                  (backgroundColor.red * 255).toInt() shl 16 or
                  (backgroundColor.green * 255).toInt() shl 8 or
                  (backgroundColor.blue * 255).toInt()
        val settings = WidgetSettings(
            background_name = backgroundName,
            background_color = argb,
            background_image_path = backgroundImagePath
        )
        val settingsJson = Json.encodeToString(settings)
        file.writeText(settingsJson)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// 加载小部件设置
fun loadWidgetSettings(context: android.content.Context): Triple<String, Color, String?> {
    try {
        val file = File(context.filesDir, "widget_settings.json")
        if (!file.exists()) {
            return Triple("粉色 (默认)", Color(0xFFF5D8E4), null)
        }
        val settingsJson = file.readText()
        val settings = Json.decodeFromString<WidgetSettings>(settingsJson)
        val backgroundColor = Color(settings.background_color)
        return Triple(settings.background_name, backgroundColor, settings.background_image_path)
    } catch (e: Exception) {
        e.printStackTrace()
        return Triple("粉色 (默认)", Color(0xFFF5D8E4), null)
    }
}
