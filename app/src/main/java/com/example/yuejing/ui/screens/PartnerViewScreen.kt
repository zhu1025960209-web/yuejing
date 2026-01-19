package com.example.yuejing.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuejing.utils.PartnerManager
import com.example.yuejing.data.model.PeriodRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import com.example.yuejing.RecordManager
import com.example.yuejing.domain.predictor.CyclePredictor
import com.example.yuejing.data.model.PartnerInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerViewScreen(
    partnerManager: PartnerManager,
    onBackClick: () -> Unit
) {
    val partnerInfo = partnerManager.getPartnerInfo()
    val sharingSettings = partnerManager.getSharingSettings()
    val localRecords = remember {
        mutableStateOf(emptyList<PeriodRecord>())
    }
    
    val localContext = LocalContext.current
    
    // 伴侣信息编辑状态
    val isEditing = remember { mutableStateOf(false) }
    val nameState = remember { mutableStateOf(partnerInfo?.name ?: "") }
    val heightState = remember { mutableStateOf(partnerInfo?.height ?: "") }
    val weightState = remember { mutableStateOf(partnerInfo?.weight ?: "") }
    val currentDate = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    
    // 加载本地记录
    LaunchedEffect(Unit) {
        val records = RecordManager.loadRecords(localContext)
        localRecords.value = records
    }
    
    // 使用CyclePredictor计算预测结果
    val predictor = CyclePredictor(localRecords.value)
    val predictionDates = predictor.predictNextPeriod()
    
    // 计算当前周期状态
    val currentCycleStatus = calculateCurrentCycleStatus(localRecords.value, predictionDates)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "伴侣视图",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7D5260)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color(0xFF7D5260)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF5D8E4)
                )
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 伴侣信息卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF8E1EB)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "伴侣信息",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7D5260)
                            )
                            Button(
                                onClick = { isEditing.value = !isEditing.value },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFED9EBC),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.size(width = 80.dp, height = 32.dp)
                            ) {
                                Text(text = if (isEditing.value) "保存" else "编辑", fontSize = 12.sp)
                            }
                        }
                        
                        if (isEditing.value) {
                            // 编辑模式
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "姓名:", color = Color(0xFF7D5260), modifier = Modifier.width(60.dp))
                                    OutlinedTextField(
                                        value = nameState.value,
                                        onValueChange = { nameState.value = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text(text = "请输入姓名") },
                                        singleLine = true
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "身高:", color = Color(0xFF7D5260), modifier = Modifier.width(60.dp))
                                    OutlinedTextField(
                                        value = heightState.value,
                                        onValueChange = { heightState.value = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text(text = "请输入身高，如170cm") },
                                        singleLine = true
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "体重:", color = Color(0xFF7D5260), modifier = Modifier.width(60.dp))
                                    OutlinedTextField(
                                        value = weightState.value,
                                        onValueChange = { weightState.value = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text(text = "请输入体重，如50kg") },
                                        singleLine = true
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            // 保存伴侣信息
                                            val updatedPartnerInfo = PartnerInfo(
                                                id = partnerInfo?.id ?: "partner_${System.currentTimeMillis()}",
                                                name = nameState.value,
                                                height = heightState.value,
                                                weight = weightState.value,
                                                isConnected = true,
                                                lastSyncTime = System.currentTimeMillis().toString()
                                            )
                                            partnerManager.savePartnerInfo(updatedPartnerInfo)
                                            isEditing.value = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4CAF50),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.size(width = 80.dp, height = 32.dp)
                                    ) {
                                        Text(text = "保存", fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            // 取消编辑，恢复原始值
                                            nameState.value = partnerInfo?.name ?: ""
                                            heightState.value = partnerInfo?.height ?: ""
                                            weightState.value = partnerInfo?.weight ?: ""
                                            isEditing.value = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFF44336),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.size(width = 80.dp, height = 32.dp)
                                    ) {
                                        Text(text = "取消", fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            // 查看模式
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "姓名:", color = Color(0xFF7D5260))
                                    Text(text = partnerInfo?.name ?: "未设置", color = Color(0xFF7D5260))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "身高:", color = Color(0xFF7D5260))
                                    Text(text = partnerInfo?.height ?: "未设置", color = Color(0xFF7D5260))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "体重:", color = Color(0xFF7D5260))
                                    Text(text = partnerInfo?.weight ?: "未设置", color = Color(0xFF7D5260))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "连接状态:", color = Color(0xFF7D5260))
                                    Text(
                                        text = if (partnerManager.isPartnerConnected()) "已连接" else "未连接",
                                        color = if (partnerManager.isPartnerConnected()) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // 当前周期状态卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF8E1EB)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "当前周期状态",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7D5260)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = currentCycleStatus.status,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = currentCycleStatus.color
                            )
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = currentCycleStatus.status,
                                tint = currentCycleStatus.color,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        if (currentCycleStatus.nextEvent != null) {
                            Text(
                                text = currentCycleStatus.nextEvent,
                                fontSize = 14.sp,
                                color = Color(0xFF7D5260)
                            )
                        }
                    }
                }
            }
            
            // 共享设置卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF8E1EB)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "共享设置",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7D5260)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "经期提醒", color = Color(0xFF7D5260))
                            Switch(
                                checked = sharingSettings.sharePeriodReminders,
                                onCheckedChange = { },
                                enabled = false,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFED9EBC),
                                    checkedTrackColor = Color(0xFFED9EBC).copy(alpha = 0.5f)
                                )
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "排卵期提醒", color = Color(0xFF7D5260))
                            Switch(
                                checked = sharingSettings.shareOvulationReminders,
                                onCheckedChange = { },
                                enabled = false,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFED9EBC),
                                    checkedTrackColor = Color(0xFFED9EBC).copy(alpha = 0.5f)
                                )
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "易孕期提醒", color = Color(0xFF7D5260))
                            Switch(
                                checked = sharingSettings.shareFertileReminders,
                                onCheckedChange = { },
                                enabled = false,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFED9EBC),
                                    checkedTrackColor = Color(0xFFED9EBC).copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }
            
            // 近期事件卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF8E1EB)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "近期事件",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7D5260)
                        )
                        // 这里可以添加近期事件列表
                        Text(
                            text = "- 下次经期: 预计 ${calculateNextPeriodDate(localRecords.value)}",
                            color = Color(0xFF7D5260)
                        )
                        Text(
                            text = "- 下次排卵期: 预计 ${calculateNextOvulationDate(localRecords.value)}",
                            color = Color(0xFF7D5260)
                        )
                    }
                }
            }
        }
    }
}

// 周期状态数据类
private data class CycleStatus(
    val status: String,
    val color: Color,
    val nextEvent: String? = null
)

// 计算当前周期状态
private fun calculateCurrentCycleStatus(records: List<PeriodRecord>, predictionDates: List<LocalDate>): CycleStatus {
    val today = LocalDate.now()
    
    // 计算当前处于周期的哪个阶段，与小部件保持一致
    val nextPeriodStart = predictionDates[0]
    val nextPeriodEnd = predictionDates[1]
    val ovulationDate = predictionDates[2]
    val fertileStart = predictionDates[3]
    val fertileEnd = predictionDates[4]
    
    // 计算距离各阶段的天数
    val daysToNextPeriod = ChronoUnit.DAYS.between(today, nextPeriodStart)
    val daysToOvulation = ChronoUnit.DAYS.between(today, ovulationDate)
    val daysToFertileStart = ChronoUnit.DAYS.between(today, fertileStart)
    
    // 确定当前周期阶段，优化状态提示
    return when {
        // 经期
        today >= nextPeriodStart && today <= nextPeriodEnd -> {
            CycleStatus(
                status = "经期",
                color = Color(0xFFF44336),
                nextEvent = "经期预计${ChronoUnit.DAYS.between(today, nextPeriodEnd) + 1}天结束"
            )
        }
        // 排卵期前后5天
        daysToOvulation in -5L..5L -> {
            val status = when {
                daysToOvulation < -2L -> "排卵期刚过"
                daysToOvulation == -2L || daysToOvulation == -1L -> "排卵期刚过"
                daysToOvulation == 0L -> "排卵期"
                daysToOvulation == 1L || daysToOvulation == 2L -> "即将进入排卵期"
                else -> "即将进入排卵期"
            }
            val nextEvent = when {
                daysToOvulation < 0 -> "排卵期已过${-daysToOvulation}天"
                daysToOvulation > 0 -> "排卵期还有${daysToOvulation}天"
                else -> "今天是排卵期"
            }
            CycleStatus(
                status = status,
                color = Color(0xFFF5D8E4),
                nextEvent = nextEvent
            )
        }
        // 易孕期
        today >= fertileStart && today <= fertileEnd -> {
            CycleStatus(
                status = "易孕期",
                color = Color(0xFFFF9800),
                nextEvent = "易孕期预计还有${ChronoUnit.DAYS.between(today, fertileEnd)}天结束"
            )
        }
        // 易孕期即将到来（提前3天提示）
        daysToFertileStart in 1L..3L -> {
            CycleStatus(
                status = "卵泡期",
                color = Color(0xFF4CAF50),
                nextEvent = "即将进入易孕期，还有${daysToFertileStart}天"
            )
        }
        // 卵泡期
        today < fertileStart -> {
            val nextEvent = when {
                daysToOvulation > 7L -> "排卵期还有${daysToOvulation}天"
                else -> "即将进入排卵期，还有${daysToOvulation}天"
            }
            CycleStatus(
                status = "卵泡期",
                color = Color(0xFF4CAF50),
                nextEvent = nextEvent
            )
        }
        // 黄体期（距离下次经期不同天数的提示）
        else -> {
            val status = "黄体期"
            val nextEvent = when {
                daysToNextPeriod > 7L -> "距离下次经期还有${daysToNextPeriod}天"
                daysToNextPeriod > 3L -> "即将进入经期，还有${daysToNextPeriod}天"
                daysToNextPeriod > 0L -> "即将进入经期，还有${daysToNextPeriod}天"
                else -> "经期即将开始"
            }
            CycleStatus(
                status = status,
                color = Color(0xFF2196F3),
                nextEvent = nextEvent
            )
        }
    }
}

// 计算下次经期日期
private fun calculateNextPeriodDate(records: List<PeriodRecord>): String {
    // 使用CyclePredictor计算预测结果
    val predictor = CyclePredictor(records)
    val predictionDates = predictor.predictNextPeriod()
    
    // 返回下次经期开始日期
    return predictionDates[0].format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}

// 计算下次排卵期日期
private fun calculateNextOvulationDate(records: List<PeriodRecord>): String {
    // 使用CyclePredictor计算预测结果
    val predictor = CyclePredictor(records)
    val predictionDates = predictor.predictNextPeriod()
    
    // 返回排卵期日期
    return predictionDates[2].format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}
