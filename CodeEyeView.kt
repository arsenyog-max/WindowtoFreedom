package io.github.romanvht.byedpi

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
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

    private val colorBrow = Color.parseColor("#607D8B")
    private val colorBrowActive = Color.parseColor("#80CBC4")
    private val colorLid = Color.parseColor("#90A4AE")
    private val colorLash = Color.parseColor("#CFD8DC")
    private val colorSclera = Color.parseColor("#ECEFF1")
    private val colorIrisRing = Color.parseColor("#69F0AE")
    private val colorPupilGreen = Color.parseColor("#00FF66")
    private val colorHighlight = Color.parseColor("#FFFFFF")
    private val colorCodeGray = Color.parseColor("#78909C")
    private val colorCodeGreen = Color.parseColor("#00E676")

    private var currentState: EyeState = EyeState.CLOSED
    private var openProgress: Float = 0f

    private val browPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = colorBrow
    }

    private val lidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = colorLid
    }

    private val lashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = colorLash
    }

    private val scleraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = colorSclera
    }

    private val irisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = colorIrisRing
    }

    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = colorPupilGreen
    }

    private val streamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = colorCodeGray
    }

    private data class CodeGrain(
        var xRatio: Float,
        var y: Float,
        var speed: Float,
        var glyph: Char,
        var alpha: Int
    )

    private val codeGrains = mutableListOf<CodeGrain>()
    private val browChars = listOf('/', '\\', '|', '-', '~', '^', ';', ':')
    private val lidChars = listOf('-', '=', '_', '/', '\\', ':', ';', '+')
    private val lashChars = listOf('|', '/', '\\', '\'', '`')
    private val scleraChars = listOf('.', ':', ';', '+', '~')
    private val irisChars = listOf('%', '&', '+', '=', '/')
    private val pupilChars = listOf('@', '#', '0', '*')
    private val streamChars = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '/', '\\', ':', ';', '=')

    private var openAnimator: ValueAnimator? = null
    private var streamAnimator: ValueAnimator? = null
    private var lastFrameTime: Long = 0L

    // Длительность однократного прохода зрачка: влево -> вправо -> остановка по центру
    private val scanDurationSec: Float = 1.6f
    private var scanElapsedSec: Float = scanDurationSec

    init {
        isClickable = true
        isFocusable = true
        initStream()
        startStreamLoop()
    }

    private fun initStream() {
        codeGrains.clear()
        repeat(140) {
            codeGrains.add(
                CodeGrain(
                    xRatio = Random.nextFloat() * 1.9f - 0.95f,
                    y = Random.nextFloat() * 900f,
                    speed = Random.nextFloat() * 115f + 65f,
                    glyph = streamChars.random(),
                    alpha = Random.nextInt(85, 230)
                )
            )
        }
    }

    fun toggleDemoState() {
        val next = when (currentState) {
            EyeState.CLOSED -> EyeState.CONNECTING
            EyeState.CONNECTING -> EyeState.CONNECTED
            EyeState.CONNECTED -> EyeState.CLOSED
        }
        setEyeState(next, animate = true)
    }

    fun setEyeState(newState: EyeState, animate: Boolean = true) {
        if (currentState == newState && !animate) return
        currentState = newState

        if (newState == EyeState.CONNECTING) {
            scanElapsedSec = 0f
        } else {
            scanElapsedSec = scanDurationSec
        }

        val targetOpen = when (newState) {
            EyeState.CLOSED -> 0f
            EyeState.CONNECTING, EyeState.CONNECTED -> 1f
        }

        openAnimator?.cancel()
        if (animate) {
            openAnimator = ValueAnimator.ofFloat(openProgress, targetOpen).apply {
                duration = 380L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    openProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            openProgress = targetOpen
            invalidate()
        }
    }

    private fun computeScanOffsetX(): Float {
        if (currentState != EyeState.CONNECTING || scanElapsedSec >= scanDurationSec) {
            return 0f
        }
        val t = (scanElapsedSec / scanDurationSec).coerceIn(0f, 1f)
        // Волна: влево (-1), затем вправо (+1), и к t=1 плавно возвращается в 0 (центр)
        val wave = -sin(2.0 * PI * t).toFloat() * (1f - t * 0.35f)
        return (wave * 0.38f).coerceIn(-0.42f, 0.42f)
    }

    private fun startStreamLoop() {
        if (streamAnimator?.isRunning == true) return
        lastFrameTime = System.nanoTime()
        streamAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val now = System.nanoTime()
                val dt = ((now - lastFrameTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameTime = now

                if (currentState == EyeState.CONNECTING && scanElapsedSec < scanDurationSec) {
                    scanElapsedSec = (scanElapsedSec + dt).coerceAtMost(scanDurationSec)
                }

                val streamAreaHeight = (height * 0.6f).coerceAtLeast(140f)
                for (grain in codeGrains) {
                    grain.y += grain.speed * dt
                    if (Random.nextFloat() < 0.14f) {
                        grain.glyph = streamChars.random()
                    }
                    if (grain.y > streamAreaHeight) {
                        grain.y = 0f
                        grain.x
                        
