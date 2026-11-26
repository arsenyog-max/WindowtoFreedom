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
import kotlin.math.abs
import kotlin.math.hypot
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

    private val colorLid = Color.parseColor("#90A4AE")
    private val colorOpenEye = Color.parseColor("#E0F2F1")
    private val colorPupilGreen = Color.parseColor("#00FF66")
    private val colorCodeGray = Color.parseColor("#78909C")
    private val colorCodeGreen = Color.parseColor("#00E676")

    private var currentState: EyeState = EyeState.CLOSED
    private var openProgress: Float = 0f

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = colorLid
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
    private val lidChars = listOf('0', '1', '/', '\\', '-', '_', '=', '+', '<', '>', ';', ':')
    private val streamChars = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '/', '\\', ':', ';', '=')

    private var openAnimator: ValueAnimator? = null
    private var streamAnimator: ValueAnimator? = null
    private var lastFrameTime: Long = 0L
    private var pupilScanPhase: Float = 0f

    init {
        isClickable = true
        isFocusable = true
        initStream()
        startStreamLoop()
    }

    private fun initStream() {
        codeGrains.clear()
        repeat(130) {
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

        if (newState == EyeState.CONNECTED) {
            pupilScanPhase = 0f
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

                if (currentState == EyeState.CONNECTED) {
                    pupilScanPhase += dt * 2.4f
                }

                val streamAreaHeight = (height * 0.6f).coerceAtLeast(140f)
                for (grain in codeGrains) {
                    grain.y += grain.speed * dt
                    if (Random.nextFloat() < 0.14f) {
                        grain.glyph = streamChars.random()
                    }
                    if (grain.y > streamAreaHeight) {
                        grain.y = 0f
                        grain.xRatio = Random.nextFloat() * 1.9f - 0.95f
                        grain.speed = Random.nextFloat() * 115f + 65f
                        grain.alpha = Random.nextInt(90, 235)
                    }
                }
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startStreamLoop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        openAnimator?.cancel()
        streamAnimator?.cancel()
        streamAnimator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val centerX = width / 2f
        val eyeCenterY = height * 0.34f
        val eyeWidth = width * 0.36f
        val maxLidHeight = height * 0.14f * openProgress.coerceAtLeast(0.08f)

        // Смещение самого зрачка влево-вправо внутри глаза при активном подключении
        val pupilNormOffsetX = if (currentState == EyeState.CONNECTED) {
            sin(pupilScanPhase) * 0.42f
        } else {
            0f
        }

        val charSize = (width * 0.032f).coerceIn(18f, 34f)
        eyePaint.textSize = charSize
        pupilPaint.textSize = charSize * 1.1f
        streamPaint.textSize = charSize * 0.85f

        val isGreenMode = currentState == EyeState.CONNECTING || currentState == EyeState.CONNECTED

        // Нижняя граница глаза и поток кода под ним по всей ширине вниз
        val lowerLidY = eyeCenterY + maxLidHeight
        val streamStartY = lowerLidY + charSize * 0.8f
        val streamAreaHeight = (height - streamStartY - charSize * 0.5f).coerceAtLeast(140f)
        val activeStreamColor = if (isGreenMode) colorCodeGreen else colorCodeGray

        for (grain in codeGrains) {
            if (grain.y > streamAreaHeight) continue
            val gx = centerX + grain.xRatio * (width * 0.46f)
            val gy = streamStartY + grain.y
            val fade = (1f - (grain.y / streamAreaHeight) * 0.6f).coerceIn(0.25f, 1f)
            streamPaint.color = activeStreamColor
            streamPaint.alpha = (grain.alpha * fade).toInt().coerceIn(30, 255)
            canvas.drawText(grain.glyph.toString(), gx, gy, streamPaint)
        }

        // Глаз на месте, зрачок смещается влево-вправо
        val cols = 17
        val rows = 9

        for (r in -rows / 2..rows / 2) {
            val rowNorm = r / (rows / 2f)
            for (c in -cols / 2..cols / 2) {
                val colNorm = c / (cols / 2f)
                val x = centerX + colNorm * eyeWidth
                val lidCurve = (1f - abs(colNorm) * abs(colNorm)).coerceAtLeast(0f)
                val y = eyeCenterY + rowNorm * maxLidHeight * lidCurve

                val distFromPupil = hypot(
                    (colNorm - pupilNormOffsetX).toDouble(),
                    (rowNorm / openProgress.coerceAtLeast(0.15f)).toDouble()
                )

                when {
                    currentState == EyeState.CONNECTED && distFromPupil < 0.36 -> {
                        pupilPaint.color = colorPupilGreen
                        pupilPaint.alpha = 255
                        val glyph = if (distFromPupil < 0.18) '#' else '*'
                        canvas.drawText(glyph.toString(), x, y, pupilPaint)
                    }

                    openProgress > 0.25f && distFromPupil < 0.48 -> {
                        val pulse = ((sin(System.nanoTime() / 1.8e8) + 1.0) * 0.5).toFloat()
                        eyePaint.color = if (currentState == EyeState.CONNECTING) {
                            Color.argb(230, (80 + pulse * 120).toInt(), 255, (120 + pulse * 100).toInt())
                        } else {
                            colorOpenEye
                        }
                        canvas.drawText("o", x, y, eyePaint)
                    }

                    abs(abs(rowNorm) - 1f) < 0.28f || (openProgress < 0.25f && r == 0) -> {
                        eyePaint.color = colorLid
                        eyePaint.alpha = 230
                        val ch = if (openProgress < 0.25f && r == 0) {
                            '-'
                        } else {
                            lidChars[(abs(r * 7 + c * 13)) % lidChars.size]
                        }
                        canvas.drawText(ch.toString(), x, y, eyePaint)
                    }
                }
            }
        }
    }
}
