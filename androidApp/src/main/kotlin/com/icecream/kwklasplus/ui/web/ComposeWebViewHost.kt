package com.icecream.kwklasplus.ui.web

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeWebViewHost(
    webView: WebView,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    applyImePadding: Boolean = true,
) {
    ComposeWebContentHost(
        contentView = webView,
        isLoading = isLoading,
        modifier = modifier,
        title = title,
        contentTag = "compose_web_view",
        applyImePadding = applyImePadding,
    )
}

@Composable
fun ComposeRefreshableWebViewHost(
    refreshLayout: SwipeRefreshLayout,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    applyImePadding: Boolean = true,
) {
    ComposeWebContentHost(
        contentView = refreshLayout,
        isLoading = isLoading,
        modifier = modifier,
        title = title,
        contentTag = "compose_refreshable_web_view",
        applyImePadding = applyImePadding,
    )
}

@Composable
fun ComposePlatformViewHost(
    contentView: View,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    contentTag: String = "compose_platform_view",
    applyImePadding: Boolean = true,
) {
    ComposeWebContentHost(
        contentView = contentView,
        isLoading = isLoading,
        modifier = modifier,
        title = title,
        contentTag = contentTag,
        applyImePadding = applyImePadding,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeWebContentHost(
    contentView: View,
    isLoading: Boolean,
    modifier: Modifier,
    title: String?,
    contentTag: String,
    applyImePadding: Boolean,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (title != null) {
                TopAppBar(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = {
                        Text(
                            title,
                            fontWeight = FontWeight.Bold
                        ) }
                )
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .then(if (applyImePadding) Modifier.imePadding() else Modifier)
                .testTag("compose_web_host"),
        ) {
            AndroidView(
                factory = {
                    contentView.apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { view ->
                    val layoutParams = view.layoutParams
                    if (
                        layoutParams.width != ViewGroup.LayoutParams.MATCH_PARENT ||
                        layoutParams.height != ViewGroup.LayoutParams.MATCH_PARENT
                    ) {
                        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                        view.layoutParams = layoutParams
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(contentTag),
            )
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .testTag("compose_web_loading"),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
