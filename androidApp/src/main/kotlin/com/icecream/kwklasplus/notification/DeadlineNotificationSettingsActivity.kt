package com.icecream.kwklasplus.notification

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.icecream.kwklasplus.appDependencies
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import com.icecream.kwklasplus.ui.theme.KlasButtonHeight
import com.icecream.kwklasplus.ui.theme.KlasControlShape
import com.icecream.kwklasplus.ui.theme.klasInverseButtonColors
import kotlinx.coroutines.CancellationException
import com.icecream.kwklasplus.ui.onboarding.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch

class DeadlineNotificationSettingsActivity: AppCompatActivity() {
    private var checking by mutableStateOf(true)
    private var busy by mutableStateOf(false)
    private var denied by mutableStateOf(false)
    private var completed by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var awaitingSettings=false
    private val attempt get()=intent.getLongExtra("consent_attempt",0)
    private val permissionRequest=registerForActivityResult(ActivityResultContracts.RequestPermission()) { complete() }
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        awaitingSettings=savedInstanceState?.getBoolean("awaiting_settings") ?: false
        completed=savedInstanceState?.getBoolean("completed") ?: false
        setContent {
            KlasPlusTheme {
                ModalBottomSheet(onDismissRequest={ if(!busy)finish() },sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),dragHandle=null) {
                    Box(Modifier.fillMaxWidth()) {
                        FunnelGlow(Modifier.align(Alignment.TopCenter))
                        Column(Modifier.fillMaxWidth()) {
                            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            AnimatedContent(targetState=completed,label="permission-completion") { success ->
                                Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                                    Text(if(success) "알림 설정 완료!" else "곧 마감되는 할 일 알림 켜기",style=MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    if(checking) {
                                        CircularProgressIndicator(Modifier.size(28.dp))
                                        Text("알림 설정을 확인하고 있어요.",style=MaterialTheme.typography.bodySmall)
                                    } else {
                                        Text(if(success) "앱을 사용하고 있지 않을 때, 주기적으로 24시간 이내에 마감되는 할 일이 있는지 확인해서 알림을 보내줄게요." else "24시간 이내에 마감되는 할 일을 모아 알림으로 받아보세요.",style=MaterialTheme.typography.bodyMedium)
                                        if(success) {
                                            Text("동일한 항목은 하루에 한 번만 발송되며, 기기 상태에 따라 알림이 늦어질 수 있어요.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                            DeadlineNotificationPreview()
                                            Button(onClick={ finish() },shape=KlasControlShape,colors=klasInverseButtonColors(),modifier=Modifier.fillMaxWidth().height(KlasButtonHeight)) { Text("닫기") }
                                        } else {
                                            if(denied)Text("기기 설정에서 KLAS+ 알림을 허용한 뒤 돌아와 주세요.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                                            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                                            DeadlineNotificationPreview()
                                            Column(Modifier.fillMaxWidth()) {
                                                Button(onClick={ if(denied)openSystemSettings() else requestPermission() },enabled=!busy,shape=KlasControlShape,colors=klasInverseButtonColors(),modifier=Modifier.fillMaxWidth().height(KlasButtonHeight)) {
                                                    Text(if(denied)"시스템 설정으로 이동" else "권한 허용하기")
                                                }
                                                TextButton(onClick={ finish() },enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("나중에") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            try {
                val permission=appDependencies.reminders.engine.settings().permission
                if(!completed && permission in listOf("authorized","provisional")) { complete();return@launch }
                else if(!completed && permission=="denied" && (Build.VERSION.SDK_INT<33 || getPreferences(MODE_PRIVATE).getBoolean("permission_requested",false)))denied=true
            } catch(cause: Exception) {
                if(cause is CancellationException)throw cause
                error="알림 설정을 확인하지 못했어요. 다시 시도해 주세요."
            }
            checking=false
        }
    }
    private fun requestPermission() { lifecycleScope.launch {
        busy=true;error=null
        try {
            val permission=appDependencies.reminders.engine.settings().permission
            if(permission in listOf("authorized","provisional"))complete()
            else if(Build.VERSION.SDK_INT>=33 && (!getPreferences(MODE_PRIVATE).getBoolean("permission_requested",false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                getPreferences(MODE_PRIVATE).edit().putBoolean("permission_requested",true).apply()
                permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else { denied=true;busy=false }
        } catch(cause: Exception) { if(cause is CancellationException)throw cause;error="권한 상태를 확인하지 못했어요. 다시 시도해 주세요.";busy=false }
    } }
    private fun complete() { lifecycleScope.launch {
        busy=true
        try {
            when(appDependencies.reminders.consent.complete(attempt)) {
                "COMPLETED" -> { completed=true;denied=false;error=null }
                "PERMISSION_DENIED" -> denied=true
                else -> { error="설정 요청이 취소되었어요. 시트를 닫고 다시 켜 주세요." }
            }
        } catch(cause: Exception) { if(cause is CancellationException)throw cause;denied=false;error="알림 설정을 저장하지 못했어요. 다시 시도해 주세요." }
        finally { busy=false;checking=false }
    } }
    override fun onResume() {
        super.onResume()
        if(awaitingSettings) { awaitingSettings=false;complete() }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("completed",completed);outState.putBoolean("awaiting_settings",awaitingSettings);super.onSaveInstanceState(outState) }
    override fun onDestroy() {
        if(isFinishing)appDependencies.reminders.cancelConsent(attempt)
        super.onDestroy()
    }
    private fun openSystemSettings() {
        awaitingSettings=true
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
    }
}
