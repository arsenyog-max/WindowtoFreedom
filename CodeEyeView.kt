package io.github.romanvht.byedpi

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class CodeEyeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class EyeState {
        CLOSED,
        CONNECTING,
        CONNECTED
    }

    private val colorCrease = Color.parseColor("#546E7A")
    private val colorLid = Color.parseColor("#B0BEC5")
    private val colorLidDark = Color.parseColor("#78909C")
    private val colorLash = Color.parseColor("#CFD8DC")
    private val colorSclera = Color.parseColor("#ECEFF1")
    private val colorIrisRing = Color.parseColor("#69F0AE")
    private val colorPupilGreen = Color.parseColor("#00FF66")
    private val colorHighlight = Color.parseColor("#FFFFFF")
    private val colorCodeGray = Color.parseColor("#78909C")
    private val colorCodeGreen = Color.parseColor("#00E676")

    private var currentState: EyeState = EyeState.CLOSED
    private var openProgress: Float = 0f

    private val creasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = colorCrease
    }

    private val lidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign
        
