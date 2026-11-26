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

    private val colorShadowDeep = Color.parseColor("#3E2723")
    private val colorShadowMid = Color.parseColor("#6D4C41")
    private val colorUnderEyeShadow = Color.parseColor("#5D4037")
    private val colorLidWarm = Color.parseColor("#D7B899")
    private val colorScleraBright = Color.parseColor("#F5EFE6")
    private val colorHighlightWarm = Color.parseColor("#FFFDE7")
    private val colorIrisEmerald = Color.parseColor("#00E676")
    private val colorIrisGlow = Color.parseColor("#69F0AE")
    private val colorPupilGreen = Color.parseColor("#00FF66")
    private val colorCodeGray = Color.parseColor("#78909C")
    private val colorCodeGreen = Color.parseColor("#00E676")

    private var currentState: EyeState = EyeState.CLOSED
    private var openProgress: Float = 0f

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lashPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scleraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val irisPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val streamPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private class CodeGrain {
        var xRatio: Float = 0f
        var y: Float = 0f
        var speed: Float = 85f
        var glyph: String = "0"
        var alpha: Int = 180
        var isGreenAccent: Boolean = false
    }

    private val codeGrains = mutableListOf<CodeGrain>()

    private val closedLineGlyphs = arrayOf(
        "=", "-", "_", "~", ":", ";", "+", ".", "*", "/"
    )
    private val underEyeGlyphs = arrayOf(
        ".", ":", ";", "-", "_", "~", "=", "+", "*"
    )
    private val openLidGlyphs = arrayOf("=", "-", "_", "/", ":", ";", "+", "#")
    private val lashGlyphs = arrayOf("|", "/", "-", "^", ":", "`", "*")
    private val scleraGlyphs = arrayOf("=", "+", ":", ";", "~", "*", "%", "/", "#")
    private val irisGlyphs = arrayOf("#", "@", "%", "&", "+", "=", "/")
    private val pupilGlyphs = arrayOf("@", "#", "0", "%", "&")
    private val streamGlyphs = arrayOf(
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
        "/", ":", ";", "=", "-", "+", "*", "%", "#"
    )

    private var openAnimator: ValueAnimator? = null
    private var streamAnimator: ValueAnimator? = null
    private var lastFrameTime: Long = 0L

    private val scanDurationSec: Float = 1.6f
    private var scanElapsedSec: Float = scanDurationSec

    init {
        isClickable = true
        isFocusable = true

        shadowPaint.typeface = Typeface.MONOSPACE
        shadowPaint.textAlign = Paint.Align.CENTER
        shadowPaint.color = colorShadowDeep

        lidPaint.typeface = Typeface.MONOSPACE
        lidPaint.textAlign = Paint.Align.CENTER
        lidPaint.color = colorLidWarm

        lashPaint.typeface = Typeface.MONOSPACE
        lashPaint.textAlign = Paint.Align.CENTER
        lashPaint.color = colorShadowMid

        scleraPaint.typeface = Typeface.MONOSPACE
        scleraPaint.textAlign = Paint.Align.CENTER
        scleraPaint.color = colorScleraBright

        irisPaint.typeface = Typeface.MONOSPACE
        irisPaint.textAlign = Paint.Align.CENTER
        irisPaint.color = colorIrisEmerald

        pupilPaint.typeface = Typeface.MONOSPACE
        pupilPaint.textAlign = Paint.Align.CENTER
        pupilPaint.color = colorPupilGreen

        streamPaint.typeface = Typeface.MONOSPACE
        streamPaint.textAlign = Paint.Align.CENTER
        streamPaint.color = colorCodeGray

        initStream()
        startStreamLoop()
    }

    private fun resetGrain(grain: CodeGrain, randomY: Boolean) {
        grain.xRatio = Random.nextFloat() * 1.92f - 0.96f
        grain.y = if (randomY) Random.nextFloat() * 920f else 0f
        grain.speed = Random.nextFloat() * 120f + 70f
        grain.glyph = streamGlyphs[Random.nextInt(streamGlyphs.size)]
        grain.alpha = Random.nextInt(95, 240)
        grain.isGreenAccent = Random.nextFloat() < 0.5f
    }

    private fun initStream() {
        codeGrains.clear()
        for (i in 0 until 180) {
            val g = CodeGrain()
            resetGrain(g, randomY = true)
            codeGrains.add(g)
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

        scanElapsedSec = if (newState == EyeState.CONNECTING) {
            0f
        } else {
            scanDurationSec
        }

        val targetOpen = when (newState) {
            EyeState.CLOSED -> 0f
            EyeState.CONNECTING, EyeState.CONNECTED -> 1f
        }

        openAnimator?.cancel()
        if (animate) {
            openAnimator = ValueAnimator.ofFloat(openProgress, targetOpen)
            openAnimator?.duration = 460L
            openAnimator?.interpolator = OvershootInterpolator(0.55f)
            openAnimator?.addUpdateListener { anim ->
                val raw = anim.animatedValue as Float
                openProgress = raw.coerceIn(-0.08f, 1.12f)
                invalidate()
            }
            openAnimator?.start()
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
        val angle = 2.0 * PI * t.toDouble()
        val wave = (-sin(angle)).toFloat() * (1f - t * 0.35f)
        return (wave * 0.38f).coerceIn(-0.42f, 0.42f)
    }

    private fun startStreamLoop() {
        if (streamAnimator?.isRunning == true) return
        lastFrameTime = System.nanoTime()
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 1000L
        animator.repeatCount = ValueAnimator.INFINITE
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener {
            val now = System.nanoTime()
            val dt = ((now - lastFrameTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastFrameTime = now

            if (currentState == EyeState.CONNECTING && scanElapsedSec < scanDurationSec) {
                scanElapsedSec = (scanElapsedSec + dt).coerceAtMost(scanDurationSec)
            }

            val streamAreaHeight = (height * 0.6f).coerceAtLeast(140f)
            for (grain in codeGrains) {
                grain.y += grain.speed * dt
                if (Random.nextFloat() < 0.16f) {
                    grain.glyph = streamGlyphs[Random.nextInt(streamGlyphs.size)]
                    if (currentState == EyeState.CONNECTING && Random.nextFloat() < 0.3f) {
                        grain.isGreenAccent = Random.nextFloat() < 0.5f
                    }
                }
                if (grain.y > streamAreaHeight) {
                    resetGrain(grain, randomY = false)
                }
            }
            invalidate()
        }
        animator.start()
        streamAnimator = animator
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
        val eyeCenterY = height * 0.36f
        val eyeHalfWidth = width * 0.42f

        val upperFactor = (openProgress * 1.08f).coerceIn(0f, 1.15f)
        val lowerFactor = (openProgress * 0.72f).coerceIn(0f, 0.8f)

        val maxUpperLid = height * 0.16f * upperFactor.coerceAtLeast(0.02f)
        val maxLowerLid = height * 0.11f * lowerFactor.coerceAtLeast(0.015f)

        val pupilNormOffsetX = computeScanOffsetX()

        val charSize = (width * 0.021f).coerceIn(12.5f, 22f)
        shadowPaint.textSize = charSize * 0.9f
        lidPaint.textSize = charSize
        lashPaint.textSize = charSize * 0.85f
        scleraPaint.textSize = charSize * 0.96f
        irisPaint.textSize = charSize * 1.04f
        pupilPaint.textSize = charSize * 1.15f
        streamPaint.textSize = charSize * 0.88f

        val eyeVisibility = ((openProgress + 0.08f) / 1.08f).coerceIn(0f, 1f)
        val maxStreamDrop = (height * 0.48f).coerceAtLeast(140f)

        // 1. Поток кода под нижней округлостью глаза:
        // CLOSED -> серый; CONNECTING -> зелёный вперемешку с серым; CONNECTED -> зелёный
        for (grain in codeGrains) {
            if (grain.y > maxStreamDrop) continue
            val gx = centerX + grain.xRatio * (width * 0.46f)
            val eyeColNorm = ((gx - centerX) / eyeHalfWidth).coerceIn(-1f, 1f)
            val lowerCurveFactor = (1f - abs(eyeColNorm).pow(1.65f)).coerceAtLeast(0f)
            val lowerContourY = eyeCenterY + maxLowerLid * lowerCurveFactor
            val gy = lowerContourY + charSize * 1.15f + grain.y
            if (gy > height - charSize * 0.3f) continue

            val fade = (1f - (grain.y / maxStreamDrop) * 0.65f).coerceIn(0.25f, 1f)
            streamPaint.color = when (currentState) {
                EyeState.CLOSED -> colorCodeGray
                EyeState.CONNECTING -> if (grain.isGreenAccent) colorCodeGreen else colorCodeGray
                EyeState.CONNECTED -> colorCodeGreen
            }
            streamPaint.alpha = (grain.alpha * fade).toInt().coerceIn(30, 255)
            canvas.drawText(grain.glyph, gx, gy, streamPaint)
        }

        val cols = 39
        val rows = 23

        // 2. Плотная строчная текстура под глазом (тени и линии как на референсе)
        for (c in 0 until cols) {
            val colNorm = (c / (cols - 1f)) * 2f - 1f
            val arch = (1f - abs(colNorm).pow(1.65f)).coerceAtLeast(0f)
            val x = centerX + colNorm * eyeHalfWidth
            val lowerEdgeY = eyeCenterY + maxLowerLid * arch

            if (arch > 0.08f) {
                for (rowIdx in 1..3) {
                    val subY = lowerEdgeY + rowIdx * charSize * 0.48f
                    val rowFade = (1f - (rowIdx - 1) * 0.28f) * arch
                    shadowPaint.color = if (rowIdx == 1) colorUnderEyeShadow else colorShadowDeep
                    shadowPaint.alpha = (165 * rowFade).toInt().coerceIn(35, 185)
                    val gIdx = (c * 3 + rowIdx * 7) % underEyeGlyphs.size
                    canvas.drawText(underEyeGlyphs[gIdx], x, subY, shadowPaint)
                }
            }
        }

        // 3. Детализированный закрытый глаз (без брови) в плотной строчной манере
        if (eyeVisibility < 0.25f) {
            val closedWeight = (1f - eyeVisibility / 0.25f).coerceIn(0f, 1f)
            for (c in 0 until cols) {
                val colNorm = (c / (cols - 1f)) * 2f - 1f
                val arch = (1f - abs(colNorm).pow(1.75f)).coerceAtLeast(0f)
                val x = centerX + colNorm * eyeHalfWidth
                val lidLineY = eyeCenterY - arch * (charSize * 0.42f) + (colNorm * colNorm * charSize * 0.22f)

                if (arch > 0.08f) {
                    // Верхняя складка века и слои закрытого века
                    for (layer in 1..3) {
                        val upY = lidLineY - layer * charSize * 0.48f
                        if (layer == 3) {
                            shadowPaint.color = colorShadowDeep
                            shadowPaint.alpha = (160 * closedWeight * arch).toInt().coerceIn(35, 185)
                            val gIdx = (c * 5 + layer) % closedLineGlyphs.size
                            canvas.drawText(closedLineGlyphs[gIdx], x, upY, shadowPaint)
                        } else {
                            val isHighlight = layer == 1 && abs(colNorm) < 0.35f
                            lidPaint.color = if (isHighlight) colorHighlightWarm else colorLidWarm
                            lidPaint.alpha = ((215 - layer * 40) * closedWeight * arch).toInt().coerceIn(45, 230)
                            val gIdx = (c * 3 + layer * 4) % closedLineGlyphs.size
                            canvas.drawText(closedLineGlyphs[gIdx], x, upY, lidPaint)
                        }
                    }

                    // Тень под сомкнутым веком
                    val underShadowY = lidLineY + charSize * 0.44f
                    shadowPaint.color = colorShadowMid
                    shadowPaint.alpha = (165 * closedWeight * arch).toInt().coerceIn(40, 185)
                    canvas.drawText(closedLineGlyphs[(c * 5 + 2) % closedLineGlyphs.size], x, underShadowY, shadowPaint)
                }

                lidPaint.color = when {
                    abs(colNorm) > 0.92f -> colorShadowDeep
                    abs(colNorm) < 0.28f -> colorHighlightWarm
                    else -> colorLidWarm
                }
                lidPaint.alpha = (245 * closedWeight).toInt().coerceIn(70, 255)
                val closedGlyph = when {
                    abs(colNorm) > 0.94f && colNorm < 0f -> "<"
                    abs(colNorm) > 0.94f -> ">"
                    arch > 0.55f -> closedLineGlyphs[c % closedLineGlyphs.size]
                    else -> "-"
                }
                canvas.drawText(closedGlyph, x, lidLineY, lidPaint)

                if (arch > 0.12f) {
                    val lashX = x + colNorm * charSize * 0.36f
                    val lashY = lidLineY + charSize * (0.88f + arch * 0.4f)
                    lashPaint.color = if (c % 3 == 0) colorShadowDeep else colorShadowMid
                    lashPaint.alpha = (195 * closedWeight * arch).toInt().coerceIn(55, 230)
                    val lashGlyph = if (colNorm > 0.24f) "/" else "|"
                    canvas.drawText(lashGlyph, lashX, lashY, lashPaint)
                }
            }
        }

        // 4. Открытый глаз: веки, ресницы, тёплая склера с бликами, зелёная радужка и зелёный зрачок
        val isActiveEye = currentState == EyeState.CONNECTING || currentState == EyeState.CONNECTED

        for (r in -rows / 2..rows / 2) {
            val rowNorm = r / (rows / 2f)
            for (c in -cols / 2..cols / 2) {
                val colNorm = c / (cols / 2f)
                val almondFactor = (1f - abs(colNorm).pow(1.65f)).coerceAtLeast(0f)
                if (almondFactor <= 0.02f && abs(colNorm) < 0.96f) continue

                val x = centerX + colNorm * eyeHalfWidth
                val y = if (rowNorm < 0f) {
                    eyeCenterY + rowNorm * maxUpperLid * almondFactor
                } else {
                    eyeCenterY + rowNorm * maxLowerLid * almondFactor
                }

                val isUpperBorder = r == -rows / 2 && almondFactor > 0.1f
                val isLowerBorder = r == rows / 2 && almondFactor > 0.15f
                if (isUpperBorder && eyeVisibility > 0.12f) {
                    val lashSlope = -colNorm * 0.5f
                    val lashX = x + lashSlope * charSize * 0.55f
                    val lashY = y - charSize * (0.72f + 0.42f * almondFactor)
                    lashPaint.color = if (c % 2 == 0) colorShadowMid else colorScleraBright
                    lashPaint.alpha = (165 + 90 * eyeVisibility).toInt().coerceIn(100, 255)
                    val lashGlyph = if (colNorm > 0.2f) "/" else "|"
                    canvas.drawText(lashGlyph, lashX, lashY, lashPaint)
                } else if (isLowerBorder && eyeVisibility > 0.35f) {
                    val lashY = y + charSize * 0.64f
                    lashPaint.color = colorShadowMid
                    lashPaint.alpha = (150 * eyeVisibility).toInt().coerceIn(70, 190)
                    canvas.drawText(lashGlyphs[abs(c) % lashGlyphs.size], x, lashY, lashPaint)
                }

                val onLidEdge = eyeVisibility >= 0.2f && abs(abs(rowNorm) - 1f) < 0.15f
                if (onLidEdge) {
                    lidPaint.color = if (rowNorm < 0f) colorLidWarm else colorShadowMid
                    lidPaint.alpha = 245
                    val ch = openLidGlyphs[(abs(r * 5 + c * 11)) % openLidGlyphs.size]
                    canvas.drawText(ch, x, y, lidPaint)
                    continue
                }

                if (eyeVisibility > 0.22f && abs(rowNorm) < 0.94f) {
                    val verticalScale = if (rowNorm < 0f) maxUpperLid else maxLowerLid
                    val vNorm = if (verticalScale > 1f) {
                        abs(y - eyeCenterY) / verticalScale
                    } else {
                        1f
                    }
                    val dx = (colNorm - pupilNormOffsetX) * 1.15
                    val dy = vNorm * 1.15
                    val irisDist = hypot(dx, dy)

                    if (irisDist < 0.17) {
                        pupilPaint.color = colorPupilGreen
                        pupilPaint.alpha = 255
                        val pGlyph = pupilGlyphs[(abs(r * 3 + c * 7)) % pupilGlyphs.size]
                        canvas.drawText(pGlyph, x, y, pupilPaint)
                    } else if (irisDist < 0.38) {
                        val pulse = ((sin(System.nanoTime() / 2.2e8) + 1.0) * 0.5).toFloat()
                        irisPaint.color = if (isActiveEye) colorIrisGlow else colorIrisEmerald
                        irisPaint.alpha = (200 + pulse * 55).toInt().coerceIn(175, 255)
                        val iGlyph = irisGlyphs[(abs(r * 11 + c * 3)) % irisGlyphs.size]
                        canvas.drawText(iGlyph, x, y, irisPaint)
                    } else {
                        val minX = pupilNormOffsetX - 0.19f
                        val maxX = pupilNormOffsetX - 0.04f
                        val inHighlightX = colNorm >= minX && colNorm <= maxX
                        val inHighlightY = rowNorm >= -0.58f && rowNorm <= -0.18f
                        if (inHighlightX && inHighlightY) {
                            scleraPaint.color = colorHighlightWarm
                            scleraPaint.alpha = 255
                            canvas.drawText("#", x, y, scleraPaint)
                        } else {
                            val edgeShadow = abs(colNorm) > 0.68f || abs(rowNorm) > 0.72f
                            scleraPaint.color = if (edgeShadow) colorShadowMid else colorScleraBright
                            scleraPaint.alpha = (220 * eyeVisibility).toInt().coerceIn(110, 250)
                            val sGlyph = scleraGlyphs[(abs(r * 7 + c * 5)) % scleraGlyphs.size]
                            canvas.drawText(sGlyph, x, y, scleraPaint)
                        }
                    }
                }
            }
        }
    }
}
