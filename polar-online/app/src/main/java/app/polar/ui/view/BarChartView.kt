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
import androidx.core.content.ContextCompat
import app.polar.R
import com.google.android.material.color.MaterialColors

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<Float> = emptyList()
    private var labels: List<String> = emptyList()
    
    private var animatedData: FloatArray = FloatArray(0)
    private var maxDataValue: Float = 0f
    
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val emptyBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dpToPx(12f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dpToPx(10f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    
    private val barRect = RectF()
    private var animator: ValueAnimator? = null
    
    // Config
    private val barWidthDp = 16f
    private val cornerRadiusDp = 8f
    private val labelMarginTopDp = 16f
    private val valueMarginBottomDp = 6f
    
    private var primaryColor: Int = 0
    private var surfaceVariantColor: Int = 0
    private var onSurfaceColor: Int = 0
    
    init {
        setWillNotDraw(false)
        updateColors()
    }
    
    private fun getColorFromTheme(attr: Int, defaultColor: Int): Int {
        val typedValue = android.util.TypedValue()
        return if (context.theme.resolveAttribute(attr, typedValue, true)) {
            typedValue.data
        } else {
            defaultColor
        }
    }

    private fun updateColors() {
        primaryColor = getColorFromTheme(android.R.attr.colorPrimary, android.graphics.Color.BLUE)
        surfaceVariantColor = getColorFromTheme(com.google.android.material.R.attr.colorSurfaceVariant, android.graphics.Color.DKGRAY)
        onSurfaceColor = getColorFromTheme(com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE)
        
        barPaint.color = primaryColor
        emptyBarPaint.color = surfaceVariantColor
        
        // Ensure text is legible with correct alpha
        textPaint.color = onSurfaceColor
        textPaint.alpha = (255 * 0.5f).toInt()
        
        valueTextPaint.color = onSurfaceColor
        valueTextPaint.alpha = (255 * 0.8f).toInt()
    }

    fun setData(newData: List<Int>, newLabels: List<String>) {
        updateColors()
        data = newData.map { it.toFloat() }
        labels = newLabels
        maxDataValue = data.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        animatedData = FloatArray(data.size) { 0f }
        
        requestLayout()
        startAnimation()
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                for (i in data.indices) {
                    animatedData[i] = data[i] * fraction
                }
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (data.isEmpty() || maxDataValue == 0f) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        val barWidth = dpToPx(barWidthDp)
        val cornerRadius = dpToPx(cornerRadiusDp)
        val labelMargin = dpToPx(labelMarginTopDp)
        val valueMargin = dpToPx(valueMarginBottomDp)
        
        val textHeight = textPaint.textSize
        val valueTextHeight = valueTextPaint.textSize
        
        val availableHeightForBars = viewHeight - textHeight - labelMargin - valueTextHeight - valueMargin - dpToPx(10f)
        
        val spacing = viewWidth / data.size

        for (i in data.indices) {
            val centerX = spacing * i + spacing / 2f
            
            // Draw X label
            val labelY = viewHeight - dpToPx(4f)
            if (i < labels.size) {
                canvas.drawText(labels[i], centerX, labelY, textPaint)
            }
            
            val barBottom = viewHeight - textHeight - labelMargin
            
            // Calculate height based on animated value
            val value = animatedData[i]
            val actualDataValue = data[i]
            
            val barHeight = if (value > 0) {
                (value / maxDataValue) * availableHeightForBars
            } else {
                dpToPx(4f) // Minimum visible bar height
            }
            
            val barTop = barBottom - barHeight
            
            barRect.set(centerX - barWidth / 2f, barTop, centerX + barWidth / 2f, barBottom)
            
            if (actualDataValue > 0) {
                canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint)
                
                // Draw value text on top
                val displayValue = value.toInt()
                if (displayValue > 0) {
                    canvas.drawText(
                        displayValue.toString(), 
                        centerX, 
                        barTop - valueMargin, 
                        valueTextPaint
                    )
                }
            } else {
                canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, emptyBarPaint)
            }
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
