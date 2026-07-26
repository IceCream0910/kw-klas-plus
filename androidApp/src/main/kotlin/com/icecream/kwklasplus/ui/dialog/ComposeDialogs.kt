package com.icecream.kwklasplus.ui.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme

class ComposeLoadingDialog(
    context: Context,
    allowTouchesOutside: Boolean = false,
) : ComponentDialog(context) {
    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCancelable(false)
        setContentView(
            ComposeView(context).apply {
                setContent {
                    KlasPlusTheme {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            tonalElevation = 6.dp,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(28.dp),
                            )
                        }
                    }
                }
            },
        )
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (allowTouchesOutside) {
            setOnShowListener {
                window?.addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                )
            }
        }
    }
}

class ComposeDownloadProgressDialog(
    context: Context,
    private val fileName: String,
    private val onCancel: () -> Unit,
) : ComponentDialog(context) {
    private var progress by mutableIntStateOf(0)

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCancelable(false)
        setContentView(
            ComposeView(context).apply {
                setContent {
                    KlasPlusTheme {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 560.dp),
                                shape = MaterialTheme.shapes.extraLarge,
                                tonalElevation = 6.dp,
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                ) {
                                    Text(
                                        text = "다운로드 중",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(20.dp))
                                    Text(
                                        text = fileName,
                                        maxLines = 1,
                                        overflow = TextOverflow.MiddleEllipsis,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    LinearProgressIndicator(
                                        progress = { progress / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "$progress%",
                                        modifier = Modifier.align(Alignment.End),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        TextButton(
                                            onClick = {
                                                onCancel()
                                                dismiss()
                                            },
                                        ) {
                                            Text("취소")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setOnShowListener {
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    fun updateProgress(value: Int) {
        progress = value.coerceIn(0, 100)
    }
}
