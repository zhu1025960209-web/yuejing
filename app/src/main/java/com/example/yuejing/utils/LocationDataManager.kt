package com.example.yuejing.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.yuejing.data.model.LocationData
import com.example.yuejing.data.model.PartnerLocationState
import com.google.gson.Gson

class LocationDataManager(private val context: Context) {
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("location_prefs", Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    
    private val KEY_PARTNER_LOCATION_STATE = "partner_location_state"
    
    // 保存位置状态
    fun savePartnerLocationState(locationState: PartnerLocationState) {
        val json = gson.toJson(locationState)
        sharedPreferences.edit().putString(KEY_PARTNER_LOCATION_STATE, json).apply()
    }
    
    // 获取位置状态
    fun getPartnerLocationState(): PartnerLocationState {
        val json = sharedPreferences.getString(KEY_PARTNER_LOCATION_STATE, null)
        return if (json != null) {
            try {
                gson.fromJson(json, PartnerLocationState::class.java)
            } catch (e: Exception) {
                // 如果反序列化失败，返回默认值
                PartnerLocationState()
            }
        } else PartnerLocationState()
    }
    
    // 更新当前用户的位置
    fun updateUserLocation(locationData: LocationData) {
        val currentState = getPartnerLocationState()
        val updatedState = if (locationData.gender == "female") {
            currentState.copy(femaleLocation = locationData)
        } else {
            currentState.copy(maleLocation = locationData)
        }
        savePartnerLocationState(updatedState)
    }
}