package com.icecream.kwklasplus

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.IOException

class QRScanActivity : AppCompatActivity() {
    private lateinit var bodyJSON: JSONObject
    private lateinit var sessionId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrscan)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        bodyJSON = JSONObject(intent.getStringExtra("bodyJSON")!!)
        sessionId = intent.getStringExtra("sessionID")!!

        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        val scanner = GmsBarcodeScanning.getClient(this, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                barcode.rawValue?.let {
                    qrScanComplete(it)
                }
            }
            .addOnCanceledListener {
                Toast.makeText(this, "QR 스캔이 취소되었습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "QR 스캔 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    fun qrScanComplete(qr: String) {
        checkin(qr, bodyJSON) { result ->
            when (result) {
                is CheckinResult.Success -> {
                    val jsonObject = result.data
                    if (jsonObject.has("fieldErrors")) {
                        val fieldErrors = jsonObject.getJSONArray("fieldErrors")
                        val message = StringBuilder()
                        for (i in 0 until fieldErrors.length()) {
                            message.append(fieldErrors.getJSONObject(i).getString("message"))
                            message.append(" ")
                        }
                        if (message.toString().trim().isEmpty()) {
                            showDialog("출석 체크 성공", "정상적으로 출석 처리 되었습니다.")
                        } else {
                            showDialog("출석 체크 실패", message.toString().trim())
                        }
                    } else {
                        showDialog("출석 체크 성공", "정상적으로 출석 처리 되었습니다.")
                    }
                }
                is CheckinResult.Error -> {
                    showDialog("오류 발생", "출석 체크 중 오류가 발생했습니다. 로그인 세션이 만료되었을 수 있습니다. 앱을 재시작한 후 다시 시도해보세요: ${result.message}")
                }
            }
        }
    }

    private fun showDialog(title: String, message: String) {
        runOnUiThread {
            val builder = MaterialAlertDialogBuilder(this)
            builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton("확인") { _, _ -> finish() }
            builder.show()
        }
    }

    sealed class CheckinResult {
        data class Success(val data: JSONObject) : CheckinResult()
        data class Error(val message: String) : CheckinResult()
    }

    fun checkin(id: String, body: JSONObject, callback: (CheckinResult) -> Unit) {
        Thread {
            val client = OkHttpClient()
            body.put("encrypt", id)
            val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), body.toString())
            val request = Request.Builder()
                .url("https://klas.kw.ac.kr/mst/ads/admst/KwAttendQRCodeInsert.do")
                .header("Content-Type", "application/json")
                .header("Cookie", "SESSION=$sessionId")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G960N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.181 Mobile Safari/537.36 NuriwareApp")
                .post(requestBody)
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonObject = JSONObject(responseBody)
                        callback(CheckinResult.Success(jsonObject))
                    } else {
                        callback(CheckinResult.Error("응답 내용이 비어있습니다."))
                    }
                } else {
                    callback(CheckinResult.Error("서버 오류: ${response.code}"))
                }
            } catch (e: IOException) {
                callback(CheckinResult.Error("네트워크 오류: ${e.message}"))
            } catch (e: Exception) {
                callback(CheckinResult.Error("알 수 없는 오류: ${e.message}"))
            }
        }.start()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}