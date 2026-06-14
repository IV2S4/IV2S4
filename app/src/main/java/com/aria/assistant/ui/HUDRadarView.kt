package com.aria.assistant.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class HUDRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State { IDLE, LISTENING, PROCESSING, SPEAKING }

    private var state = State.IDLE
    private var animAngle = 0f
    private var pulseRadius = 0f
    private var pulseAlpha = 255
    private var breathScale = 1f
    private var breathDir = 1f
    private var particleAngle = 0f

    private val paintCyan = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00F5FF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val paintCyanFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00F5FF")
        style = Paint.Style.FILL
    }
    private val paintBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0066FF")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val paintOrange = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B00")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00F5FF")
        textSize = 18f
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val paintTextSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A6080")
        textSize = 12f
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val paintGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }
    private val paintSweep = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val animRunnable = object : Runnable {
        override fun run() {
            animAngle = (animAngle + getRotationSpeed()) % 360f
            breathScale += breathDir * 0.003f
            if (breathScale > 1.04f) breathDir = -1f
            if (breathScale < 0.96f) breathDir = 1f
            particleAngle = (particleAngle + 0.8f) % 360f

            if (state == State.LISTENING || state == State.PROCESSING) {
                pulseRadius += 3f
                pulseAlpha = maxOf(0, pulseAlpha - 5)
                if (pulseAlpha <= 0) {
                    pulseRadius = 0f
                    pulseAlpha = 220
                }
            }
            invalidate()
            postDelayed(this, 16)
        }
    }

    private fun getRotationSpeed() = when (state) {
        State.IDLE -> 0.3f
        State.LISTENING -> 2.5f
        State.PROCESSING -> 4f
        State.SPEAKING -> 1.5f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(animRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(animRunnable)
    }

    fun setState(newState: State) {
        state = newState
        pulseRadius = 0f
        pulseAlpha = 220
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 16f

        drawBackground(canvas, cx, cy, r)
        drawGrid(canvas, cx, cy, r)
        drawRings(canvas, cx, cy, r)
        drawSweep(canvas, cx, cy, r)
        drawPulse(canvas, cx, cy)
        drawCrossHairs(canvas, cx, cy, r)
        drawCornerMarkers(canvas, cx, cy, r)
        drawCenterCore(canvas, cx, cy)
        drawDataOverlay(canvas, cx, cy, r)
    }

    private fun drawBackground(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, r,
                intArrayOf(
                    Color.parseColor("#0A1628"),
                    Color.parseColor("#050D1A"),
                    Color.parseColor("#030A14")
                ),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, r, bgPaint)
    }

    private fun drawGrid(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A2A44")
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }
        for (i in 1..6) {
            canvas.drawLine(
                cx - r * i / 6, cy - r, cx - r * i / 6, cy + r, gridPaint
            )
            canvas.drawLine(
                cx + r * i / 6, cy - r, cx + r * i / 6, cy + r, gridPaint
            )
            canvas.drawLine(
                cx - r, cy - r * i / 6, cx + r, cy - r * i / 6, gridPaint
            )
            canvas.drawLine(
                cx - r, cy + r * i / 6, cx + r, cy + r * i / 6, gridPaint
            )
        }
        // Clip to circle
        val clipPath = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        canvas.clipPath(clipPath)
    }

    private fun drawRings(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val rings = floatArrayOf(0.25f, 0.5f, 0.75f, 1f)
        rings.forEachIndexed { i, ratio ->
            paintCyan.alpha = if (i == rings.size - 1) 200 else 80
            paintCyan.strokeWidth = if (i == rings.size - 1) 2f else 1f
            canvas.drawCircle(cx, cy, r * ratio * breathScale, paintCyan)
        }
        paintCyan.alpha = 255
        paintCyan.strokeWidth = 1.5f
    }

    private fun drawSweep(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val sweepColor = when (state) {
            State.IDLE -> Color.parseColor("#00F5FF")
            State.LISTENING -> Color.parseColor("#00FF88")
            State.PROCESSING -> Color.parseColor("#FF6B00")
            State.SPEAKING -> Color.parseColor("#0066FF")
        }

        val sweepShader = SweepGradient(
            cx, cy,
            intArrayOf(Color.TRANSPARENT, sweepColor and 0x33FFFFFF, sweepColor and 0x88FFFFFF.toInt(), Color.TRANSPARENT),
            floatArrayOf(0f, 0.2f, 0.5f, 1f)
        )
        paintSweep.shader = sweepShader

        canvas.save()
        canvas.rotate(animAngle, cx, cy)
        canvas.drawCircle(cx, cy, r * 0.98f, paintSweep)
        canvas.restore()

        // Sweep arm
        val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = sweepColor
            style = Paint.Style.STROKE
            strokeWidth = 2f
            alpha = 200
        }
        val rad = Math.toRadians(animAngle.toDouble())
        canvas.drawLine(
            cx, cy,
            cx + (r * 0.95f * cos(rad)).toFloat(),
            cy + (r * 0.95f * sin(rad)).toFloat(),
            sweepPaint
        )
    }

    private fun drawPulse(canvas: Canvas, cx: Float, cy: Float) {
        if (state == State.LISTENING || state == State.PROCESSING) {
            val pulseColor = if (state == State.LISTENING)
                Color.parseColor("#00FF88") else Color.parseColor("#FF6B00")
            val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pulseColor
                style = Paint.Style.STROKE
                strokeWidth = 2f
                alpha = pulseAlpha
            }
            canvas.drawCircle(cx, cy, pulseRadius, pulsePaint)
        }
    }

    private fun drawCrossHairs(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A4A6E")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawLine(cx - r, cy, cx + r, cy, crossPaint)
        canvas.drawLine(cx, cy - r, cx, cy + r, crossPaint)

        // Angle markers
        val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A2A44")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        for (angle in 0 until 360 step 30) {
            val rad = Math.toRadians(angle.toDouble())
            val inner = r * 0.9f
            val outer = r * 1.0f
            canvas.drawLine(
                cx + (inner * cos(rad)).toFloat(),
                cy + (inner * sin(rad)).toFloat(),
                cx + (outer * cos(rad)).toFloat(),
                cy + (outer * sin(rad)).toFloat(),
                markerPaint
            )
        }
    }

    private fun drawCornerMarkers(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00F5FF")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val angles = floatArrayOf(45f, 135f, 225f, 315f)
        angles.forEach { angle ->
            val rad = Math.toRadians(angle.toDouble())
            val dotX = cx + (r * 0.88f * cos(rad)).toFloat()
            val dotY = cy + (r * 0.88f * sin(rad)).toFloat()
            canvas.drawCircle(dotX, dotY, 3f, paintCyanFill)
        }
    }

    private fun drawCenterCore(canvas: Canvas, cx: Float, cy: Float) {
        // Glow effect
        paintGlow.color = when (state) {
            State.IDLE -> Color.parseColor("#00F5FF")
            State.LISTENING -> Color.parseColor("#00FF88")
            State.PROCESSING -> Color.parseColor("#FF6B00")
            State.SPEAKING -> Color.parseColor("#0066FF")
        }
        paintGlow.alpha = 150
        canvas.drawCircle(cx, cy, 28f, paintGlow)

        // Core circle
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, 24f,
                intArrayOf(
                    Color.parseColor("#AAFFFFFF"),
                    paintGlow.color,
                    Color.parseColor("#00000000")
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, 24f, corePaint)

        // Center dot
        canvas.drawCircle(cx, cy, 4f, paintCyanFill)

        // State label
        val stateText = when (state) {
            State.IDLE -> "ARIA"
            State.LISTENING -> "..."
            State.PROCESSING -> "AI"
            State.SPEAKING -> "▶"
        }
        paintText.textSize = 14f
        paintText.color = Color.parseColor("#030A14")
        canvas.drawText(stateText, cx, cy + 5f, paintText)
    }

    private fun drawDataOverlay(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // Rotating small data indicators around the ring
        for (i in 0 until 8) {
            val angle = particleAngle + i * 45f
            val rad = Math.toRadians(angle.toDouble())
            val dotR = r * 0.5f
            val x = cx + (dotR * cos(rad)).toFloat()
            val y = cy + (dotR * sin(rad)).toFloat()

            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00F5FF")
                alpha = (sin(Math.toRadians((angle + particleAngle).toDouble())) * 127 + 128).toInt()
            }
            canvas.drawCircle(x, y, 2f, dotPaint)
        }

        // HUD text data
        paintTextSmall.color = Color.parseColor("#1A4A6E")
        paintTextSmall.textSize = 9f
        canvas.drawText("SYS: OK", cx - r * 0.5f, cy + r * 0.85f, paintTextSmall)
        canvas.drawText("NET: ON", cx + r * 0.5f, cy + r * 0.85f, paintTextSmall)
        canvas.drawText(String.format("%.0f°", animAngle), cx, cy - r * 0.82f, paintTextSmall)
    }
}
