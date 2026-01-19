package com.example.yuejing.utils

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.example.yuejing.data.model.LocationData
import com.example.yuejing.data.model.PartnerLocationState
import com.example.yuejing.data.model.PeriodRecord
import com.example.yuejing.data.model.PartnerMessage
import com.example.yuejing.data.model.PartnerSharingState

class GistSync {
    private val client = OkHttpClient()
    private val gson = GsonBuilder()
        .setLenient()
        .registerTypeAdapter(PartnerMessage::class.java, PartnerMessageTypeAdapter())
        .create()
    // 使用自定义后端服务地址 - 实际部署时应替换为腾讯云服务器IP或域名
    private val baseUrl = "http://localhost:3000/api"
    
    // 自定义TypeAdapter，处理旧消息中没有senderGender字段的情况
    private inner class PartnerMessageTypeAdapter : TypeAdapter<PartnerMessage>() {
        override fun write(out: JsonWriter, value: PartnerMessage) {
            out.beginObject()
            out.name("id").value(value.id)
            out.name("senderId").value(value.senderId)
            out.name("receiverId").value(value.receiverId)
            out.name("content").value(value.content)
            out.name("timestamp").value(value.timestamp)
            out.name("senderGender").value(value.senderGender)
            out.name("isRead").value(value.isRead)
            out.endObject()
        }
        
        override fun read(reader: JsonReader): PartnerMessage {
            var id = ""
            var senderId = ""
            var receiverId = ""
            var content = ""
            var timestamp = ""
            var senderGender = "female" // 默认值，处理旧消息
            var isRead = false
            
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextString()
                    "senderId" -> senderId = reader.nextString()
                    "receiverId" -> receiverId = reader.nextString()
                    "content" -> content = reader.nextString()
                    "timestamp" -> timestamp = reader.nextString()
                    "senderGender" -> senderGender = reader.nextString()
                    "isRead" -> isRead = reader.nextBoolean()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            
            // 根据senderId推断senderGender，处理旧消息
            if (senderGender.isBlank()) {
                senderGender = if (senderId == "female") "female" else "male"
            }
            
            return PartnerMessage(id, senderId, receiverId, content, timestamp, senderGender, isRead)
        }
    }
    
    // 检查令牌是否有效 - 自定义后端不需要GitHub token
    fun isTokenValid(): Boolean {
        return true
    }
    
    // 验证Gist ID是否有效 - 自定义后端不需要Gist ID
    fun isGistIdValid(gistId: String): Boolean {
        return true
    }
    
    // 创建或更新记录 - 使用自定义后端
    fun uploadRecords(records: List<PeriodRecord>, gistId: String? = null): String {
        // 自定义后端暂不支持记录同步，返回空字符串
        return ""
    }
    
    // 上传伴侣共享状态 - 使用自定义后端，数据加密
    fun uploadPartnerSharingState(sharingState: PartnerSharingState, gistId: String? = null): String {
        try {
            println("开始上传伴侣共享状态: $sharingState")
            
            // 序列化并加密数据
            val jsonData = gson.toJson(sharingState)
            val encryptedData = EncryptionManager.encrypt(jsonData)
            
            // 创建加密请求体
            val encryptedRequest = mapOf("encryptedData" to encryptedData)
            
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = gson.toJson(encryptedRequest).toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url("$baseUrl/partner-sharing")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                throw Exception("后端API错误: ${response.code} ${response.message} - $responseBody")
            }
            
            val responseBody = response.body?.string() ?: ""
            println("伴侣共享状态上传成功: $responseBody")
            return "success"
        } catch (e: Exception) {
            println("上传伴侣共享状态失败: ${e.message}")
            e.printStackTrace()
            throw Exception("上传伴侣共享状态失败: ${e.message}")
        }
    }
    
    // 下载记录 - 使用自定义后端
    fun downloadRecords(gistId: String): List<PeriodRecord> {
        // 自定义后端暂不支持记录同步，返回空列表
        return emptyList()
    }
    
    // 下载伴侣共享状态 - 使用自定义后端，数据解密
    fun downloadPartnerSharingState(gistId: String): PartnerSharingState {
        try {
            println("开始下载伴侣共享状态")
            
            val request = Request.Builder()
                .url("$baseUrl/partner-sharing")
                .header("Content-Type", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                throw Exception("后端API错误: ${response.code} ${response.message} - $responseBody")
            }
            
            val responseBody = response.body?.string() ?: ""
            println("伴侣共享状态下载成功: $responseBody")
            
            // 解析响应，获取加密数据
            val result = gson.fromJson(responseBody, Map::class.java)
            val encryptedData = result["encryptedData"] as? String
            
            if (encryptedData != null) {
                // 解密数据
                val decryptedJson = EncryptionManager.decrypt(encryptedData)
                return gson.fromJson(decryptedJson, PartnerSharingState::class.java)
            } else {
                // 兼容未加密数据格式
                val data = result["data"] as? Map<String, Any> ?: emptyMap()
                val messagesJson = gson.toJson(data["messages"] ?: emptyList<PartnerMessage>())
                
                return PartnerSharingState(
                    partnerInfo = null,
                    sharingSettings = SharingSettings(),
                    messages = gson.fromJson(messagesJson, Array<PartnerMessage>::class.java).toList(),
                    pregnancyPreparation = null
                )
            }
        } catch (e: Exception) {
            println("下载伴侣共享状态失败: ${e.message}")
            e.printStackTrace()
            // 如果反序列化失败，返回默认值
            return PartnerSharingState()
        }
    }
    
    // 上传位置信息 - 支持单个位置数据，数据加密
    fun uploadLocationData(locationData: LocationData, gistId: String? = null): String {
        try {
            println("开始上传位置数据: $locationData")
            
            // 序列化并加密数据
            val jsonData = gson.toJson(locationData)
            val encryptedData = EncryptionManager.encrypt(jsonData)
            
            // 创建加密请求体
            val encryptedRequest = mapOf("encryptedData" to encryptedData)
            
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = gson.toJson(encryptedRequest).toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url("$baseUrl/location")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                throw Exception("后端API错误: ${response.code} ${response.message} - $responseBody")
            }
            
            val responseBody = response.body?.string() ?: ""
            println("位置数据上传成功: $responseBody")
            return "success"
        } catch (e: Exception) {
            println("上传位置数据失败: ${e.message}")
            e.printStackTrace()
            throw Exception("上传位置失败: ${e.message}")
        }
    }
    
    // 上传完整位置状态 - 支持同步场景
    fun uploadLocationState(locationState: PartnerLocationState, gistId: String? = null): String {
        try {
            // 如果有女生位置，上传女生位置
            if (locationState.femaleLocation != null) {
                uploadLocationData(locationState.femaleLocation, gistId)
            }
            
            // 如果有男生位置，上传男生位置
            if (locationState.maleLocation != null) {
                uploadLocationData(locationState.maleLocation, gistId)
            }
            
            println("完整位置状态上传成功")
            return "success"
        } catch (e: Exception) {
            println("上传完整位置状态失败: ${e.message}")
            e.printStackTrace()
            throw Exception("上传位置失败: ${e.message}")
        }
    }
    
    // 下载位置信息 - 使用自定义后端，数据解密
    fun downloadLocationData(gistId: String): PartnerLocationState {
        try {
            println("开始下载位置数据")
            
            val request = Request.Builder()
                .url("$baseUrl/location")
                .header("Content-Type", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                throw Exception("后端API错误: ${response.code} ${response.message} - $responseBody")
            }
            
            val responseBody = response.body?.string() ?: ""
            println("位置数据下载成功: $responseBody")
            
            // 解析响应，获取加密数据
            val result = gson.fromJson(responseBody, Map::class.java)
            val encryptedData = result["encryptedData"] as? String
            
            if (encryptedData != null) {
                // 解密数据
                val decryptedJson = EncryptionManager.decrypt(encryptedData)
                return gson.fromJson(decryptedJson, PartnerLocationState::class.java)
            } else {
                // 兼容未加密数据格式
                return gson.fromJson(responseBody, PartnerLocationState::class.java)
            }
        } catch (e: Exception) {
            println("下载位置数据失败: ${e.message}")
            e.printStackTrace()
            // 如果反序列化失败，返回默认值
            return PartnerLocationState()
        }
    }
}