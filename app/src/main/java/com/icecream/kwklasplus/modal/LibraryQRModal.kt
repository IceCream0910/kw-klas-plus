import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.WriterException
import com.icecream.kwklasplus.LibraryQRWidget
import com.icecream.kwklasplus.R
import com.icecream.kwklasplus.modal.LibraryQRSettingsBottomSheetDialog
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class LibraryQRModal(private var isWidget: Boolean) : BottomSheetDialogFragment() {
    private var originalBrightness: Float = 0f
    private lateinit var name: TextView
    private lateinit var numberAndDepartment: TextView
    private lateinit var qrImg: ImageView
    private lateinit var qrProgressBar: ProgressBar
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val baseUrl = "https://mobileid.kw.ac.kr"
    private var isRetry = false
    private lateinit var cacheManager: CacheManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var refreshButton: Button
    private lateinit var refreshButtonForWidget: Button
    private var countDownTimer: CountDownTimer? = null
    private val refreshInterval = 30000L

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        originalBrightness = activity?.window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

        return inflater.inflate(R.layout.library_qr_modal, container, false)
    }

    private fun isWidgetAdded(): Boolean {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetComponent = context?.let { ComponentName(it, LibraryQRWidget::class.java) }
        val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
        return widgetIds.isNotEmpty()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val settingBtn = view.findViewById<TextView>(R.id.settingButton)
        val addWidget = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.addWidget)
        val refreshImageButtonForWidget =
            view.findViewById<ImageView>(R.id.refreshImageButtonForWidget)
        refreshButtonForWidget = view.findViewById(R.id.refreshButtonForWidget)
        refreshButton = view.findViewById(R.id.refreshButton)
        name = view.findViewById(R.id.name)
        numberAndDepartment = view.findViewById(R.id.numberAndDepartment)
        qrImg = view.findViewById(R.id.qrImageView)
        qrProgressBar = view.findViewById(R.id.qrProgressBar)
        cacheManager = CacheManager(requireContext())

        if (isWidget) {
            settingBtn.visibility = View.GONE
            refreshImageButtonForWidget.visibility = View.VISIBLE
            refreshButtonForWidget.visibility = View.VISIBLE
            refreshButton.visibility = View.GONE
            addWidget.visibility = View.GONE
        } else {
            refreshImageButtonForWidget.visibility = View.GONE
            refreshButtonForWidget.visibility = View.GONE
            refreshButton.visibility = View.VISIBLE
            if(!isWidgetAdded()) {
                addWidget.visibility = View.VISIBLE
            }
        }
        addWidget.setOnClickListener {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val provider = context?.let { it1 -> ComponentName(it1, LibraryQRWidget::class.java) }

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported){
                val intent = Intent(context, LibraryQRWidget::class.java)
                val successCallback = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                if (provider != null) {
                    appWidgetManager.requestPinAppWidget(provider, null, successCallback)
                }
            }
        }

        refreshButton.setOnClickListener {
            coroutineScope.launch {
                refreshQRCode()
            }
        }

        refreshButtonForWidget.setOnClickListener {
            coroutineScope.launch {
                refreshQRCode()
            }
        }

        refreshImageButtonForWidget.setOnClickListener {
            coroutineScope.launch {
                refreshQRCode()
            }
        }

        val sharedPreferences =
            activity?.getSharedPreferences("com.icecream.kwklasplus", Context.MODE_PRIVATE)
        val stdNumber = sharedPreferences?.getString("library_stdNumber", null)
        val phone = sharedPreferences?.getString("library_phone", null)
        val password = sharedPreferences?.getString("library_password", null)

        if (stdNumber == null || phone == null || password == null) {
            showSettingsDialog()
            dismiss()
        } else {
            coroutineScope.launch {
                displayQR(stdNumber, phone, password)
                startCountDownTimer()
            }
        }

        settingBtn.setOnClickListener {
            showSettingsDialog()
        }

        // FIX: 태블릿에서 완전히 펼쳐지지 않는 이슈
        view.viewTreeObserver.addOnGlobalLayoutListener {
            val dialog = dialog as BottomSheetDialog?
            val bottomSheet =
                dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?
            val behavior = BottomSheetBehavior.from(bottomSheet!!)
            behavior.peekHeight = view.measuredHeight
        }
    }

    private fun showSettingsDialog() {
        val settingsModal = LibraryQRSettingsBottomSheetDialog()
        settingsModal.show(parentFragmentManager, "LibraryQRSettingsModal")
    }

    private fun startCountDownTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(refreshInterval, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                if (isWidget) {
                    refreshButtonForWidget.text = "$secondsRemaining 초"
                } else {
                    refreshButton.text = " $secondsRemaining 초"
                }
            }

            override fun onFinish() {
                coroutineScope.launch {
                    refreshQRCode()
                }
            }
        }.start()
    }

    private suspend fun refreshQRCode() {
        val sharedPreferences =
            activity?.getSharedPreferences("com.icecream.kwklasplus", Context.MODE_PRIVATE)
        val stdNumber = sharedPreferences?.getString("library_stdNumber", null)
        val phone = sharedPreferences?.getString("library_phone", null)
        val password = sharedPreferences?.getString("library_password", null)

        if (stdNumber != null && phone != null && password != null) {
            qrProgressBar.visibility = View.VISIBLE
            qrImg.visibility = View.GONE
            displayQR(stdNumber, phone, password)
            startCountDownTimer()
        } else {
            Snackbar.make(requireView(), "QR 코드를 새로고침할 수 없습니다. 설정을 확인해주세요.", Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    private suspend fun refreshQRCodeWithoutCache() {
        val sharedPreferences = activity?.getSharedPreferences("com.icecream.kwklasplus", Context.MODE_PRIVATE)
        val stdNumber = sharedPreferences?.getString("library_stdNumber", null)
        val phone = sharedPreferences?.getString("library_phone", null)
        val password = sharedPreferences?.getString("library_password", null)

        if (stdNumber != null && phone != null && password != null) {
            val realId = "0$stdNumber"
            val userInfoHash = getUserInfoHash(stdNumber, phone, password)
            cacheManager.clearCache(realId, userInfoHash)
            refreshQRCode()
        } else {
            Snackbar.make(requireView(), "QR 코드를 새로고침할 수 없습니다. 설정을 확인해주세요.", Snackbar.LENGTH_SHORT).show()
        }
    }


    private suspend fun displayQR(stdNumber: String, phone: String, password: String) =
        withContext(Dispatchers.IO) {
            val realId = "0$stdNumber"
            val userInfoHash = getUserInfoHash(stdNumber, phone, password)

            try {
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

                val qrData = getQrCode(realId, authKey)

                displayQrCode(qrData)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, e.toString())
                }
                cacheManager.clearCache(realId, userInfoHash)
            }
        }

    private fun getUserInfoHash(stdNumber: String, phone: String, password: String): String {
        return (stdNumber + phone + password).hashCode().toString()
    }

    private suspend fun getSecretKey(realId: String): String = withContext(Dispatchers.IO) {
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
        parseXmlResponse(responseData ?: "", "sec_key")
            ?: throw Exception("Failed to get secret key")
    }

    private suspend fun login(
        realId: String,
        stdNumber: String,
        phone: String,
        password: String,
        secret: String
    ): String = withContext(Dispatchers.IO) {
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
        parseXmlResponse(loginResponseData ?: "", "auth_key") ?: throw Exception("Login failed")
    }

    private suspend fun getQrCode(realId: String, authKey: String): JSONObject =
        withContext(Dispatchers.IO) {
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
            xmlToJson(qrResponseData ?: "")
                ?: throw Exception("Failed to get QR code data")
        }

    private suspend fun displayQrCode(qrData: JSONObject) = withContext(Dispatchers.Main) {
        if (!qrData.has("qr_code") || qrData.getString("qr_code").length < 5) {
            qrProgressBar.visibility = View.GONE
            qrImg.visibility = View.VISIBLE
            if (!isRetry) {
                isRetry = true
                refreshQRCodeWithoutCache()
            } else {
                val builder = MaterialAlertDialogBuilder(requireContext())
                    .setTitle("오류")
                    .setMessage("모바일 학생증 정보를 가져올 수 없습니다.\n모바일 학생증 설정에서 입력한 정보가 올바른지 확인한 후 다시 시도해주세요.")
                    .setPositiveButton("확인") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        } else {
            isRetry = false
            val isDarkMode =
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

            val qrColor = if (isDarkMode) Color.WHITE else Color.BLACK
            val backgroundColor = Color.TRANSPARENT

            name.text = qrData.getString("user_name")
            numberAndDepartment.text = "광운대학교 ${qrData.getString("user_code").trim()}\n${qrData.getString("user_deptName")} ${qrData.getString("user_patName")}"

            val qrValue = qrData.getString("qr_code")
            val qrgEncoder = QRGEncoder(qrValue, null, QRGContents.Type.TEXT, 200)
            qrgEncoder.colorBlack = qrColor
            qrgEncoder.colorWhite = backgroundColor
            try {
                val bitmap = qrgEncoder.getBitmap(0)
                qrImg.setImageBitmap(bitmap)
                qrProgressBar.visibility = View.GONE
                qrImg.visibility = View.VISIBLE
                val layoutParams = activity?.window?.attributes
                layoutParams?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                activity?.window?.attributes = layoutParams
            } catch (e: WriterException) {
                Log.v(TAG, e.toString())
            }
        }
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
        var currentTag = ""
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                }
                XmlPullParser.TEXT -> {
                    jsonObject.put(currentTag, parser.text)
                }
            }
            eventType = parser.next()
        }
        return jsonObject
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // 화면 밝기 원래대로
        val layoutParams = activity?.window?.attributes
        layoutParams?.screenBrightness = originalBrightness
        activity?.window?.attributes = layoutParams

        if (isWidget) {
            activity?.finish()
        }
    }

    override fun onDestroyView() {
        countDownTimer?.cancel()
        coroutineScope.cancel()
        super.onDestroyView()
    }

    companion object {
        const val TAG = "LibraryQRModal"
    }
}

class CacheManager(context: Context) {
    private val sharedPreferences =
        context.getSharedPreferences("LibraryQRCache", Context.MODE_PRIVATE)

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