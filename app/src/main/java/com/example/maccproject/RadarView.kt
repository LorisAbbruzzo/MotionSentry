package com.example.maccproject

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin


class RadarView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    // Paint for the green circles
    private val circlePaint = Paint().apply {
        color = Color.parseColor("#00FF00") // Hacker Green
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    // Paint for the rotating sweep line
    private val linePaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        strokeWidth = 8f
        isAntiAlias = true
        // Add a "glow" effect
        setShadowLayer(10f, 0f, 0f, Color.GREEN)
    }

    private var rotationAngle = 0f
    private var isAlarming = false

    init {
        // Create an infinite animation loop from 0 to 360 degrees
        val animator = ValueAnimator.ofFloat(0f, 360f)
        animator.duration = 1500 // 1.5 seconds per full rotation
        animator.repeatCount = ValueAnimator.INFINITE
        animator.interpolator = LinearInterpolator()

        animator.addUpdateListener { animation ->
            rotationAngle = animation.animatedValue as Float

            invalidate()
        }
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (Math.min(cx, cy)) - 20f

        // 1. Draw Static Concentric Circles
        canvas.drawCircle(cx, cy, radius, circlePaint)
        canvas.drawCircle(cx, cy, radius * 0.75f, circlePaint)
        canvas.drawCircle(cx, cy, radius * 0.5f, circlePaint)
        canvas.drawCircle(cx, cy, radius * 0.25f, circlePaint)

        // 2. Draw the Rotating Scanner Line
        // Math: x = r * cos(theta), y = r * sin(theta)
        val radian = Math.toRadians(rotationAngle.toDouble())
        val endX = cx + (radius * cos(radian)).toFloat()
        val endY = cy + (radius * sin(radian)).toFloat()

        canvas.drawLine(cx, cy, endX, endY, linePaint)
    }


    fun setAlarmState(isDanger: Boolean) {
        if (isDanger != isAlarming) {
            isAlarming = isDanger
            if (isDanger) {
                // Change to RED
                circlePaint.color = Color.RED
                linePaint.color = Color.RED
                linePaint.setShadowLayer(10f, 0f, 0f, Color.RED)
            } else {
                // Back to GREEN
                circlePaint.color = Color.GREEN
                linePaint.color = Color.GREEN
                linePaint.setShadowLayer(10f, 0f, 0f, Color.GREEN)
            }
            invalidate()
        }
    }
}