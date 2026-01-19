# 月经记录应用 (YueJing)

## 项目简介
这是一个月经记录应用，支持通过GitHub Gist进行数据同步，实现多设备间的数据共享。

## 功能特性
- 记录月经周期
- 预测下次月经日期
- 通过GitHub Gist同步数据
- 多设备数据共享
- 实时定位共享（伴侣功能）
- 伴侣聊天功能
- 数据加密传输和存储

## 环境设置

### GitHub Token 配置（可选，用于Gist同步）
为了使用数据同步功能，需要配置GitHub个人访问令牌。

#### 步骤：
1. 登录GitHub账号
2. 进入 Settings → Developer settings → Personal access tokens → Tokens (classic)
3. 点击 "Generate new token"
4. 设置令牌名称，例如 "YueJing Sync"
5. 权限设置：选择 `gist` 权限（仅需要此权限）
6. 点击 "Generate token"
7. 复制生成的令牌

#### 本地开发环境配置
将令牌添加到 `local.properties` 文件中（该文件已添加到 .gitignore，不会被提交到版本控制）：

```
github.token=YOUR_GITHUB_TOKEN_HERE
```

#### 生产环境配置
在CI/CD系统中，需要设置环境变量 `GITHUB_TOKEN`，构建系统会自动读取此变量。

## 技术栈
- Android SDK
- Kotlin
- Jetpack Compose
- SQLite
- OkHttp (网络请求)
- Gson (JSON序列化)
- GitHub Gist API (数据同步)
- Node.js + Express (后端服务)
- AES加密算法 (数据安全)

## 实时定位和伴侣聊天功能

### 后端服务
应用支持使用自定义后端服务实现实时定位和伴侣聊天功能。后端服务使用Node.js + Express开发，提供以下API：
- 位置管理：获取/上传双方位置
- 消息系统：发送/获取/标记已读消息
- 伴侣共享状态：同步伴侣相关设置和消息

### 数据加密
为了保护用户隐私，应用使用AES/CBC/PKCS5Padding算法对敏感数据进行加密：
- 上传前：数据序列化 → AES加密 → 上传密文
- 下载后：下载密文 → AES解密 → 反序列化数据

## 权限说明
- 存储权限：用于本地数据存储
- 网络权限：用于API通信
- 位置权限：用于实时定位共享

## 注意事项
- GitHub令牌仅需要 `gist` 权限，不要授予其他不必要的权限
- 令牌应妥善保管，不要分享给他人
- 本地开发时，`local.properties` 文件包含敏感信息，确保不会被提交到版本控制
- 实时定位功能会消耗电池，建议合理使用
