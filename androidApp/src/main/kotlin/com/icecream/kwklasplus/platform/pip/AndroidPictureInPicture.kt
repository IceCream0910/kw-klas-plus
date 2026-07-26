package com.icecream.kwklasplus.platform.pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import com.icecream.kwklasplus.core.platform.PictureInPicture
import com.icecream.kwklasplus.core.platform.PictureInPictureState
import com.icecream.kwklasplus.core.platform.PlatformActionResult

class AndroidPictureInPicture(
    private val activity: Activity,
) : PictureInPicture {
    override suspend fun enter(state: PictureInPictureState): PlatformActionResult =
        enterNow(state, emptyList())

    override suspend fun exit(): PlatformActionResult = PlatformActionResult.Unsupported

    fun enterNow(
        state: PictureInPictureState,
        actions: List<RemoteAction>,
    ): PlatformActionResult {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return PlatformActionResult.Unsupported
        }
        val params = createParams(state, actions) ?: return PlatformActionResult.Failed("invalid_pip_state")
        return try {
            if (activity.enterPictureInPictureMode(params)) PlatformActionResult.Success
            else PlatformActionResult.Failed("pip_entry_rejected")
        } catch (_: IllegalStateException) {
            PlatformActionResult.Failed("pip_entry_rejected")
        }
    }

    fun update(
        state: PictureInPictureState,
        actions: List<RemoteAction>,
        autoEnterEnabled: Boolean = false,
    ): PlatformActionResult {
        val params = createParams(state, actions, autoEnterEnabled)
            ?: return PlatformActionResult.Failed("invalid_pip_state")
        return try {
            activity.setPictureInPictureParams(params)
            PlatformActionResult.Success
        } catch (_: IllegalStateException) {
            PlatformActionResult.Failed("pip_update_rejected")
        }
    }

    private fun createParams(
        state: PictureInPictureState,
        actions: List<RemoteAction>,
        autoEnterEnabled: Boolean = false,
    ): PictureInPictureParams? {
        if (state.aspectRatioWidth <= 0 || state.aspectRatioHeight <= 0) return null
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(state.aspectRatioWidth, state.aspectRatioHeight))
            .setActions(actions)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(autoEnterEnabled)
                }
            }
            .build()
    }
}
