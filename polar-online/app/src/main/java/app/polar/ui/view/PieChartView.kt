package app.polar.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(
        val label: String,
        val value: Float,
        val color: Int
    )

    private var slices: List<Slice> = emptyList()
    private var animatedFraction = 0f
    private var totalValue = 0f

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val legendCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(12f)
        typeface = Typeface.DEFAULT
    }

    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(12f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = resolveColor(com.google.android.material.R.attr.colorSurfaceVariant)
        strokeWidth = dpToPx(24f)
        strokeCap = Paint.Cap.ROUND
    }

    private val oval = RectF()
    private var animator: ValueAnimator? = null

    private var onSurfaceColor = 0

    init {
        setWillNotDraw(false)
        updateColors()
    }

    private fun updateColors() {
        onSurfaceColor = resolveColor(com.google.android.material.R.attr.colorOnSurface)
        textPaint.color = onSurfaceColor
        textPaint.alpha = (255 * 0.85f).toInt()
        percentPaint.color = onSurfaceColor
    }

    private fun resolveColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        return if (context.theme.resolveAttribute(attr, typedValue, true)) typedValue.data
        else android.graphics.Color.GRAY
    }

    fun setData(newSlices: List<Slice>) {
        slices = newSlices.filter { it.value > 0 }
        totalValue = slices.sumOf { it.value.toDouble() }.toFloat()
        animatedFraction = 0f
        updateColors()
        requestLayout()
        startAnimation()
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                this@PieChartView.animatedFraction = it.animatedValue as Float
                this@PieChartView.invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (slices.isEmpty() || totalValue <= 0f) {
            drawEmptyDonut(canvas)
            return
        }

        val padding = dpToPx(16f)
        val availableWidth = width - padding * 2
        val availableHeight = height - padding * 2

        // Donut uses left portion; legend uses right portion
        val chartAreaWidth = availableWidth * 0.55f
        val chartSize = minOf(chartAreaWidth, availableHeight)
        val stroke = chartSize * 0.22f
        val radius = (chartSize - stroke) / 2f

        val centerX = padding + chartSize / 2f
        val centerY = height / 2f

        strokePaint.strokeWidth = stroke
        oval.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        var startAngle = -90f
        val totalSweep = 360f * animatedFraction

        // Reserve a small gap between slices
        val gapAngle = 2f
        val usableSweep = totalSweep - gapAngle * slices.size

        for (slice in slices) {
            val sliceSweep = (slice.value / totalValue) * usableSweep
            strokePaint.color = slice.color
            canvas.drawArc(oval, startAngle, sliceSweep, false, strokePaint)
            startAngle += sliceSweep + gapAngle
        }

        drawLegend(canvas, centerX + radius + stroke + dpToPx(24f), padding, availableWidth - chartSize - dpToPx(24f))
    }

    private fun drawEmptyDonut(canvas: Canvas) {
        val padding = dpToPx(16f)
        val availableWidth = width - padding * 2
        val availableHeight = height - padding * 2
        val chartSize = minOf(availableWidth * 0.55f, availableHeight)
        val stroke = chartSize * 0.22f
        val radius = (chartSize - stroke) / 2f
        val centerX = padding + chartSize / 2f
        val centerY = height / 2f
        emptyPaint.strokeWidth = stroke
        oval.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        canvas.drawArc(oval, 0f, 360f, false, emptyPaint)
    }

    private fun drawLegend(canvas: Canvas, left: Float, top: Float, maxWidth: Float) {
        val itemHeight = dpToPx(40f)
        val circleSize = dpToPx(8f)
        val lineSpacing = dpToPx(8f)

        val total = totalValue.coerceAtLeast(1f)
        var currentY = top

        for (slice in slices) {
            if (currentY + itemHeight > height - top) break

            legendCirclePaint.color = slice.color
            canvas.drawCircle(left + circleSize, currentY + circleSize, circleSize, legendCirclePaint)

            val percent = ((slice.value / total) * 100).toInt()
            val labelText = slice.label
            val percentText = "$percent%"
            val labelStart = left + circleSize * 3f

            // Label on the first line
            canvas.drawText(
                labelText,
                labelStart,
                currentY + dpToPx(11f),
                textPaint
            )

            // Percentage below the label to avoid horizontal collisions
            canvas.drawText(
                percentText,
                labelStart,
                currentY + dpToPx(26f),
                percentPaint
            )

            currentY += itemHeight + lineSpacing
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
