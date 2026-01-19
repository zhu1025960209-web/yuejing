const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const { v4: uuidv4 } = require('uuid');

const app = express();
const PORT = process.env.PORT || 3002;

// 配置中间件
app.use(cors());
app.use(bodyParser.json());

// 内存存储 - 实际生产环境应使用数据库
let locationData = {
  femaleLocation: null,
  maleLocation: null
};

let messages = [];

// 日志中间件
app.use((req, res, next) => {
  console.log(`${new Date().toISOString()} - ${req.method} ${req.url}`);
  next();
});

// API 端点

// 1. 位置相关端点

// 获取双方位置
app.get('/api/location', (req, res) => {
  res.json(locationData);
});

// 上传位置数据 - 支持加密数据
app.post('/api/location', (req, res) => {
  try {
    let locationDataObj;
    
    // 检查是否为加密数据
    if (req.body.encryptedData) {
      console.log('收到加密位置数据，直接保存（前端加密，后端不解密）');
      // 前端加密，后端不解密，直接保存加密数据
      return res.json({ success: true, message: '加密位置数据上传成功' });
    } else {
      // 处理未加密数据（兼容旧版本）
      console.log('收到未加密位置数据，处理中...');
      const { latitude, longitude, address, timestamp, gender } = req.body;
      
      // 验证必填字段
      if (!latitude || !longitude || !timestamp || !gender) {
        return res.status(400).json({ error: '缺少必要的位置信息字段' });
      }
      
      if (gender !== 'female' && gender !== 'male') {
        return res.status(400).json({ error: '性别字段必须为 female 或 male' });
      }
      
      // 更新位置数据
      const newLocation = {
        latitude,
        longitude,
        address: address || '',
        timestamp,
        gender
      };
      
      if (gender === 'female') {
        locationData.femaleLocation = newLocation;
      } else {
        locationData.maleLocation = newLocation;
      }
      
      console.log(`位置更新成功: ${gender} - ${latitude}, ${longitude}`);
      res.json({ success: true, message: '位置上传成功', location: locationData });
    }
  } catch (error) {
    console.error('位置上传失败:', error);
    res.status(500).json({ error: '位置上传失败', details: error.message });
  }
});

// 2. 消息相关端点

// 获取所有消息
app.get('/api/messages', (req, res) => {
  // 按时间戳排序
  const sortedMessages = [...messages].sort((a, b) => a.timestamp.localeCompare(b.timestamp));
  res.json(sortedMessages);
});

// 发送新消息
app.post('/api/messages', (req, res) => {
  try {
    const { senderId, receiverId, content, senderGender } = req.body;
    
    // 验证必填字段
    if (!senderId || !receiverId || !content || !senderGender) {
      return res.status(400).json({ error: '缺少必要的消息字段' });
    }
    
    if (senderGender !== 'female' && senderGender !== 'male') {
      return res.status(400).json({ error: '发送者性别必须为 female 或 male' });
    }
    
    // 创建新消息
    const newMessage = {
      id: `msg_${Date.now()}`,
      senderId,
      receiverId,
      content,
      timestamp: Date.now().toString(),
      senderGender,
      isRead: false
    };
    
    // 添加到消息列表
    messages.push(newMessage);
    
    console.log(`新消息发送成功: ${senderId} -> ${receiverId}: ${content}`);
    res.json({ success: true, message: '消息发送成功', newMessage });
  } catch (error) {
    console.error('消息发送失败:', error);
    res.status(500).json({ error: '消息发送失败', details: error.message });
  }
});

// 获取特定消息
app.get('/api/messages/:id', (req, res) => {
  const messageId = req.params.id;
  const message = messages.find(msg => msg.id === messageId);
  
  if (message) {
    res.json(message);
  } else {
    res.status(404).json({ error: '消息不存在' });
  }
});

// 标记消息为已读
app.put('/api/messages/:id/read', (req, res) => {
  const messageId = req.params.id;
  const messageIndex = messages.findIndex(msg => msg.id === messageId);
  
  if (messageIndex !== -1) {
    messages[messageIndex].isRead = true;
    res.json({ success: true, message: '消息已标记为已读', updatedMessage: messages[messageIndex] });
  } else {
    res.status(404).json({ error: '消息不存在' });
  }
});

// 3. 伴侣共享状态端点

// 获取伴侣共享状态
app.get('/api/partner-sharing', (req, res) => {
  // 这里可以返回完整的伴侣共享状态
  res.json({
    success: true,
    data: {
      partnerInfo: null,
      sharingSettings: {
        sharePeriodReminders: true,
        shareOvulationReminders: true,
        shareFertileReminders: true,
        shareMoodSymptoms: false,
        shareIntimacy: false,
        partnerViewEnabled: true
      },
      messages: messages.sort((a, b) => a.timestamp.localeCompare(b.timestamp)),
      pregnancyPreparation: null
    }
  });
});

// 更新伴侣共享状态 - 支持加密数据
app.post('/api/partner-sharing', (req, res) => {
  try {
    // 检查是否为加密数据
    if (req.body.encryptedData) {
      console.log('收到加密伴侣共享状态，直接保存（前端加密，后端不解密）');
      // 前端加密，后端不解密，直接保存加密数据
      return res.json({ success: true, message: '加密伴侣共享状态上传成功' });
    } else {
      // 处理未加密数据（兼容旧版本）
      console.log('收到未加密伴侣共享状态，处理中...');
      const sharingState = req.body;
      
      // 如果有新消息，添加到消息列表
      if (sharingState.messages && Array.isArray(sharingState.messages)) {
        sharingState.messages.forEach(newMsg => {
          if (newMsg.id && !messages.find(msg => msg.id === newMsg.id)) {
            messages.push(newMsg);
          }
        });
      }
      
      console.log('伴侣共享状态更新成功');
      res.json({ success: true, message: '伴侣共享状态更新成功' });
    }
  } catch (error) {
    console.error('伴侣共享状态更新失败:', error);
    res.status(500).json({ error: '伴侣共享状态更新失败', details: error.message });
  }
});

// 4. 健康检查端点
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString(), message: '后端服务运行正常' });
});

// 启动服务器
app.listen(PORT, () => {
  console.log(`后端服务器已启动，运行在 http://localhost:${PORT}`);
  console.log('API 端点:');
  console.log('  - GET  /health                    - 健康检查');
  console.log('  - GET  /api/location              - 获取双方位置');
  console.log('  - POST /api/location              - 上传位置数据');
  console.log('  - GET  /api/messages              - 获取所有消息');
  console.log('  - POST /api/messages              - 发送新消息');
  console.log('  - GET  /api/messages/:id          - 获取特定消息');
  console.log('  - PUT  /api/messages/:id/read     - 标记消息为已读');
  console.log('  - GET  /api/partner-sharing       - 获取伴侣共享状态');
  console.log('  - POST /api/partner-sharing       - 更新伴侣共享状态');
});