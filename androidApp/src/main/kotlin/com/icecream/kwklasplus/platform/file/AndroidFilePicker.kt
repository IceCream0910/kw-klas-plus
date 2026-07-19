package com.icecream.kwklasplus.platform.file

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.icecream.kwklasplus.core.platform.FilePicker
import com.icecream.kwklasplus.core.platform.FilePickerRequest
import com.icecream.kwklasplus.core.platform.FilePickerResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class AndroidFilePicker(
    private val activity: ComponentActivity,
) : FilePicker {
    private var continuation: Continuation<FilePickerResult>? = null
    private var webCallback: ValueCallback<Array<Uri>>? = null
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ::handleResult,
    )

    override suspend fun pick(request: FilePickerRequest): FilePickerResult =
        suspendCancellableCoroutine { pending ->
            cancelPending()
            continuation = pending
            pending.invokeOnCancellation {
                if (continuation === pending) continuation = null
            }
            launch(request)
        }

    fun showForWeb(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        cancelPending()
        webCallback = callback
        val request = FilePickerRequest(
            acceptedMimeTypes = params.acceptTypes?.toList().orEmpty(),
            allowMultiple = params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE,
        )
        return runCatching { launch(request) }.isSuccess.also { launched ->
            if (!launched) {
                webCallback?.onReceiveValue(null)
                webCallback = null
            }
        }
    }

    private fun launch(request: FilePickerRequest) {
        val mimeTypes = request.normalizedMimeTypes()
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = when {
                mimeTypes.size == 1 -> mimeTypes.first()
                else -> "*/*"
            }
            if (mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, request.allowMultiple)
        }
        launcher.launch(Intent.createChooser(intent, "파일 선택"))
    }

    private fun handleResult(result: ActivityResult) {
        val webResult = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        webCallback?.onReceiveValue(webResult)
        webCallback = null

        val selected = result.data?.selectedUris().orEmpty()
        continuation?.resume(
            if (selected.isEmpty()) FilePickerResult.Cancelled
            else FilePickerResult.Selected(selected.map(Uri::toString)),
        )
        continuation = null
    }

    private fun cancelPending() {
        webCallback?.onReceiveValue(null)
        webCallback = null
        continuation?.resume(FilePickerResult.Cancelled)
        continuation = null
    }
}

private fun Intent.selectedUris(): List<Uri> {
    val values = mutableListOf<Uri>()
    clipData?.let { clips: ClipData ->
        repeat(clips.itemCount) { index -> clips.getItemAt(index).uri?.let(values::add) }
    }
    data?.let { if (it !in values) values += it }
    return values
}
