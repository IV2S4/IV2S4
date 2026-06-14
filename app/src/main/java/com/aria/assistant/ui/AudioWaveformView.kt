package com.aria.assistant.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class AudioWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isActive = false
    private val bars = 32
    private val barAmplitudes = FloatArray(bars) { 0f }
    private val barTargets = FloatArray(bars) { 0f }
    private var tick = 0

    private val paintBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0A2A44")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val animRunnable = object : Runnable {
        override fun run() {
            tick++
            if (isActive) {
                if (tick % 3 == 0) {
                    for (i in barTargets.indices) {
                        barTargets[i] = Random.nextFloat() * 0.9f + 0.1f
                    }
                }
                for (i in barAmplitudes.indices) {
                    val diff = barTargets[i] - barAmplitudes[i]
                    barAmplitudes[i] += diff * 0.3f
                }
            } else {
                for (i in barAmplitudes.indices) {
                    barAmplitudes[i] *= 0.85f
                    if (barAmplitudes[i] < 0.02f) barAmplitudes[i] = 0.02f
                }
            }
            invalidate()
            postDelayed(this, 40)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(animRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(animRunnable)
    }

    fun setActive(active: Boolean) {
        isActive = active
    }

    fun updateAmplitude(amplitude: Float) {
        if (isActive) {
            for (i in barTargets.indices) {
                val wave = sin(i * 0.4 + tick * 0.2) * 0.3 + 0.7
                barTargets[i] = (amplitude * wave).toFloat().coerceIn(0.05f, 1f)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val barW = (w / bars) * 0.6f
        val spacing = w / bars

        // Center line
        canvas.drawLine(0f, h / 2f, w, h / 2f, paintLine)

        for (i in 0 until bars) {
            val x = i * spacing + spacing / 2f
            val amp = barAmplitudes[i]
            val barH = amp * h * 0.9f

            val distFromCenter = abs(x - cx) / cx
            val alpha = ((1f - distFromCenter * 0.5f) * 255).toInt()

            val color = when {
                isActive -> interpolateColor(
                    Color.parseColor("#0066FF"),
                    Color.parseColor("#00F5FF"),
                    amp
                )
                else -> Color.parseColor("#1A4A6E")
            }

            paintBar.color = color
            paintBar.alpha = alpha

            canvas.drawRoundRect(
                x - barW / 2f,
                h / 2f - barH / 2f,
                x + barW / 2f,
                h / 2f + barH / 2f,
                2f, 2f,
                paintBar
            )
        }
    }

    private fun interpolateColor(c1: Int, c2: Int, t: Float): Int {
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t).toInt()
        return Color.rgb(r, g, b)
    }
}
