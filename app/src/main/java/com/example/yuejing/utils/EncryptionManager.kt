package com.example.yuejing.utils

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.security.SecureRandom

/**
 * 加密管理类：用于对上传到服务器的数据进行加密，以及从服务器下载的数据进行解密
 */
object EncryptionManager {
    // AES 加密算法相关配置
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val KEY_SIZE = 16 // 128 bits
    
    // 生成随机密钥和初始化向量
    private val key by lazy { generateKey() }
    private val iv by lazy { generateIV() }
    
    /**
     * 生成AES密钥
     */
    private fun generateKey(): ByteArray {
        val key = ByteArray(KEY_SIZE)
        SecureRandom().nextBytes(key)
        return key
    }
    
    /**
     * 生成初始化向量
     */
    private fun generateIV(): ByteArray {
        val iv = ByteArray(KEY_SIZE)
        SecureRandom().nextBytes(iv)
        return iv
    }
    
    /**
     * 加密数据
     * @param data 要加密的字符串数据
     * @return 加密后的Base64字符串
     */
    fun encrypt(data: String): String {
        try {
            val secretKeySpec = SecretKeySpec(key, ALGORITHM)
            val ivParameterSpec = IvParameterSpec(iv)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
            
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("加密失败: ${e.message}")
        }
    }
    
    /**
     * 解密数据
     * @param encryptedData 加密后的Base64字符串
     * @return 解密后的原始字符串
     */
    fun decrypt(encryptedData: String): String {
        try {
            val secretKeySpec = SecretKeySpec(key, ALGORITHM)
            val ivParameterSpec = IvParameterSpec(iv)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
            
            val encryptedBytes = Base64.decode(encryptedData, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("解密失败: ${e.message}")
        }
    }
}