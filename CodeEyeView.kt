package io.github.romanvht.byedpi

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
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

    private val colorTgGrayMain = Color.parseColor("#90A4AE")
    private val colorTgGrayDark = Color.parseColor("#546E7A")
    private val colorTgBlueMain = Color.parseColor("#29B6F6")
    private val colorTgBlueDark = Color.parseColor("#0288D1")

    private val eyePaletteColors = intArrayOf(
        colorLidWarm,
        colorScleraBright,
        colorHighlightWarm,
        colorShadowMid,
        colorUnderEyeShadow,
        colorIrisEmerald,
        colorIrisGlow,
        colorPupilGreen
    )

    private var currentState: EyeState = EyeState.CLOSED
    private var openProgress: Float = 0f
    private var isTelegramActive: Boolean = false
    private val telegramBounds = RectF()
    private var touchDownOnTelegram: Boolean = false

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lashPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scleraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val irisPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val streamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val telegramPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private class CodeGrain {
        var xRatio: Float = 0f
        var y: Float = 0f
        var speed: Float = 85f
        var glyph: String = "0"
        var alpha: Int = 180
        var colorIndex: Int = 0
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
    private val numericGlyphs = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

    private val telegramMask = arrayOf(
        "..........................##",
        ".......................#####",
        "....................########",
        ".................###########",
        "..............##############",
        "...........#################",
        "........####################",
        ".....##########...##########",
        "..############.....#########",
        "#############.......########",
        ".##########..........#######",
        "....######............######",
        ".......###.............#####",
        "..........#.............####",
        "........................####",
        ".........................###",
        "..........................##"
    )

    private var openAnimator: ValueAnimator? = null
    private var streamAnimator
    
    private fun drawCodeStream(
        canvas: Canvas,
        centerX: Float,
        eyeCenterY: Float,
        eyeHalfWidth: Float,
        maxLowerLid: Float,
        charSize: Float,
        maxStreamDrop: Float
    ) {
        val deflectMargin = charSize * 2.2f
        val maxGy = height - charSize * 0.3f

        for (grain in codeGrains) {
            if (grain.y > maxStreamDrop) continue
            val rawX = centerX + grain.xRatio * (width * 0.46f)
            val eyeColNorm = ((rawX - centerX) / eyeHalfWidth).coerceIn(-1f, 1f)
            val lowerCurveFactor = (1f - abs(eyeColNorm).pow(1.65f)).coerceAtLeast(0f)
            val lowerContourY = eyeCenterY + maxLowerLid * lowerCurveFactor
            val gy = lowerContourY + charSize * 1.15f + grain.y
            if (gy > maxGy) continue

            val gx = deflectAroundTelegram(rawX, gy, deflectMargin)
            val fade = (1f - (grain.y / maxStreamDrop) * 0.6f).coerceIn(0.3f, 1f)
            val safeIdx = grain.colorIndex.coerceIn(0, eyePaletteColors.size - 1)
            val alphaFloat = grain.alpha * fade
            streamPaint.color = eyePaletteColors[safeIdx]
            streamPaint.alpha = alphaFloat.toInt().coerceIn(35, 255)
            canvas.drawText(grain.glyph, gx, gy, streamPaint)
        }
    }

    private fun drawUnderEyeShadows(
        canvas: Canvas,
        centerX: Float,
        eyeCenterY: Float,
        eyeHalfWidth: Float,
        maxLowerLid: Float,
        charSize: Float,
        cols: Int
    ) {
        for (c in 0 until cols) {
            val colNorm = (c / (cols - 1f)) * 2f - 1f
            val arch = (1f - abs(colNorm).pow(1.65f)).coerceAtLeast(0f)
            if (arch <= 0.08f) continue

            val x = centerX + colNorm * eyeHalfWidth
            val lowerEdgeY = eyeCenterY + maxLowerLid * arch

            for (rowIdx in 1..3) {
                val subY = lowerEdgeY + rowIdx * charSize * 0.48f
                val rowFade = (1f - (rowIdx - 1) * 0.28f) * arch
                shadowPaint.color = if (rowIdx == 1) colorUnderEyeShadow else colorShadowDeep
                shadowPaint.alpha = (165f * rowFade).toInt().coerceIn(35, 185)
                val gIdx = (c * 3 + rowIdx * 7) % underEyeGlyphs.size
                canvas.drawText(underEyeGlyphs[gIdx], x, subY, shadowPaint)
            }
        }
    }

    private fun drawClosedEye(
        canvas: Canvas,
        centerX: Float,
        eyeCenterY: Float,
        eyeHalfWidth: Float,
        charSize: Float,
        closedWeight: Float,
        cols: Int
    ) {
        for (c in 0 until cols) {
            val colNorm = (c / (cols - 1f)) * 2f - 1f
            val arch = (1f - abs(colNorm).pow(1.75f)).coerceAtLeast(0f)
            val x = centerX + colNorm * eyeHalfWidth
            val lidLineY = eyeCenterY - arch * (charSize * 0.42f) + (colNorm * colNorm * charSize * 0.22f)

            if (arch > 0.08f) {
                for (layer in 1..3) {
                    val upY = lidLineY - layer * charSize * 0.48f
                    if (layer == 3) {
                        shadowPaint.color = colorShadowDeep
                        shadowPaint.alpha = (160f * closedWeight * arch).toInt().coerceIn(35, 185)
                        val gIdx = (c * 5 + layer) % closedLineGlyphs.size
                        canvas.drawText(closedLineGlyphs[gIdx], x, upY, shadowPaint)
                    } else {
                        val isHighlight = layer == 1 && abs(colNorm) < 0.35f
                        lidPaint.color = if (isHighlight) colorHighlightWarm else colorLidWarm
                        val layerAlpha = (215f - layer * 40f) * closedWeight * arch
                        lidPaint.alpha = layerAlpha.toInt().coerceIn(45, 230)
                        val gIdx = (c * 3 + layer * 4) % closedLineGlyphs.size
                        canvas.drawText(closedLineGlyphs[gIdx], x, upY, lidPaint)
                    }
                }

                val underShadowY = lidLineY + charSize * 0.44f
                shadowPaint.color = colorShadowMid
                shadowPaint.alpha = (165f * closedWeight * arch).toInt().coerceIn(40, 185)
                val underIdx = (c * 5 + 2) % closedLineGlyphs.size
                canvas.drawText(closedLineGlyphs[underIdx], x, underShadowY, shadowPaint)
            }

            lidPaint.color = when {
                abs(colNorm) > 0.92f -> colorShadowDeep
                abs(colNorm) < 0.28f -> colorHighlightWarm
                else -> colorLidWarm
            }
            lidPaint.alpha = (245f * closedWeight).toInt().coerceIn(70, 255)
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
                lashPaint.alpha = (195f * closedWeight * arch).toInt().coerceIn(55, 230)
                val lashGlyph = if (colNorm > 0.24f) "/" else "|"
                canvas.drawText(lashGlyph, lashX, lashY, lashPaint)
            }
        }
    }

    private fun drawOpenEye(
        canvas: Canvas,
        centerX: Float,
        eyeCenterY: Float,
        eyeHalfWidth: Float,
        maxUpperLid: Float,
        maxLowerLid: Float,
        pupilNormOffsetX: Float,
        charSize: Float,
        eyeVisibility: Float,
        cols: Int,
        rows: Int
    ) {
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
                    lashPaint.alpha = (165f + 90f * eyeVisibility).toInt().coerceIn(100, 255)
                    val lashGlyph = if (colNorm > 0.2f) "/" else "|"
                    canvas.drawText(lashGlyph, lashX, lashY, lashPaint)
                } else if (isLowerBorder && eyeVisibility > 0.35f) {
                    val lashY = y + charSize * 0.64f
                    lashPaint.color = colorShadowMid
                    lashPaint.alpha = (150f * eyeVisibility).toInt().coerceIn(70, 190)
                    canvas.drawText(lashGlyphs[abs(c) % lashGlyphs.size], x, lashY, lashPaint)
                }

                val onLidEdge = eyeVisibility >= 0.2f && abs(abs(rowNorm) - 1f) < 0.15f
                if (onLidEdge) {
                    lidPaint.color = if (rowNorm < 0f) colorLidWarm else colorShadowMid
                    lidPaint.alpha = 245
                    val ch
