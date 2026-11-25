package io.github.romanvht.byedpi.views

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

    private var currentState: EyeState = EyeState.CLOSED
    private var openProgress: Float = 0f

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#B8C4CC")
    }

    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#00FF66")
    }

    private val sandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#00E676")
    }

    private data class SandGrain(
        var xRatio: Float,
        var y: Float,
        var speed: Float,
        var digit: Char,
        var alpha: Int
    )

    private val sandGrains = mutableListOf<SandGrain>()
    private val codeChars = listOf('0', '1', '/', '\\', '-', '_', '=', '+', '<', '>', ';', ':')
    private val digits = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')

    private var openAnimator: ValueAnimator? = null
    private var sandAnimator: ValueAnimator? = null
    private var lastFrameTime: Long = 0L

    init {
        isClickable = true
        isFocusable = true
        initSand()
    }

    private fun initSand() {
        sandGrains.clear()
        repeat(52) {
            sandGrains.add(
                SandGrain(
                    xRatio = Random.nextFloat() * 0.72f - 0.36f,
                    y = Random.nextFloat() * 160f,
                    speed = Random.nextFloat() * 95f + 75f,
                    digit = digits.random(),
                    alpha = Random.nextInt(100, 240)
                )
            )
        }
    }

    fun setEyeState(newState: EyeState, animate: Boolean = true) {
        if (currentState == newState && !animate) return
        currentState = newState

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

        if (newState == EyeState.CONNECTING) {
            startSandLoop()
        } else {
            stopSandLoop()
            invalidate()
        }
    }

    private fun startSandLoop() {
        if (sandAnimator?.isRunning == true) return
        lastFrameTime = System.nanoTime()
        sandAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val now = System.nanoTime()
                val dt = ((now - lastFrameTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameTime = now

                val maxSandHeight = height * 0.38f
                for (grain in sandGrains) {
                    grain.y += grain.speed * dt
                    if (Random.nextFloat() < 0.18f) {
                        grain.digit = digits.random()
                    }
                    if (grain.y > maxSandHeight) {
                        grain.y = 0f
                        grain.xRatio = Random.nextFloat() * 0.72f - 0.36f
                        grain.speed = Random.nextFloat() * 95f + 75f
                        grain.alpha = Random.nextInt(110, 245)
                    }
                }
                invalidate()
            }
            start()
        }
    }

    private fun stopSandLoop() {
        sandAnimator?.cancel()
        sandAnimator = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        openAnimator?.cancel()
        stopSandLoop()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val centerX = width / 2f
        val eyeCenterY = height * 0.36f
        val eyeWidth = width * 0.36f
        val maxLidHeight = height * 0.14f * openProgress.coerceAtLeast(0.08f)

        val charSize = (width * 0.032f).coerceIn(18f, 34f)
        eyePaint.textSize = charSize
        pupilPaint.textSize = charSize * 1.1f
        sandPaint.textSize = charSize * 0.85f

        val cols = 17
        val rows = 9

        for (r in -rows / 2..rows / 2) {
            val rowNorm = r / (rows / 2f)
            for (c in -cols / 2..cols / 2) {
                val colNorm = c / (cols / 2f)
                val x = centerX + colNorm * eyeWidth
                val lidCurve = (1f - abs(colNorm) * abs(colNorm)).coerceAtLeast(0f)
                val y = eyeCenterY + rowNorm * maxLidHeight * lidCurve

                val distFromCenter = hypot(
                    colNorm.toDouble(),
                    (rowNorm / openProgress.coerceAtLeast(0.15f)).toDouble()
                )

                when {
                    currentState == EyeState.CONNECTED && distFromCenter < 0.42 -> {
                        pupilPaint.alpha = 255
                        val glyph = if (distFromCenter < 0.2) '#' else '*'
                        canvas.drawText(glyph.toString(), x, y, pupilPaint)
                    }

                    openProgress > 0.25f && distFromCenter < 0.48 -> {
                        val pulse = ((sin(System.nanoTime() / 1.8e8) + 1.0) * 0.5).toFloat()
                        eyePaint.color = if (currentState == EyeState.CONNECTING) {
                            Color.argb(230, (80 + pulse * 120).toInt(), 255, (120 + pulse * 100).toInt())
                        } else {
                            Color.parseColor("#E0F2F1")
                        }
                        canvas.drawText("o", x, y, eyePaint)
                    }

                    abs(abs(rowNorm) - 1f) < 0.28f || (openProgress < 0.25f && r == 0) -> {
                        eyePaint.color = Color.parseColor("#90A4AE")
                        eyePaint.alpha = 230
                        val ch = if (openProgress < 0.25f && r == 0) '-' else codeChars[(abs(r * 7 + c * 13)) % codeChars.size]
                        canvas.drawText(ch.toString(), x, y, eyePaint)
                    }
                }
            }
        }

        if (currentState == EyeState.CONNECTING) {
            val startY = eyeCenterY + maxLidHeight + charSize * 1.4f
            for (grain in sandGrains) {
                val gx = centerX + grain.xRatio * eyeWidth
                val gy = startY + grain.y
                val fade = (1f - (grain.y / (height * 0.38f))).coerceIn(0.15f, 1f)
                sandPaint.alpha = (grain.alpha * fade).toInt().coerceIn(20, 255)
                canvas.drawText(grain.digit.toString(), gx, gy, sandPaint)
            }
        }
    }
}
