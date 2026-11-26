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
        val eyeCenterY = height * 0.37f
        val eyeHalfWidth = width * 0.38f
        val maxUpperLid = height * 0.145f * openProgress.coerceAtLeast(0.05f)
        val maxLowerLid = height * 0.10f * openProgress.coerceAtLeast(0.04f)

        val pupilNormOffsetX = computeScanOffsetX()

        val charSize = (width * 0.03f).coerceIn(17f, 32f)
        browPaint.textSize = charSize * 0.92f
        lidPaint.textSize = charSize
        lashPaint.textSize = charSize * 0.85f
        scleraPaint.textSize = charSize * 0.9f
        irisPaint.textSize = charSize
        pupilPaint.textSize = charSize * 1.12f
        streamPaint.textSize = charSize * 0.85f

        val isGreenMode = currentState == EyeState.CONNECTING || currentState == EyeState.CONNECTED

        val browSteps = 27
        val browBaseY = eyeCenterY - maxUpperLid - charSize * 3.1f
        browPaint.color = if (isGreenMode) colorBrowActive else colorBrow
        for (i in 0 until browSteps) {
            val u = (i / (browSteps - 1f)) * 2f - 1f
            val bx = centerX + u * (eyeHalfWidth * 1.04f)
            val arch = (1f - u * u) * (charSize * 1.15f) - (u * 0.35f * charSize)
            val byCenter = browBaseY - arch
            val thicknessRows = when {
                u < -0.2f -> 4
                u < 0.45f -> 3
                u < 0.8f -> 2
                else -> 1
            }
            for (t in 0 until thicknessRows) {
                val offsetY = (t - thicknessRows / 2f) * (charSize * 0.52f)
                val slant = when {
                    u < -0.15f -> if (t % 2 == 0) '/' else '|'
                    u > 0.35f -> if (t % 2 == 0) '\\' else '-'
                    else -> browChars[(i * 3 + t * 5) % browChars.size]
                }
                val edgeFade = (1f - abs(u) * 0.45f).coerceIn(0.45f, 1f)
                browPaint.alpha = (225 * edgeFade).toInt().coerceIn(80, 255)
                canvas.drawText(slant.toString(), bx, byCenter + offsetY, browPaint)
            }
        }

        val activeStreamColor = if (isGreenMode) colorCodeGreen else colorCodeGray
        val maxStreamDrop = (height * 0.46f).coerceAtLeast(140f)

        for (grain in codeGrains) {
            if (grain.y > maxStreamDrop) continue
            val gx = centerX + grain.xRatio * (width * 0.46f)
            val eyeColNorm = ((gx - centerX) / eyeHalfWidth).coerceIn(-1f, 1f)
            val lowerCurveFactor = (1f - abs(eyeColNorm).pow(1.65f)).coerceAtLeast(0f)
            val lowerContourY = eyeCenterY + maxLowerLid * lowerCurveFactor

            val gy = lowerContourY + charSize * 0.75f + grain.y
            if (gy > height - charSize * 0.3f) continue

            val fade = (1f - (grain.y / maxStreamDrop) * 0.65f).coerceIn(0.25f, 1f)
            streamPaint.color = activeStreamColor
            streamPaint.alpha = (grain.alpha * fade).toInt().coerceIn(30, 255)
            canvas.drawText(grain.glyph.toString(), gx, gy, streamPaint)
        }

        val cols = 21
        val rows = 13

        for (r in -rows / 2..rows / 2) {
            val rowNorm = r / (rows / 2f)
            for (c in -cols / 2..cols / 2) {
                val colNorm = c / (cols / 2f)
                val almondFactor = (1f - abs(colNorm).pow(1.65f)).coerceAtLeast(0f)
                if (almondFactor <= 0.02f && abs(colNorm) < 0.95f) continue

                val x = centerX + colNorm * eyeHalfWidth
                val y = if (rowNorm < 0f) {
                    eyeCenterY + rowNorm * maxUpperLid * almondFactor
                } else {
                    eyeCenterY + rowNorm * maxLowerLid * almondFactor
                }

                val isUpperBorder = r == -rows / 2 && almondFactor > 0.1f
                val isLowerBorder = r == rows / 2 && almondFactor > 0.15f
                if (isUpperBorder && openProgress > 0.1f) {
                    val lashSlope = -colNorm * 0.45f
                    val lashX = x + lashSlope * charSize * 0.5f
                    val lashY = y - charSize * (0.65f + 0.35f * almondFactor)
                    lashPaint.alpha = (145 + 100 * openProgress).toInt().coerceIn(120, 255)
                    val lashGlyph = if (colNorm < -0.2f) '\\' else if (colNorm > 0.2f) '/' else '|'
                    canvas.drawText(lashGlyph.toString(), lashX, lashY, lashPaint)
                } else if (isLowerBorder && openProgress > 0.35f) {
                    val lashY = y + charSize * 0.65f
                    lashPaint.alpha = 140
                    canvas.drawText(lashChars[abs(c) % lashChars.size].toString(), x, lashY, lashPaint)
                }

                val onLidEdge = abs(abs(rowNorm) - 1f) < 0.22f || (openProgress < 0.2f && r == 0)
                if (onLidEdge) {
                    lidPaint.alpha = 240
                    val ch = if (openProgress < 0.2f) {
                        if (abs(colNorm) < 0.88f) '-' else '>'
                    } else {
                        lidChars[(abs(r * 5 + c * 11)) % lidChars.size]
                    }
                    canvas.drawText(ch.toString(), x, y, lidPaint)
                    continue
                }

                if (openProgress > 0.22f && abs(rowNorm) < 0.92f) {
                    val verticalScale = if (rowNorm < 0f) maxUpperLid else maxLowerLid
                    val vNorm = if (verticalScale > 1f) {
                        abs(y - eyeCenterY) / verticalScale
                    } else {
                        1f
                    }
                    val irisDist = hypot(
                        ((colNorm - pupilNormOffsetX) * 1.15).toDouble(),
                        (vNorm * 1.15).toDouble()
                    )

                    when {
                        irisDist < 0.17 -> {
                            pupilPaint.color = colorPupilGreen
                            pupilPaint.alpha = 255
                            val pGlyph = pupilChars[(abs(r * 3 + c * 7)) % pupilChars.size]
                            canvas.drawText(pGlyph.toString(), x, y, pupilPaint)
                        }
                        irisDist < 0.38 -> {
                            val pulse = ((sin(System.nanoTime() / 2.2e8) + 1.0) * 0.5).toFloat()
                            irisPaint.color = if (isGreenMode) colorIrisRing else colorLid
                            irisPaint.alpha = (180 + pulse * 65).toInt().coerceIn(160, 255)
                            val iGlyph = irisChars[(abs(r * 11 + c * 3)) % irisChars.size]
                            canvas.drawText(iGlyph.toString(), x, y, irisPaint)
                        }
                        else -> {
                            val minX = pupilNormOffsetX - 0.18f
                            val maxX = pupilNormOffsetX - 0.04f
                            val isHighlight = colNorm >= minX && colNorm <= maxX &&
                                rowNorm >= -0.55f && rowNorm <= -0.2f
                            if (isHighlight) {
                                scleraPaint.color = colorHighlight
                                scleraPaint.alpha = 255
                                canvas.drawText("*", x, y, scleraPaint)
                            } else {
                                scleraPaint.color = colorSclera
                                scleraPaint.alpha = (195 * openProgress).toInt().coerceIn(90, 235)
                                val sGlyph = scleraChars[(abs(r * 7 + c * 5)) % scleraChars.size]
                                canvas.drawText(sGlyph.toString(), x, y, scleraPaint)
                            }
                        }
                    }
                }
            }
        }
    }
}
