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
    private val lidChars = listOf('-', '=', '_', '/', '\\', ':', ';', '+')
    private val lashChars = listOf('|', '/', '\\', '\'', '`')
    private val scleraChars = listOf('.', ':', ';', '+', '~')
    private val irisChars = listOf('%', '&', '+', '=', '/')
    private val pupilChars = listOf('@', '#', '0', '*')
    private val streamChars = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '/', '\\', ':', ';', '=')

    private var openAnimator: ValueAnimator? = null
    private var streamAnimator: ValueAnimator? = null
    private var lastFrameTime: Long = 0L
    private var scanElapsedSec: Float = 0f

    init {
        isClickable = true
        isFocusable = true
        initStream()
        startStreamLoop()
    }

    private fun initStream() {
        codeGrains.clear()
        repeat(135) {
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

                // Зрачок двигается влево и вправо только во время подключения (CONNECTING)
                if (currentState == EyeState.CONNECTING) {
                    scanElapsedSec += dt
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
        val eyeHalfWidth = width * 0.38f
        val maxUpperLid = height * 0.15f * openProgress.coerceAtLeast(0.06f)
        val maxLowerLid = height * 0.105f * openProgress.coerceAtLeast(0.04f)

        // Во время CONNECTING зрачок идёт влево-вправо; при CONNECTED встаёт ровно по центру (0f)
        val pupilNormOffsetX = when (currentState) {
            EyeState.CONNECTING -> sin(scanElapsedSec * 2.5f) * 0.38f
            EyeState.CONNECTED -> 0f
            EyeState.CLOSED -> 0f
        }

        val charSize = (width * 0.03f).coerceIn(17f, 32f)
        lidPaint.textSize = charSize
        lashPaint.textSize = charSize * 0.85f
        scleraPaint.textSize = charSize * 0.9f
        irisPaint.textSize = charSize
        pupilPaint.textSize = charSize * 1.12f
        streamPaint.textSize = charSize * 0.85f

        val isGreenMode = currentState == EyeState.CONNECTING || currentState == EyeState.CONNECTED

        // Поток кода под глазом: серый в CLOSED, зелёный в CONNECTING и CONNECTED
        val streamStartY = eyeCenterY + maxLowerLid + charSize * 1.35f
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

        // Реалистичный контур миндалевидного глаза: верхнее веко более выпуклое, нижнее мягче
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

                // Ресницы над верхним веком и немного под нижним
                val isUpperBorder = r == -rows / 2 && almondFactor > 0.1f
                val isLowerBorder = r == rows / 2 && almondFactor > 0.15f
                if (isUpperBorder && openProgress > 0.12f) {
                    val lashSlope = -colNorm * 0.45f
                    val lashX = x + lashSlope * charSize * 0.5f
                    val lashY = y - charSize * (0.65f + 0.35f * almondFactor)
                    lashPaint.alpha = (140 + 100 * openProgress).toInt().coerceIn(120, 255)
                    val lashGlyph = if (colNorm < -0.2f) '\\' else if (colNorm > 0.2f) '/' else '|'
                    canvas.drawText(lashGlyph.toString(), lashX, lashY, lashPaint)
                } else if (isLowerBorder && openProgress > 0.35f) {
                    val lashY = y + charSize * 0.65f
                    lashPaint.alpha = 140
                    canvas.drawText(lashChars[abs(c) % lashChars.size].toString(), x, lashY, lashPaint)
                }

                // Веки (закрытая линия или края век)
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

                // Внутренняя область открытого глаза: белок (склера), радужка и зрачок
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
                            // Зрачок
                            pupilPaint.color = colorPupilGreen
                            pupilPaint.alpha = 255
                            val pGlyph = pupilChars[(abs(r * 3 + c * 7)) % pupilChars.size]
                            canvas.drawText(pGlyph.toString(), x, y, pupilPaint)
                        }
                        irisDist < 0.38 -> {
                            // Радужка вокруг зрачка
                            val pulse = ((sin(System.nanoTime() / 2.2e8) + 1.0) * 0.5).toFloat()
                            irisPaint.color = if (isGreenMode) colorIrisRing else colorLid
                            irisPaint.alpha = (180 + pulse * 65).toInt().coerceIn(160, 255)
                            val iGlyph = irisChars[(abs(r * 11 + c * 3)) % irisChars.size]
                            canvas.drawText(iGlyph.toString(), x, y, irisPaint)
                        }
                        else -> {
                            // Белок глаза (склера) с лёгким бликом вверху слева от зрачка
                            val isHighlight = colNorm in (pupilNormOffsetX - 0.18f)..(pupilNormOffsetX - 0.04f) &&
                                rowNorm in -0.55f..-0.2f
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
