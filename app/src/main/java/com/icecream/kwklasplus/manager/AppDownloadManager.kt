package com.icecream.kwklasplus.manager

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.icecream.kwklasplus.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class AppDownloadManager(private val activity: Activity) {

    private val downloadManager =
        activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun attachTo(webView: WebView) {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            startDownload(url, userAgent, contentDisposition, mimetype)
        }
    }

    private fun startDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String?
    ) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))

            val mime = resolveMimeType(url, mimetype)
            request.setMimeType(mime)

            val cookies = CookieManager.getInstance().getCookie(url)
            if (!cookies.isNullOrBlank()) {
                request.addRequestHeader("cookie", cookies)
            }
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("파일 다운로드 중...")

            var filename = URLUtil.guessFileName(
                url,
                contentDisposition,
                mime
            )

            try {
                filename = URLDecoder.decode(
                    filename,
                    StandardCharsets.UTF_8.name()
                )
            } catch (_: Exception) {
            }

            filename =
                filename.replace("[\\\\/:*?\"<>|]".toRegex(), "_")

            request.setTitle(filename)

            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )

            // 다운로드 폴더 저장
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                filename
            )

            val downloadId = downloadManager.enqueue(request)

            showProgressDialog(
                downloadId,
                filename,
                mime
            )
        } catch (e: Exception) {
            Log.e("AppDownloadManager", "Download failed", e)
            Toast.makeText(
                activity,
                "다운로드에 실패했습니다: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showProgressDialog(
        downloadId: Long,
        filename: String,
        mimeType: String
    ) {
        val dialogView = LayoutInflater.from(activity)
            .inflate(
                R.layout.layout_download_progress,
                null
            )

        val progressBar =
            dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val progressText =
            dialogView.findViewById<TextView>(R.id.progressText)
        val fileNameText =
            dialogView.findViewById<TextView>(R.id.fileName)

        fileNameText.text = filename

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("다운로드 중")
            .setView(dialogView)
            .setNegativeButton("취소") { _, _ ->
                downloadManager.remove(downloadId)
            }
            .setCancelable(false)
            .create()

        dialog.show()

        val scope =
            (activity as? AppCompatActivity)?.lifecycleScope
                ?: CoroutineScope(
                    Dispatchers.Main + Job()
                )

        scope.launch {
            var downloading = true

            while (downloading) {
                val query =
                    DownloadManager.Query()
                        .setFilterById(downloadId)

                val cursor: Cursor =
                    downloadManager.query(query)

                try {
                    if (cursor.moveToFirst()) {
                        val status =
                            cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_STATUS
                                )
                            )

                        val downloaded =
                            cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                                )
                            )

                        val total =
                            cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                                )
                            )

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                downloading = false
                                dialog.dismiss()
                                showCompletionDialog(
                                    downloadId,
                                    filename,
                                    mimeType
                                )
                            }

                            DownloadManager.STATUS_FAILED -> {
                                downloading = false
                                dialog.dismiss()

                                Toast.makeText(
                                    activity,
                                    "다운로드 실패",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            else -> {
                                if (total > 0) {
                                    val progress =
                                        (downloaded * 100L / total)
                                            .toInt()

                                    progressBar.progress =
                                        progress
                                    progressText.text =
                                        "$progress%"
                                }
                            }
                        }
                    }
                } finally {
                    cursor.close()
                }

                if (downloading) {
                    delay(300)
                }
            }
        }
    }

    private fun showCompletionDialog(
        downloadId: Long,
        filename: String,
        mimeType: String
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("다운로드 완료")
            .setMessage(
                "$filename\n다운로드가 완료되었습니다."
            )
            .setPositiveButton("열기") { _, _ ->
                openFile(
                    downloadId,
                    filename,
                    mimeType
                )
            }
            .setNeutralButton("폴더 열기") { _, _ ->
                openDownloadsFolder()
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun openFile(
        downloadId: Long,
        filename: String,
        mimeType: String?
    ) {
        try {
            val uri =
                downloadManager.getUriForDownloadedFile(
                    downloadId
                )

            if (uri == null) {
                Toast.makeText(
                    activity,
                    "파일 URI를 찾을 수 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val finalMime =
                resolveMimeType(
                    filename,
                    mimeType
                )

            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        uri,
                        finalMime
                    )

                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    clipData =
                        ClipData.newRawUri(
                            "",
                            uri
                        )
                }

            activity.startActivity(
                Intent.createChooser(
                    intent,
                    "파일 열기"
                )
            )
        } catch (e: Exception) {
            Log.e(
                "AppDownloadManager",
                "Failed to open file",
                e
            )

            Toast.makeText(
                activity,
                "파일을 열 수 있는 앱이 없거나 오류가 발생했습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun resolveMimeType(
        filenameOrUrl: String,
        mimeType: String?
    ): String {

        if (!mimeType.isNullOrBlank() &&
            mimeType != "application/octet-stream"
        ) {
            return mimeType
        }

        val ext =
            MimeTypeMap.getFileExtensionFromUrl(
                filenameOrUrl
            ).lowercase()

        return when (ext) {
            "pdf" ->
                "application/pdf"

            "hwp" ->
                "application/x-hwp"

            "hwpx" ->
                "application/haansoft-hwpx"

            "doc" ->
                "application/msword"

            "docx" ->
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

            "xls" ->
                "application/vnd.ms-excel"

            "xlsx" ->
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

            "ppt" ->
                "application/vnd.ms-powerpoint"

            "pptx" ->
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"

            "txt" ->
                "text/plain"

            "csv" ->
                "text/csv"

            "jpg",
            "jpeg" ->
                "image/jpeg"

            "png" ->
                "image/png"

            "gif" ->
                "image/gif"

            "webp" ->
                "image/webp"

            "zip" ->
                "application/zip"

            else ->
                MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(ext)
                    ?: "application/octet-stream"
        }
    }

    private fun openDownloadsFolder() {
        val intent =
            Intent(
                DownloadManager.ACTION_VIEW_DOWNLOADS
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                activity,
                "폴더를 열 수 없습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}