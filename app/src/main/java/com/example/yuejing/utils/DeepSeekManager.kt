package com.example.yuejing.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class DeepSeekManager(private val context: Context) {
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("deepseek_prefs", Context.MODE_PRIVATE)
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    // API配置
    private val BASE_URL = "https://api.deepseek.com/v1/chat/completions"
    private val MODEL_NAME = "deepseek-reasoner"
    
    // 保存API密钥
    fun saveApiKey(apiKey: String) {
        sharedPreferences.edit().putString("deepseek_api_key", apiKey).apply()
    }
    
    // 获取API密钥
    fun getApiKey(): String? {
        return sharedPreferences.getString("deepseek_api_key", null) ?: "sk-8f4c0beab0044597a40c20edd7ba1729"
    }
    
    // DeepSeek API请求数据类
    data class Message(val role: String, val content: String)
    data class RequestBody(val model: String, val messages: List<Message>, val temperature: Double = 0.7, val max_tokens: Int = 2048)
    data class Choice(val message: Message)
    data class ResponseBody(val choices: List<Choice>)
    
    // 发送AI请求
    suspend fun sendRequest(prompt: String): String? {
        return try {
            val apiKey = getApiKey() ?: return null
            
            val messages = listOf(
                Message("system", "你是一位专业的女性健康AI助手，专注于月经周期、健康建议、症状分析和孕期准备。请根据用户提供的数据提供准确、科学的建议和预测。"),
                Message("user", prompt)
            )
            
            val requestBody = RequestBody(MODEL_NAME, messages)
            val jsonBody = gson.toJson(requestBody)
            
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("DeepSeekManager", "API请求失败: ${response.code} ${response.message}")
                    return null
                }
                
                val responseBody = response.body?.string() ?: run {
                    Log.e("DeepSeekManager", "API响应体为空")
                    return null
                }
                
                try {
                    val responseData = gson.fromJson(responseBody, ResponseBody::class.java)
                    val content = responseData.choices.firstOrNull()?.message?.content
                    if (content.isNullOrBlank()) {
                        Log.e("DeepSeekManager", "AI返回内容为空")
                        return null
                    }
                    return content
                } catch (jsonException: Exception) {
                    Log.e("DeepSeekManager", "JSON解析失败: ${jsonException.message}")
                    Log.e("DeepSeekManager", "原始响应体: $responseBody")
                    return null
                }
            }
        } catch (e: Exception) {
            Log.e("DeepSeekManager", "API请求异常: ${e.message}", e)
            null
        }
    }
    
    // 智能月经周期预测
    suspend fun predictCycle(periodData: String): String? {
        val prompt = "$periodData"
        return sendRequest(prompt)
    }
    
    // 个性化健康建议
    suspend fun getHealthAdvice(userData: String, cyclePhase: String): String? {
        val prompt = "基于以下用户数据和当前月经周期阶段（$cyclePhase），提供个性化的健康建议：\n$userData\n\n请提供饮食、运动、休息和心理健康方面的建议。"
        return sendRequest(prompt)
    }
    
    // 症状分析与建议
    suspend fun analyzeSymptoms(symptoms: String, cyclePhase: String): String? {
        val prompt = "分析以下月经周期症状（当前周期阶段：$cyclePhase）并提供建议：\n$symptoms\n\n请解释症状的可能原因，并提供缓解建议。"
        return sendRequest(prompt)
    }
    
    // 孕期准备AI助手
    suspend fun getPregnancyAdvice(prepData: String): String? {
        val prompt = "基于以下孕期准备数据，提供科学的孕期准备建议：\n$prepData\n\n请包括饮食、运动、生活方式、检查和心理准备方面的建议。"
        return sendRequest(prompt)
    }
    
    // 情绪分析与调节建议
    suspend fun analyzeMood(moodData: String, cyclePhase: String): String? {
        val prompt = "分析以下情绪数据（当前周期阶段：$cyclePhase）并提供调节建议：\n$moodData\n\n请解释情绪变化与月经周期的可能关联，并提供实用的情绪调节方法。"
        return sendRequest(prompt)
    }
    
    // 智能统计分析
    suspend fun getStatsAnalysis(statsData: String): String? {
        val prompt = "分析以下月经周期统计数据并提供见解：\n$statsData\n\n请总结周期规律、健康状况，并提供改善建议。"
        return sendRequest(prompt)
    }
}