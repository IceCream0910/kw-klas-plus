package com.icecream.kwklasplus.manager

import android.content.Context
import android.util.Base64
import android.util.Log
import com.icecream.kwklasplus.libraryQrCachePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class LibraryManager(context: Context) {
    private val cacheManager = CacheManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val baseUrl = "https://mobileid.kw.ac.kr"

    suspend fun getLibraryQrData(stdNumber: String, phone: String, password: String): JSONObject? = withContext(Dispatchers.IO) {
        val realId = "0$stdNumber"
        val userInfoHash = (stdNumber + phone + password).hashCode().toString()

        return@withContext try {
            var secret = cacheManager.getSecret(realId, userInfoHash)
            if (secret == null) {
                secret = getSecretKey(realId)
                cacheManager.saveSecret(realId, userInfoHash, secret)
            }

            var authKey = cacheManager.getAuthKey(realId, userInfoHash)
            if (authKey == null) {
                authKey = login(realId, stdNumber, phone, password, secret)
                cacheManager.saveAuthKey(realId, userInfoHash, authKey)
            }

            getQrCode(realId, authKey)
        } catch (e: Exception) {
            Log.e("LibraryManager", "Error getting library QR data: ${e.message}", e)
            cacheManager.clearCache(realId, userInfoHash)
            null
        }
    }

    private fun getSecretKey(realId: String): String {
        val getUserKeyBody = FormBody.Builder()
            .add("user_id", encode(realId))
            .build()
        val getUserKeyRequest = Request.Builder()
            .url("$baseUrl/mobile/MA/xml_user_key.php")
            .post(getUserKeyBody)
            .build()

        val response = client.newCall(getUserKeyRequest).execute()
        if (!response.isSuccessful) throw IOException("Unexpected code $response")

        val responseData = response.body?.string()
        return parseXmlResponse(responseData ?: "", "sec_key")
            ?: throw Exception("Failed to get secret key")
    }

    private fun login(
        realId: String,
        stdNumber: String,
        phone: String,
        password: String,
        secret: String
    ): String {
        val loginBody = FormBody.Builder()
            .add("real_id", encode(realId))
            .add("rid", encode(stdNumber))
            .add("device_gb", "A")
            .add("tel_no", phone)
            .add("pass_wd", encrypt(password, secret))
            .build()
        val loginRequest = Request.Builder()
            .url("$baseUrl/mobile/MA/xml_login_and.php")
            .post(loginBody)
            .build()

        val response = client.newCall(loginRequest).execute()
        if (!response.isSuccessful) throw IOException("Unexpected code $response")

        val loginResponseData = response.body?.string()
        return parseXmlResponse(loginResponseData ?: "", "auth_key") ?: throw Exception("Login failed")
    }

    private fun getQrCode(realId: String, authKey: String): JSONObject {
        val qrBody = FormBody.Builder()
            .add("real_id", encode(realId))
            .add("auth_key", authKey)
            .add("new_check", "Y")
            .build()
        val qrRequest = Request.Builder()
            .url("$baseUrl/mobile/MA/xml_userInfo_auth.php")
            .post(qrBody)
            .build()

        val response = client.newCall(qrRequest).execute()
        if (!response.isSuccessful) throw IOException("Unexpected code $response")

        val qrResponseData = response.body?.string()
        return xmlToJson(qrResponseData ?: "")
    }

    private fun encode(msg: String): String {
        return Base64.encodeToString(msg.toByteArray(), Base64.NO_WRAP)
    }

    private fun encrypt(msg: String, secret: String): String {
        val iv = ByteArray(16) { 0 }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKeySpec = SecretKeySpec(secret.toByteArray(), "AES")
        val ivParameterSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
        val encrypted = cipher.doFinal(msg.toByteArray())
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun parseXmlResponse(xmlString: String, tag: String): String? {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlString))
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == tag) {
                return parser.nextText()
            }
            eventType = parser.next()
        }
        return null
    }

    private fun xmlToJson(xmlString: String): JSONObject {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlString))
        var eventType = parser.eventType
        val jsonObject = JSONObject()
        var currentTag: String? = null
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text
                    if (currentTag != null && text != null && text.trim().isNotEmpty()) {
                        jsonObject.put(currentTag, text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    currentTag = null
                }
            }
            eventType = parser.next()
        }
        return jsonObject
    }

    fun clearCache(stdNumber: String, phone: String, password: String) {
        val realId = "0$stdNumber"
        val userInfoHash = (stdNumber + phone + password).hashCode().toString()
        cacheManager.clearCache(realId, userInfoHash)
    }
}

class CacheManager(context: Context) {
    private val sharedPreferences = context.libraryQrCachePreferences

    fun saveSecret(realId: String, userInfoHash: String, secret: String) {
        sharedPreferences.edit().putString("secret_${realId}_${userInfoHash}", secret).apply()
    }

    fun getSecret(realId: String, userInfoHash: String): String? {
        return sharedPreferences.getString("secret_${realId}_${userInfoHash}", null)
    }

    fun saveAuthKey(realId: String, userInfoHash: String, authKey: String) {
        sharedPreferences.edit().putString("authKey_${realId}_${userInfoHash}", authKey).apply()
    }

    fun getAuthKey(realId: String, userInfoHash: String): String? {
        return sharedPreferences.getString("authKey_${realId}_${userInfoHash}", null)
    }

    fun clearCache(realId: String, userInfoHash: String) {
        sharedPreferences.edit().apply {
            remove("secret_${realId}_${userInfoHash}")
            remove("authKey_${realId}_${userInfoHash}")
            apply()
        }
    }
}
