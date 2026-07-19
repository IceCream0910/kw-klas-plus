package com.icecream.kwklasplus

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.icecream.kwklasplus.core.auth.AuthFailure
import com.icecream.kwklasplus.core.auth.PlainPassword
import com.icecream.kwklasplus.core.auth.CredentialPreparationResult
import com.icecream.kwklasplus.platform.navigation.openWebRoute
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var clOnboarding: View
    private lateinit var clLogin: View
    private lateinit var etId: TextInputEditText
    private lateinit var etPwd: TextInputEditText
    private lateinit var tilPwd: TextInputLayout
    private lateinit var btnLogin: Button
    private lateinit var cbAgree: CheckBox
    private lateinit var tvTitle: TextView
    private lateinit var btnStart: Button
    private lateinit var webView: WebView
    private lateinit var cbAgreeBtn: Button
    private lateinit var forgetPwdBtn: Button
    private lateinit var forgetIdBtn: Button
    private lateinit var registerBtn: Button

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        applyEdgeToEdgeInsets(R.id.main) { insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            findViewById<View>(R.id.main).setPadding(bars.left, bars.top, bars.right, bars.bottom)
        }

        lockPortraitOnPhone()

        webView = findViewById(R.id.onboarding_webView)
        clOnboarding = findViewById(R.id.clOnboarding)
        clLogin = findViewById(R.id.clLogin)
        btnStart = findViewById(R.id.btnStart)
        etId = findViewById(R.id.etId)
        etPwd = findViewById(R.id.etPwd)
        tilPwd = findViewById(R.id.tilPwd)
        btnLogin = findViewById(R.id.btnLogin)
        tvTitle = findViewById(R.id.tvTitle)
        cbAgree = findViewById(R.id.cbAgree)
        cbAgreeBtn = findViewById(R.id.cbAgreeBtn)
        forgetIdBtn = findViewById(R.id.forgetIdBtn)
        forgetPwdBtn= findViewById(R.id.forgetPwdBtn)
        registerBtn = findViewById(R.id.registerBtn)

        webView.configureAppWebView()
        webView.loadUrl(AppUrls.ONBOARDING)

        cbAgree.setOnCheckedChangeListener { _, _ ->
            updateLoginButtonState()
        }

        cbAgreeBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://blog.yuntae.in/11cfc9b9-3eca-8078-96a0-c41c4ca9cb8f")
            startActivity(intent)
        }

        btnStart.setOnClickListener {
            clOnboarding.visibility = View.GONE
            clLogin.visibility = View.VISIBLE
            etId.requestFocus()
            etId.post {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(etId, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        forgetIdBtn.setOnClickListener {
            openWebRoute("https://klas.kw.ac.kr/usr/cmn/login/modal/UserFindMemberNoPage.do", null)
        }

        forgetPwdBtn.setOnClickListener {
            openWebRoute("https://klas.kw.ac.kr/usr/cmn/login/modal/UserFindPwdPage.do", null)
        }

        registerBtn.setOnClickListener {
            openWebRoute("https://klas.kw.ac.kr/usr/cmn/login/modal/UserFrstModPwdPage.do", null)
        }

        btnLogin.setOnClickListener {
            if(cbAgree.isChecked) {
                val kwId = etId.text.toString()
                val kwPwd = etPwd.text.toString()

                if (kwId.isNotEmpty() && kwPwd.isNotEmpty()) {
                    prepareCredential(kwId, kwPwd)
                } else {
                    Toast.makeText(this, "학번과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "개인정보 수집 및 제공에 동의해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        setupInputListeners()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            currentFocus?.let { v ->
                if (v is TextInputEditText) {
                    val outRect = android.graphics.Rect()
                    v.getGlobalVisibleRect(outRect)
                    if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                        hideKeyboardFrom(v)
                        v.clearFocus()
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }


    private fun hideKeyboardFrom(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun setupInputListeners() {
        etId.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 10) {
                    tilPwd.visibility = View.VISIBLE
                    etPwd.requestFocus()
                    tvTitle.text = "KLAS 비밀번호를 입력해주세요."
                    forgetIdBtn.visibility = View.GONE
                    forgetPwdBtn.visibility = View.VISIBLE
                    registerBtn.visibility = View.GONE
                } else {
                    tilPwd.visibility = View.GONE
                    tvTitle.text = "학번을 입력해주세요."
                    forgetIdBtn.visibility = View.VISIBLE
                    forgetPwdBtn.visibility = View.GONE
                    registerBtn.visibility = View.VISIBLE
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
                } else {
                    etPwd.clearFocus()
                    hideKeyboardFrom(view = etPwd)
                }
                true
            } else {
                false
            }
        }
    }

    private fun updateLoginButtonState() {
        btnLogin.isEnabled = etId.text?.length == 10 && !etPwd.text.isNullOrEmpty() && cbAgree.isChecked
    }

    private fun prepareCredential(id: String, password: String) {
        lifecycleScope.launch {
            when (
                val result = appDependencies.prepareCredentialUseCase.prepare(
                    id,
                    PlainPassword.of(password),
                )
            ) {
                is CredentialPreparationResult.Success -> openMainActivity()
                is CredentialPreparationResult.Failure -> {
                    val message = when (result.failure) {
                        AuthFailure.Timeout -> "요청 시간이 초과되었습니다. 다시 시도해주세요."
                        AuthFailure.MalformedResponse -> "서버 응답을 처리할 수 없습니다."
                        AuthFailure.Storage -> "로그인 정보를 안전하게 저장하지 못했습니다."
                        else -> "로그인 정보를 확인하는 중 오류가 발생했습니다."
                    }
                    Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openMainActivity() {
        finish()
        startActivity(Intent(this, MainActivity::class.java))
    }
}
