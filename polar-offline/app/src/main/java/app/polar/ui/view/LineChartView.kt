package app.polar.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<Int> = emptyList()
    private var labels: List<String> = emptyList()
    private var maxDataValue = 1

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dpToPx(10f)
        typeface = Typeface.DEFAULT
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900
        interpolator = DecelerateInterpolator(1.5f)
    }
    private var animationProgress = 0f

    private val chartPath = Path()
    private val fillPath = Path()
    private val chartRect = RectF()

    init {
        setWillNotDraw(false)
        updateColors()
    }

    private fun updateColors() {
        val primary = resolveColor(android.R.attr.colorPrimary)
        val onSurface = resolveColor(com.google.android.material.R.attr.colorOnSurface)
        val surfaceVariant = resolveColor(com.google.android.material.R.attr.colorSurfaceVariant)

        linePaint.color = primary
        gridPaint.color = surfaceVariant
        labelPaint.color = onSurface
        labelPaint.alpha = (255 * 0.6f).toInt()
    }

    private fun resolveColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        return if (context.theme.resolveAttribute(attr, typedValue, true)) typedValue.data
        else android.graphics.Color.GRAY
    }

    fun setData(values: List<Int>, xLabels: List<String>) {
        data = values
        labels = xLabels
        maxDataValue = data.maxOrNull()?.coerceAtLeast(1) ?: 1
        updateColors()
        startAnimation()
    }

    private fun startAnimation() {
        animator.cancel()
        animator.removeAllUpdateListeners()
        animator.addUpdateListener {
            animationProgress = it.animatedValue as Float
            invalidate()
        }
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val paddingStart = dpToPx(8f)
        val paddingEnd = dpToPx(8f)
        val paddingTop = dpToPx(16f)
        val paddingBottom = dpToPx(24f)

        chartRect.set(
            paddingStart,
            paddingTop,
            width - paddingEnd,
            height - paddingBottom
        )

        drawGrid(canvas)
        drawLineAndFill(canvas)
        drawLabels(canvas)
    }

    private fun drawGrid(canvas: Canvas) {
        val steps = 4
        for (i in 0..steps) {
            val y = chartRect.bottom - (chartRect.height() / steps) * i
            canvas.drawLine(chartRect.left, y, chartRect.right, y, gridPaint)
        }
    }

    private fun drawLineAndFill(canvas: Canvas) {
        chartPath.reset()
        fillPath.reset()

        val pointCount = data.size
        val usableWidth = chartRect.width()
        val usableHeight = chartRect.height()
        val stepX = if (pointCount > 1) usableWidth / (pointCount - 1) else usableWidth / 2f

        val points = data.mapIndexed { index, value ->
            val x = chartRect.left + stepX * index
            val ratio = value.toFloat() / maxDataValue
            val y = chartRect.bottom - ratio * usableHeight * animationProgress
            x to y
        }

        if (points.isEmpty()) return

        chartPath.moveTo(points.first().first, points.first().second)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            // Simple cubic bezier smoothing
            val midX = (prev.first + curr.first) / 2f
            chartPath.cubicTo(midX, prev.second, midX, curr.second, curr.first, curr.second)
        }

        // Fill path: line + bottom corners
        fillPath.addPath(chartPath)
        fillPath.lineTo(points.last().first, chartRect.bottom)
        fillPath.lineTo(points.first().first, chartRect.bottom)
        fillPath.close()

        val primary = resolveColor(android.R.attr.colorPrimary)
        val transparentPrimary = android.graphics.Color.argb(
            (255 * 0.15f).toInt(),
            android.graphics.Color.red(primary),
            android.graphics.Color.green(primary),
            android.graphics.Color.blue(primary)
        )
        fillPaint.shader = LinearGradient(
            0f, chartRect.top, 0f, chartRect.bottom,
            transparentPrimary, android.graphics.Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(chartPath, linePaint)
    }

    private fun drawLabels(canvas: Canvas) {
        val pointCount = data.size
        val usableWidth = chartRect.width()
        val stepX = if (pointCount > 1) usableWidth / (pointCount - 1) else usableWidth / 2f

        for (i in labels.indices) {
            val label = labels.getOrNull(i) ?: continue
            if (label.isBlank()) continue
            val x = chartRect.left + stepX * i
            canvas.drawText(label, x, height - dpToPx(6f), labelPaint)
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}
