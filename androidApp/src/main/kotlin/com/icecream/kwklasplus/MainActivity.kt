package com.icecream.kwklasplus

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.icecream.kwklasplus.core.auth.AuthFailure
import com.icecream.kwklasplus.core.auth.LoginResult
import com.icecream.kwklasplus.core.auth.StoredCredential
import com.icecream.kwklasplus.core.session.SessionLeaseResult
import com.icecream.kwklasplus.feature.startup.AuthenticationLoadingScreen
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var loadingMessage by mutableStateOf("로그인 중")
    private val handler = Handler(Looper.getMainLooper())
    private var loadingHintRunnable: Runnable? = null
    private var isLoginActivityStarted = false
    private var isHomeStarted = false
    private var errorDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KlasPlusTheme {
                AuthenticationLoadingScreen(
                    message = loadingMessage,
                )
            }
        }

        val sharedPreferences = appPreferences
        val appTheme = sharedPreferences.getString(AppPrefs.APP_THEME, "system")
        when (appTheme) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        lockPortraitOnPhone()

        // 네트워크 연결 상태 확인
        if (!isNetworkConnected()) {
            var builder = MaterialAlertDialogBuilder(this)
            builder
                .setTitle("네트워크 연결 오류")
                .setMessage("네트워크 연결 상태를 확인해주세요.")
                .setPositiveButton("확인") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
            return
        }

        lifecycleScope.launch {
            initializeAuthentication()
        }
    }

    private suspend fun initializeAuthentication() {
        val credential = runCatching { appDependencies.credentialStore.load() }.getOrNull()
        if (credential == null) {
            finish()
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }
        when (val leaseResult = appDependencies.sessionLeaseManager.maintain()) {
            is SessionLeaseResult.Active -> {
                appDependencies.sessionKeepAlive.onSessionAvailable(leaseResult.nextCheckAfterMillis)
                isHomeStarted = true
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
                return
            }
            SessionLeaseResult.Expired, SessionLeaseResult.Missing -> Unit
            is SessionLeaseResult.Retry -> {
                showLoginFailedDialog {
                    lifecycleScope.launch { initializeAuthentication() }
                }
                return
            }
        }

        authenticate(credential)
    }

    private fun authenticate(credential: StoredCredential) {
        lifecycleScope.launch {
            startLoginTimer()
            val driver = appDependencies.httpAuthDriver()
            val result = appDependencies.loginUseCase(driver).resume(credential)
            cancelLoginTimers()
            when (result) {
                is LoginResult.Authenticated -> {
                    if (isHomeStarted) return@launch
                    isLoginActivityStarted = true
                    isHomeStarted = true
                    appDependencies.sessionKeepAlive.onSessionAvailable()
                    startActivityWithLock(Intent(this@MainActivity, HomeActivity::class.java))
                    finish()
                }
                is LoginResult.UserActionRequired -> showSecurityActionRequiredDialog()
                is LoginResult.Failed -> when (result.failure) {
                    AuthFailure.InvalidCredentials -> showInvalidCredentialDialog(null)
                    else -> showLoginFailedDialog { authenticate(credential) }
                }
            }
        }
    }

    private fun startLoginTimer() {
        cancelLoginTimers()
        loadingMessage = "로그인 중"
        val hintRunnable = Runnable {
            if (!isLoginActivityStarted && !isHomeStarted && !isFinishing && !isDestroyed) {
                loadingMessage = "조금만 더 기다려주세요"
            }
        }
        loadingHintRunnable = hintRunnable
        handler.postDelayed(hintRunnable, 7000)
    }

    private fun cancelLoginTimers() {
        loadingHintRunnable?.let { handler.removeCallbacks(it) }
        loadingHintRunnable = null
    }

    private fun showLoginFailedDialog(retry: () -> Unit) {
        if (isFinishing || isDestroyed || errorDialog != null) {
            return
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("로그인 실패")
            .setMessage("알 수 없는 오류로 인해 로그인에 실패했어요. 먼저 기기의 네트워크 상태가 불안정한지 확인 후 다시 시도해보세요. 어쩌면 전체적인 서버 장애가 발생했을 수도 있어요. 이 경우 담당자가 빠르게 대응하고 있을거예요.")
            .setNeutralButton("앱 종료") { _, _ ->
                lifecycleScope.launch {
                    appDependencies.sessionKeepAlive.onSessionCleared()
                    appDependencies.sessionCoordinator.expire()
                    runCatching { appDependencies.credentialStore.clear() }
                    appPreferences.edit().clear().apply()
                    finish()
                }
            }
            .setNegativeButton("서버 상태 확인") { _, _ ->
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(AppUrls.STATUS)
                )
                startActivity(browserIntent)
                finish()
            }
            .setPositiveButton("다시 시도") { _, _ ->
                if (!isFinishing && !isDestroyed) {
                    retry()
                }
            }
            .setCancelable(false)
            .create()
        errorDialog = dialog
        dialog.setOnDismissListener { if (errorDialog === dialog) errorDialog = null }
        dialog.show()
    }

    private fun showInvalidCredentialDialog(message: String?) {
        if (isFinishing || isDestroyed || errorDialog != null) return
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("로그인 실패")
            .setMessage(message ?: "학번 또는 비밀번호를 확인한 후 다시 로그인해주세요.")
            .setPositiveButton("확인") { _, _ ->
                finish()
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
            }
            .setCancelable(false)
            .create()
        errorDialog = dialog
        dialog.setOnDismissListener { if (errorDialog === dialog) errorDialog = null }
        dialog.show()
    }

    private fun showSecurityActionRequiredDialog() {
        if (isFinishing || isDestroyed || errorDialog != null) return

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("로그인 실패")
            .setMessage("임시 비밀번호 변경, CAPTCHA 입력 또는 2차 인증이 필요해요. 계정 보안을 위해 KLAS 웹사이트에서 먼저 로그인하신 후 다시 시도해 주세요.")
            .setPositiveButton("브라우저 열기") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppUrls.KLAS_BASE))
                startActivity(intent)
                finish()
            }
            .setNegativeButton("종료") { _, _ -> finish() }
            .setCancelable(false)
            .create()
        errorDialog = dialog
        dialog.setOnDismissListener { if (errorDialog === dialog) errorDialog = null }
        dialog.show()
    }

    // 네트워크 연결 여부 확인 함수
    private fun isNetworkConnected(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    override fun onDestroy() {
        cancelLoginTimers()
        errorDialog?.dismiss()
        errorDialog = null
        super.onDestroy()
    }
}
