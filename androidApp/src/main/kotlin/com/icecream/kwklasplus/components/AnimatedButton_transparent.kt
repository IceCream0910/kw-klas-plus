package com.icecream.kwklasplus.components

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Gravity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.icecream.kwklasplus.R
import android.view.animation.OvershootInterpolator

class AnimatedButtonTransparent @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatButton(context, attrs, defStyleAttr) {

    private val scaleDownFactor = 0.98f
    private val animationDuration = 400L
    private val interpolator = OvershootInterpolator()


    init {
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(scaleDownFactor)
                        .scaleY(scaleDownFactor)
                        .setDuration(animationDuration)
                        .setInterpolator(interpolator)
                        .start()
                    false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(animationDuration)
                        .setInterpolator(interpolator)
                        .start()
                    false
                }

                else -> false
            }
        }
        gravity = Gravity.CENTER
        val padding = (15 * context.resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
    }
}