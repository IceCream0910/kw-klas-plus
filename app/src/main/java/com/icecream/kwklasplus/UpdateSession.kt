package com.icecream.kwklasplus

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class UpdateSession : Service() {

    private val client = OkHttpClient()
    private val handler = Handler()
    private lateinit var runnableCode: Runnable
    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = intent?.getStringExtra("session")

        runnableCode = object : Runnable {
            override fun run() {
                val thisRunnable = this
                coroutineScope.launch {
                    val request = Request.Builder()
                        .url("https://klas.kw.ac.kr/usr/cmn/login/UpdateSession.do")
                        .header("Cookie", "SESSION=$session")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    }

                    handler.postDelayed(thisRunnable, TimeUnit.MINUTES.toMillis(5))
                }
            }
        }

        handler.post(runnableCode)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnableCode)
        job.cancel()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}