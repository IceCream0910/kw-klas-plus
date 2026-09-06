package com.icecream.kwklasplus.widget

import android.content.Intent
import android.net.Uri
import com.icecream.kwklasplus.HomeActivity
import com.icecream.kwklasplus.core.academic.WidgetDestination

object WidgetNavigation {
    fun forward(source: Intent, target: Intent): Intent = target.apply {
        if (source.action == HomeActivity.ACTION_OPEN_LIBRARY_SETTINGS) {
            action = HomeActivity.ACTION_OPEN_LIBRARY_SETTINGS
        }
        WidgetDestination.fromUri(source.dataString)?.let { data = Uri.parse(it.uri) }
    }
}
