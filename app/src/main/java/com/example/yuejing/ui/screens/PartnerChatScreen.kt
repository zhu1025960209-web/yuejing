package com.example.yuejing.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuejing.data.model.PartnerMessage
import com.example.yuejing.utils.PartnerManager
import com.example.yuejing.utils.SyncManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Email

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerChatScreen(
    partnerManager: PartnerManager,
    onBackClick: () -> Unit
) {
    val partnerInfo = partnerManager.getPartnerInfo()
    val messages = remember {
        mutableStateOf(partnerManager.getPartnerMessages())
    }
    val messageInput = remember {
        mutableStateOf("")
    }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    
    // 进入聊天界面时自动同步最新消息
    LaunchedEffect(true) {
        // 首先直接加载本地已有的消息
        messages.value = partnerManager.getPartnerMessages()
        
        // 然后尝试同步最新消息
        val syncManager = SyncManager(context)
        
        // 检查是否有Gist ID，只有有Gist ID时才尝试同步
        if (syncManager.getGistId() != null) {
            // 首次同步
            try {
                syncManager.downloadPartnerSharingState()
                // 同步完成后更新消息列表
                messages.value = partnerManager.getPartnerMessages()
            } catch (e: Exception) {
                // 忽略同步错误，继续显示本地消息
            }
            
            // 定期同步消息（每隔5秒）
            while (true) {
                delay(5000) // 5秒延迟
                try {
                    syncManager.downloadPartnerSharingState()
                    // 同步完成后更新消息列表
                    messages.value = partnerManager.getPartnerMessages()
                } catch (e: Exception) {
                    // 忽略同步错误，继续显示本地消息
                }
            }
        }
    }
    
    // 自动滚动到最新消息
    LaunchedEffect(messages.value.size) {
        if (messages.value.isNotEmpty()) {
            listState.scrollToItem(messages.value.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "与${partnerInfo?.name ?: "伴侣"}聊天",
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
        },
        bottomBar = {
            MessageInputBar(
                message = messageInput.value,
                onMessageChange = { messageInput.value = it },
                onSendClick = {
                    if (it.isNotBlank()) {
                        partnerManager.sendPartnerMessage(it)
                        messageInput.value = ""
                        // 发送消息后更新消息列表
                        messages.value = partnerManager.getPartnerMessages()
                    }
                }
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.value.isEmpty()) {
                item {
                    EmptyChatState()
                }
            } else {
                messages.value.forEachIndexed { index, message ->
                    item {
                        MessageItem(message = message)
                    }
                }
            }
        }
    }
}

// 消息输入栏
@Composable
private fun MessageInputBar(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF8E1EB)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier
                    .weight(1f),
                placeholder = {
                    Text(
                        text = "输入消息...",
                        color = Color(0xFFED9EBC),
                        fontSize = 16.sp
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color(0xFF7D5260),
                    unfocusedTextColor = Color(0xFF7D5260),
                    focusedPlaceholderColor = Color(0xFFED9EBC),
                    unfocusedPlaceholderColor = Color(0xFFED9EBC)
                )
            )
            IconButton(
                onClick = { onSendClick(message) },
                enabled = message.isNotBlank(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xFFED9EBC),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "发送"
                )
            }
        }
    }
}

// 消息项
@Composable
private fun MessageItem(message: PartnerMessage) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val userGender = sharedPreferences.getString("user_gender", "")
    
    // 根据当前用户性别和发送者性别判断消息显示位置
    // 这个手机输入的匹配码是男的，那么男生发的消息就在右边
    // 这个手机输入的匹配码是女的，那么女生发的消息就在右边
    val isUserMessage = message.senderGender == userGender
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUserMessage) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .padding(4.dp),
            shape = RoundedCornerShape(
                topStart = if (isUserMessage) 16.dp else 4.dp,
                topEnd = if (isUserMessage) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = if (isUserMessage) Color(0xFFED9EBC) else Color(0xFFF8E1EB)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = message.content,
                    color = Color(0xFF7D5260),
                    fontSize = 16.sp
                )
                Text(
                    text = formatTimestamp(message.timestamp),
                    color = Color(0xFF7D5260).copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                if (!isUserMessage && !message.isRead) {
                    Text(
                        text = "未读",
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// 空聊天状态
@Composable
private fun EmptyChatState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Email,
            contentDescription = "空聊天",
            tint = Color(0xFFED9EBC),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "还没有消息",
            color = Color(0xFF7D5260),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "开始与伴侣聊天吧",
            color = Color(0xFFED9EBC),
            fontSize = 14.sp
        )
    }
}

// 格式化时间戳
private fun formatTimestamp(timestamp: String): String {
    return try {
        // 使用系统默认时区转换时间戳
        val instant = java.time.Instant.ofEpochMilli(timestamp.toLong())
        val localTime = instant.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        localTime.format(formatter)
    } catch (e: Exception) {
        ""
    }
}
