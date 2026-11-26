    private val telegramMask = arrayOf(
        "...................#",
        ".................###",
        "...............#####",
        ".............#######",
        "...........#########",
        ".........###########",
        ".......#############",
        ".....#######..######",
        "...#######...#######",
        ".#######....########",
        "#####......#########",
        "..###.....##########",
        "....#....###########",
        "........############",
        ".......#############",
        "......##############",
        "......#############.",
        ".......###########..",
        "........#########...",
        ".........######....."
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
        streamPaint.color = colorLidWarm

        telegramPaint.typeface = Typeface.MONOSPACE
        telegramPaint.textAlign = Paint.Align.CENTER
        telegramPaint.color = colorTgGrayMain

        initStream()
        startStreamLoop()
    }

    fun setTelegramActive(active: Boolean) {
        if (isTelegramActive == active) return
        isTelegramActive = active
        invalidate()
    }

    fun isTelegramActive(): Boolean = isTelegramActive

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hitDown = !telegramBounds.isEmpty && telegramBounds.contains(event.x, event.y)
                if (hitDown) {
                    touchDownOnTelegram = true
                    return true
                }
                touchDownOnTelegram = false
            }
            MotionEvent.ACTION_UP -> {
                if (touchDownOnTelegram) {
                    touchDownOnTelegram = false
                    val hitUp = !telegramBounds.isEmpty && telegramBounds.contains(event.x, event.y)
                    if (hitUp) {
                        isTelegramActive = !isTelegramActive
                        invalidate()
                        performClick()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                touchDownOnTelegram = false
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun pickEyeColorIndex(): Int {
        val roll = Random.nextFloat()
        return when (currentState) {
            EyeState.CLOSED -> when {
                roll < 0.24f -> 0
                roll < 0.44f -> 1
                roll < 0.54f -> 2
                roll < 0.72f -> 3
                roll < 0.88f -> 4
                roll < 0.94f -> 5
                roll < 0.98f -> 6
                else -> 7
            }
            EyeState.CONNECTING -> when {
                roll < 0.16f -> 0
                roll < 0.30f -> 1
                roll < 0.38f -> 2
                roll < 0.48f -> 3
                roll < 0.56f -> 4
                roll < 0.74f -> 5
                roll < 0.88f -> 6
                else -> 7
            }
            EyeState.CONNECTED -> when {
                roll < 0.12f -> 0
                roll < 0.22f -> 1
                roll < 0.28f -> 2
                roll < 0.35f -> 3
                roll < 0.41f -> 4
                roll < 0.65f -> 5
                roll < 0.84f -> 6
                else -> 7
            }
        }
    }

    private fun resetGrain(grain: CodeGrain, randomY: Boolean) {
        grain.xRatio = Random.nextFloat() * 1.92f - 0.96f
        grain.y = if (randomY) Random.nextFloat() * 920f else 0f
        grain.speed = Random.nextFloat() * 120f + 70f
        grain.glyph = streamGlyphs[Random.nextInt(streamGlyphs.size)]
        grain.alpha = Random.nextInt(105, 245)
        grain.colorIndex = pickEyeColorIndex()
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

        for (grain in codeGrains) {
            if (Random.nextFloat() < 0.65f) {
                grain.colorIndex = pickEyeColorIndex()
            }
        }

        openAnimator?.cancel()
        if (animate) {
            val isClosing = targetOpen < openProgress
            openAnimator = ValueAnimator.ofFloat(openProgress, targetOpen)
            openAnimator?.duration = if (isClosing) 340L else 460L
            openAnimator?.interpolator = if (isClosing) {
                AccelerateDecelerateInterpolator()
            } else {
                OvershootInterpolator(0.55f)
            }
            openAnimator?.addUpdateListener { anim ->
                val raw = anim.animatedValue as Float
                openProgress = if (isClosing) {
                    raw.coerceIn(0f, 1f)
                } else {
                    raw.coerceIn(-0.08f, 1.12f)
                }
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

            val streamAreaHeight = (height * 0.65f).coerceAtLeast(160f)
            for (grain in codeGrains) {
                grain.y += grain.speed * dt
                if (Random.nextFloat() < 0.16f) {
                    grain.glyph = streamGlyphs[Random.nextInt(streamGlyphs.size)]
                    if (Random.nextFloat() < 0.25f) {
                        grain.colorIndex = pickEyeColorIndex()
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

    private fun deflectAroundTelegram(x: Float, y: Float, margin: Float): Float {
        if (telegramBounds.isEmpty) return x
        val leftBound = telegramBounds.left - margin
        val topBound = telegramBounds.top - margin * 0.65f
        val rightBound = telegramBounds.right + margin
        val bottomBound = telegramBounds.bottom + margin * 0.65f

        val insideX = x >= leftBound && x <= rightBound
        val insideY = y >= topBound && y <= bottomBound
        if (!insideX || !insideY) return x

        val tgCenterX = telegramBounds.centerX()
        val pad = margin * 0.35f
        return if (x <= tgCenterX) {
            leftBound - pad
        } else {
            rightBound + pad
        }
    }

    private fun updateTelegramBounds(centerX: Float, tgCenterY: Float, charSize: Float) {
        val tgCharSize = (charSize * 0.86f).coerceIn(11f, 20f)
        val stepX = tgCharSize * 0.72f
        val stepY = tgCharSize * 1.05f
        val tgHalfW = (telegramMask[0].length - 1) * stepX * 0.5f + tgCharSize * 0.8f
        val tgHalfH = (telegramMask.size - 1) * stepY * 0.5f + tgCharSize * 0.7f
        telegramBounds.set(
            centerX - tgHalfW,
            tgCenterY - tgHalfH,
            centerX + tgHalfW,
            tgCenterY + tgHalfH
        )
    }

    private fun drawNumericTelegramIcon(canvas: Canvas, centerX: Float, centerY: Float, charSize: Float) {
        val tgCharSize = (charSize * 0.86f).coerceIn(11f, 20f)
        telegramPaint.textSize = tgCharSize

        val stepX = tgCharSize * 0.72f
        val stepY = tgCharSize * 1.05f
        val numRows = telegramMask.size
        val numCols = telegramMask[0].length

        val totalWidth = (numCols - 1) * stepX
        val totalHeight = (numRows - 1) * stepY
        val startX = centerX - totalWidth * 0.5f
        val startY = centerY - totalHeight * 0.5f

        val mainColor = if (isTelegramActive) colorTgBlueMain else colorTgGrayMain
        val darkColor = if (isTelegramActive) colorTgBlueDark else colorTgGrayDark
        val activeShift = if (isTelegramActive) 2 else 0

        for (r in 0 until numRows) {
            val rowStr = telegramMask[r]
            val py = startY + r * stepY
            for (c in 0 until numCols) {
                if (rowStr[c] != '#') continue
                val px = startX + c * stepX

                val isLowerWing = r >= 11 && c <= 12
                val isCrease = r in 10..15 && c in 7..10
                val isShadowFold = isLowerWing || isCrease

                telegramPaint.color = if (isShadowFold) darkColor else mainColor
                telegramPaint.alpha = if (isShadowFold) 215 else 255

                val digitIdx = (r * 7 + c * 3 + activeShift) % numericGlyphs.size
                canvas.drawText(numericGlyphs[digitIdx], px, py, telegramPaint)
            }
        }
    }
    
