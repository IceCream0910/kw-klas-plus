package com.icecream.kwqrcheckin

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.core.view.WindowCompat
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        val etID = findViewById<android.widget.EditText>(R.id.etId)
        val etPWD = findViewById<android.widget.EditText>(R.id.etPwd)
        val btnLogin = findViewById<android.widget.Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val kwID = etID.text.toString()
            val kwPWD = etPWD.text.toString()

            if (kwID.isNotEmpty() && kwPWD.isNotEmpty()) {
                encrypt(kwID, kwPWD)
            } else {
                Toast.makeText(this, "학번과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun encrypt(id: String, str: String) {
    Thread {
        val client = OkHttpClient()

        val mediaType = "application/json".toMediaTypeOrNull()
        val body = RequestBody.create(mediaType, "{\"loginPwd\": \"$str\"}")
        val request = Request.Builder()
            .url("https://klas.kw.ac.kr/mst/cmn/login/SelectScrtyPwd.do")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (responseBody != null) {
            val jsonObject = JSONObject(responseBody)
            val encryptedPWD = jsonObject.getString("loginPwd")

            runOnUiThread {
                saveLoginInfo(id, encryptedPWD)
            }
        }
    }.start()
}

    fun saveLoginInfo(id: String, pwd: String) {
        val sharedPreferences = getSharedPreferences("com.icecream.kwqrcheckin", MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("kwID", id)
            putString("kwPWD", pwd)
            apply()
        }

        finish()
        startActivity(Intent(this, MainActivity::class.java))
    }
}