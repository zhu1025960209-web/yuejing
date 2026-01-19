package com.example.yuejing.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuejing.data.model.PreparationItem
import com.example.yuejing.utils.PartnerManager
import com.example.yuejing.domain.predictor.CyclePredictor
import com.example.yuejing.data.model.PeriodRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import com.example.yuejing.ui.components.CustomDatePicker
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PregnancyPreparationScreen(
    partnerManager: PartnerManager,
    onBackClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pregnancyPrep = remember {
        mutableStateOf(partnerManager.getPregnancyPreparation())
    }
    val preparationItems = pregnancyPrep.value?.preparationItems ?: emptyList()
    val targetConceptionDate = pregnancyPrep.value?.targetConceptionDate
    
    // AI建议状态
    val aiPregnancyAdvice = remember {
        mutableStateOf<String?>(null)
    }
    val isLoadingAIAdvice = remember {
        mutableStateOf(false)
    }
    
    // 日期选择器状态
    var showDatePicker by remember {
        mutableStateOf(false)
    }
    var initialDate by remember {
        mutableStateOf(LocalDate.now().plusMonths(3))
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "孕期准备",
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
                actions = {
                    IconButton(onClick = {
                        // 添加新项目
                        val newItem = PreparationItem(
                            id = "item_${System.currentTimeMillis()}",
                            title = "新准备项目",
                            description = "请输入项目描述",
                            isCompleted = false,
                            deadline = LocalDate.now().plusDays(7).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                            assignedTo = "user"
                        )
                        val currentPrep = pregnancyPrep.value ?: com.example.yuejing.data.model.PregnancyPreparation()
                        val updatedItems = currentPrep.preparationItems + newItem
                        val updatedPrep = currentPrep.copy(preparationItems = updatedItems)
                        partnerManager.savePregnancyPreparation(updatedPrep)
                        pregnancyPrep.value = updatedPrep
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "添加",
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
        // 获取协程作用域
        val coroutineScope = rememberCoroutineScope()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 目标受孕日期卡片
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
                            text = "目标受孕日期",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7D5260)
                        )
                        if (targetConceptionDate != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDate(targetConceptionDate),
                                    fontSize = 16.sp,
                                    color = Color(0xFF7D5260)
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            // 编辑目标日期
                                            try {
                                                val currentDate = LocalDate.parse(targetConceptionDate)
                                                initialDate = currentDate
                                            } catch (e: Exception) {
                                                // 如果日期解析失败，使用当前日期
                                                initialDate = LocalDate.now().plusMonths(3)
                                            }
                                            showDatePicker = true
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "编辑",
                                            tint = Color(0xFF7D5260)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            // 删除目标日期
                                            val currentPrep = pregnancyPrep.value ?: com.example.yuejing.data.model.PregnancyPreparation()
                                            val updatedPrep = currentPrep.copy(targetConceptionDate = null)
                                            partnerManager.savePregnancyPreparation(updatedPrep)
                                            pregnancyPrep.value = updatedPrep
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "删除",
                                            tint = Color(0xFFF44336)
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "未设置",
                                fontSize = 16.sp,
                                color = Color(0xFFED9EBC)
                            )
                        }
                        Button(
                            onClick = {
                                // 打开日期选择器
                                showDatePicker = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFED9EBC),
                                contentColor = Color.White
                            )
                        ) {
                            Text(text = if (targetConceptionDate != null) "修改目标日期" else "设置目标日期")
                        }
                    }
                }
            }
            
            // AI孕期准备建议卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF0F8F8)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "AI孕期准备建议",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7D5260)
                        )
                        
                        // AI建议按钮
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
                                        // 生成AI建议所需的数据
                                        val prepData = "目标受孕日期: ${if (targetConceptionDate != null) formatDate(targetConceptionDate) else "未设置"}\n" + 
                                        "准备项目数量: ${preparationItems.size}\n" + 
                                        "已完成项目数量: ${preparationItems.filter { it.isCompleted }.size}\n"
                                        
                                        // 调用AI建议
                                        val deepSeekManager = com.example.yuejing.utils.DeepSeekManager(context)
                                        val advice = deepSeekManager.getPregnancyAdvice(prepData)
                                        aiPregnancyAdvice.value = advice
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
                                    Text(text = "获取AI孕期准备建议")
                                }
                            }
                        }
                        
                        // 显示AI建议
                        if (aiPregnancyAdvice.value != null) {
                            Text(
                                text = aiPregnancyAdvice.value!!,
                                color = Color(0xFF7D5260),
                                lineHeight = 22.sp
                            )
                        } else {
                            Text(
                                text = "点击按钮获取AI个性化孕期准备建议",
                                color = Color(0xFFED9EBC),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
            
            // 准备项目列表
            item {
                Text(
                    text = "准备项目",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            
            if (preparationItems.isEmpty()) {
                item {
                    EmptyPreparationState()
                }
            } else {
                items(preparationItems) { item ->
                    PreparationItemCard(
                        item = item,
                        onToggleComplete = {
                            // 切换完成状态
                            val currentPrep = pregnancyPrep.value ?: com.example.yuejing.data.model.PregnancyPreparation()
                            val updatedItems = currentPrep.preparationItems.map {
                                if (it.id == item.id) it.copy(isCompleted = !it.isCompleted) else it
                            }
                            val updatedPrep = currentPrep.copy(preparationItems = updatedItems)
                            partnerManager.savePregnancyPreparation(updatedPrep)
                            pregnancyPrep.value = updatedPrep
                        },
                        onEdit = {
                            // 编辑项目（简化版本：更新描述）
                            val currentPrep = pregnancyPrep.value ?: com.example.yuejing.data.model.PregnancyPreparation()
                            val updatedItems = currentPrep.preparationItems.map {
                                if (it.id == item.id) it.copy(description = "已更新的描述") else it
                            }
                            val updatedPrep = currentPrep.copy(preparationItems = updatedItems)
                            partnerManager.savePregnancyPreparation(updatedPrep)
                            pregnancyPrep.value = updatedPrep
                        },
                        onDelete = {
                            // 删除项目
                            val currentPrep = pregnancyPrep.value ?: com.example.yuejing.data.model.PregnancyPreparation()
                            val updatedItems = currentPrep.preparationItems.filter { it.id != item.id }
                            val updatedPrep = currentPrep.copy(preparationItems = updatedItems)
                            partnerManager.savePregnancyPreparation(updatedPrep)
                            pregnancyPrep.value = updatedPrep
                        }
                    )
                }
            }
            
            // 添加项目按钮
            item {
                Button(
                    onClick = { 
                        // 添加新项目
                        val newItem = PreparationItem(
                            id = "item_${System.currentTimeMillis()}",
                            title = "新准备项目",
                            description = "请输入项目描述",
                            isCompleted = false,
                            deadline = LocalDate.now().plusDays(7).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                            assignedTo = "user"
                        )
                        val currentPrep = pregnancyPrep.value ?: com.example.yuejing.data.model.PregnancyPreparation()
                        val updatedItems = currentPrep.preparationItems + newItem
                        val updatedPrep = currentPrep.copy(preparationItems = updatedItems)
                        partnerManager.savePregnancyPreparation(updatedPrep)
                        pregnancyPrep.value = updatedPrep
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFA8DADC),
                        contentColor = Color(0xFF7D5260)
                    )
                ) {
                    Text(text = "添加准备项目")
                }
            }
        }
    }
    
    // 日期选择器
    if (showDatePicker) {
        CustomDatePicker(
            initialDate = initialDate,
            onDateSelected = { date ->
                val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val currentPrep = pregnancyPrep.value ?: com.example.yuejing.data.model.PregnancyPreparation()
                val updatedPrep = currentPrep.copy(targetConceptionDate = dateStr)
                partnerManager.savePregnancyPreparation(updatedPrep)
                pregnancyPrep.value = updatedPrep
            },
            onDismiss = {
                showDatePicker = false
            }
        )
    }
}

// 准备项目卡片
@Composable
private fun PreparationItemCard(
    item: PreparationItem,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCompleted) Color(0xFFF0F8F8) else Color(0xFFF8E1EB)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7D5260),
                    modifier = Modifier.weight(1f)
                )
                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = { onToggleComplete() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFFED9EBC),
                        uncheckedColor = Color(0xFF7D5260)
                    )
                )
            }
            Text(
                text = item.description,
                fontSize = 14.sp,
                color = Color(0xFF7D5260)
            )
            if (item.deadline != null) {
                Text(
                    text = "截止日期: ${formatDate(item.deadline)}",
                    fontSize = 12.sp,
                    color = Color(0xFF7D5260).copy(alpha = 0.7f)
                )
            }
            if (item.assignedTo != null) {
                Text(
                    text = "负责人: ${if (item.assignedTo == "user") "我" else "伴侣"}",
                    fontSize = 12.sp,
                    color = Color(0xFF7D5260).copy(alpha = 0.7f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEdit) {
                    Text(
                        text = "编辑",
                        color = Color(0xFF7D5260)
                    )
                }
                TextButton(onClick = onDelete) {
                    Text(
                        text = "删除",
                        color = Color(0xFFF44336)
                    )
                }
            }
        }
    }
}

// 空准备状态
@Composable
private fun EmptyPreparationState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "空准备",
            tint = Color(0xFFED9EBC),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "还没有准备项目",
            color = Color(0xFF7D5260),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击右上角添加按钮开始准备",
            color = Color(0xFFED9EBC),
            fontSize = 14.sp
        )
    }
}

// 格式化日期
private fun formatDate(dateString: String): String {
    return try {
        val date = LocalDate.parse(dateString)
        val formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
        date.format(formatter)
    } catch (e: Exception) {
        dateString
    }
}
