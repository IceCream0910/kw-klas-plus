package com.icecream.kwklasplus

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.widget.addTextChangedListener
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {
    private lateinit var clOnboarding: View
    private lateinit var clLogin: View
    private lateinit var etId: TextInputEditText
    private lateinit var etPwd: TextInputEditText
    private lateinit var tilPwd: TextInputLayout
    private lateinit var btnLogin: Button
    private lateinit var tvTitle: TextView
    private lateinit var btnStart: Button

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        // 모바일에서는 세로 모드 고정
        if (isTablet(this)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        clOnboarding = findViewById(R.id.clOnboarding)
        clLogin = findViewById(R.id.clLogin)
        btnStart = findViewById(R.id.btnStart)
        etId = findViewById(R.id.etId)
        etPwd = findViewById(R.id.etPwd)
        tilPwd = findViewById(R.id.tilPwd)
        btnLogin = findViewById(R.id.btnLogin)
        tvTitle = findViewById(R.id.tvTitle)

        btnStart.setOnClickListener {
            clOnboarding.visibility = View.GONE
            clLogin.visibility = View.VISIBLE
            etId.requestFocus()
        }

        setupInputListeners()
        setupLoginButton()
    }

    private fun setupInputListeners() {
        etId.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 10) {
                    tilPwd.visibility = View.VISIBLE
                    etPwd.requestFocus()
                    tvTitle.text = "KLAS 비밀번호를 입력해주세요."
                } else {
                    tilPwd.visibility = View.GONE
                    tvTitle.text = "학번을 입력해주세요."
                }
                updateLoginButtonState()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        etPwd.addTextChangedListener { updateLoginButtonState() }

        etId.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                if (etId.text?.length == 10) {
                    etPwd.requestFocus()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }

        etPwd.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (btnLogin.isEnabled) {
                    btnLogin.performClick()
                }
                true
            } else {
                false
            }
        }
    }

    private fun updateLoginButtonState() {
        btnLogin.isEnabled = etId.text?.length == 10 && !etPwd.text.isNullOrEmpty()
    }

    private fun setupLoginButton() {
        btnLogin.setOnClickListener {
            val kwId = etId.text.toString()
            val kwPwd = etPwd.text.toString()

            if (kwId.isNotEmpty() && kwPwd.isNotEmpty()) {
                encrypt(kwId, kwPwd)
            } else {
                Toast.makeText(this, "학번과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun encrypt(id: String, str: String) {
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

    private fun saveLoginInfo(id: String, pwd: String) {
        val sharedPreferences = getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("kwID", id)
            putString("kwPWD", pwd)
            apply()
        }

        finish()
        startActivity(Intent(this, MainActivity::class.java))
    }
}