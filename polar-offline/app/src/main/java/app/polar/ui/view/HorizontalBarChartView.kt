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

class HorizontalBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Bar(
        val label: String,
        val value: Int,
        val max: Int,
        val color: Int
    )

    private var bars: List<Bar> = emptyList()
    private var animatedFraction = 0f

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(12f)
        typeface = Typeface.DEFAULT
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(12f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val barRect = RectF()
    private var animator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        updateColors()
    }

    private fun updateColors() {
        trackPaint.color = resolveColor(com.google.android.material.R.attr.colorSurfaceVariant)
        val onSurface = resolveColor(com.google.android.material.R.attr.colorOnSurface)
        labelPaint.color = onSurface
        labelPaint.alpha = (255 * 0.85f).toInt()
        valuePaint.color = onSurface
    }

    private fun resolveColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        return if (context.theme.resolveAttribute(attr, typedValue, true)) typedValue.data
        else android.graphics.Color.GRAY
    }

    fun setData(newBars: List<Bar>) {
        bars = newBars
        animatedFraction = 0f
        updateColors()
        requestLayout()
        startAnimation()
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                this@HorizontalBarChartView.animatedFraction = it.animatedValue as Float
                this@HorizontalBarChartView.invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return

        val padding = dpToPx(16f)
        val topOffset = dpToPx(8f)
        val itemHeight = dpToPx(36f)
        val barHeight = dpToPx(10f)
        val labelBarGap = dpToPx(8f)

        val labelWidth = bars.maxOf { labelPaint.measureText(it.label) }.coerceAtMost(width * 0.35f)
        val barStart = padding + labelWidth + dpToPx(8f)
        val barEnd = width - padding - dpToPx(32f)
        val maxBarWidth = (barEnd - barStart).coerceAtLeast(1f)

        var currentY = topOffset

        for (bar in bars) {
            // Label
            canvas.drawText(
                bar.label,
                padding,
                currentY + dpToPx(10f),
                labelPaint
            )

            // Track
            val trackTop = currentY + labelBarGap
            barRect.set(barStart, trackTop, barEnd, trackTop + barHeight)
            canvas.drawRoundRect(barRect, barHeight / 2f, barHeight / 2f, trackPaint)

            // Bar
            val max = bar.max.coerceAtLeast(1)
            val targetWidth = (bar.value.toFloat() / max) * maxBarWidth
            val animatedWidth = targetWidth * animatedFraction
            barRect.set(barStart, trackTop, barStart + animatedWidth, trackTop + barHeight)
            barPaint.color = bar.color
            canvas.drawRoundRect(barRect, barHeight / 2f, barHeight / 2f, barPaint)

            // Value / percentage
            val percent = ((bar.value.toFloat() / max) * 100).toInt()
            val valueText = "$percent%"
            canvas.drawText(
                valueText,
                barEnd + dpToPx(8f),
                trackTop + barHeight / 2f + dpToPx(4f),
                valuePaint
            )

            currentY += itemHeight
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (dpToPx(36f) * bars.size + dpToPx(32f)).toInt()
        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = View.MeasureSpec.getSize(heightMeasureSpec)
        val height = when (heightMode) {
            View.MeasureSpec.EXACTLY -> heightSize
            View.MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }
        setMeasuredDimension(
            View.MeasureSpec.getSize(widthMeasureSpec),
            height
        )
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
