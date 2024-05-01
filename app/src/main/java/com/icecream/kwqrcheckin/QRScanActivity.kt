package com.icecream.kwqrcheckin

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.integration.android.IntentIntegrator
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

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

        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("교수님이 제공한 QR 코드를 스캔해주세요")
        integrator.setCameraId(0)
        integrator.setBeepEnabled(false)
        integrator.setBarcodeImageEnabled(false)
        integrator.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)

        if (result != null) {
            if (result.contents == null) {
                finish()
            }
            else {
                var qr = result.contents
                checkin(qr, bodyJSON) { jsonObject ->
                    if(jsonObject.getJSONArray("fieldErrors") != null) {
                        val fieldErrors = jsonObject.getJSONArray("fieldErrors")
                        val message = StringBuilder()
                        for (i in 0 until fieldErrors.length()) {
                            message.append(fieldErrors.getJSONObject(i).getString("message"))
                            message.append(" ")
                        }
                        if(message.toString().replace(" ", "") == "") {
                            runOnUiThread {
                                val builder = MaterialAlertDialogBuilder(this)
                                builder.setTitle("출석 체크 성공")
                                    .setMessage("정상적으로 출석 처리 되었습니다.")
                                    .setPositiveButton("확인",
                                        DialogInterface.OnClickListener { dialog, id ->
                                            finish()
                                        })
                                builder.show()
                            }
                        } else {
                            runOnUiThread {
                                val builder = MaterialAlertDialogBuilder(this)
                                builder.setTitle("출석 체크 실패")
                                    .setMessage(message.toString().trim())
                                    .setPositiveButton("확인",
                                        DialogInterface.OnClickListener { dialog, id ->
                                            finish()
                                        })
                                builder.show()
                            }
                        }
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun checkin(id: String, body: JSONObject, callback: (JSONObject) -> Unit) {
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

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (responseBody != null) {
                val jsonObject = JSONObject(responseBody)
                callback(jsonObject)
            }
        }.start()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}