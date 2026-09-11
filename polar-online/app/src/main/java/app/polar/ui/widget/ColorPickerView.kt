package app.polar.ui.widget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onColorChanged: ((Int) -> Unit)? = null

    private var hue = 200f   // nice teal default
    private var sat = 0.8f
    private var bri = 0.9f

    private val density get() = resources.displayMetrics.density
    private val hueStripH get() = 40f * density
    private val hueStripMargin get() = 12f * density
    private val selectorR get() = 14f * density
    private val hueSelectorR get() = 18f * density
    private val hueBarRadius get() = 8f * density

    private fun paddedLeft() = paddingLeft.toFloat()
    private fun paddedTop() = paddingTop.toFloat()
    private fun paddedRight() = (width - paddingRight).toFloat()
    private fun paddedBottom() = (height - paddingBottom).toFloat()
    private fun paddedW() = paddedRight() - paddedLeft()
    private fun mainH() = (paddedBottom() - paddedTop() - hueStripMargin - hueStripH).coerceAtLeast(1f)

    // Main area shaders
    private val satPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = null
    }
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Selector paints
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x44000000
    }
    private val whiteDiskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val colorDiskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val hueSelectorBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f
    }
    private val hueSelectorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val hueColors = intArrayOf(
        Color.RED, Color.YELLOW, Color.GREEN,
        Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
    )
    private val huePositions = floatArrayOf(0f, 1f/6f, 2f/6f, 3f/6f, 4f/6f, 5f/6f, 1f)

    private var selectorX = 0f
    private var selectorY = 0f
    private var hueSelectorX = 0f

    private var draggingHue = false
    private var draggingMain = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShaders()
        syncSelectorPositions()
    }

    private fun rebuildShaders() {
        if (width == 0) return
        val pl = paddedLeft(); val pr = paddedRight()
        val pt = paddedTop()
        val mh = mainH()
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))

        satPaint.shader = LinearGradient(pl, 0f, pr, 0f, Color.WHITE, hueColor, Shader.TileMode.CLAMP)
        valPaint.shader = LinearGradient(0f, pt, 0f, pt + mh, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        huePaint.shader = LinearGradient(pl, 0f, pr, 0f, hueColors, huePositions, Shader.TileMode.CLAMP)
    }

    private fun syncSelectorPositions() {
        if (width == 0) return
        val pl = paddedLeft(); val pr = paddedRight()
        val pt = paddedTop()
        val mh = mainH()
        selectorX = (pl + sat * (pr - pl)).coerceIn(pl, pr)
        selectorY = (pt + (1f - bri) * mh).coerceIn(pt, pt + mh)
        hueSelectorX = (pl + (hue / 360f) * (pr - pl)).coerceIn(pl, pr)
    }

    override fun onDraw(canvas: Canvas) {
        val pl = paddedLeft(); val pr = paddedRight()
        val pt = paddedTop()
        val mh = mainH()
        val hueTop = pt + mh + hueStripMargin

        // --- Main gradient ---
        val mainRect = RectF(pl, pt, pr, pt + mh)
        canvas.drawRoundRect(mainRect, 12f, 12f, satPaint)
        canvas.drawRoundRect(mainRect, 12f, 12f, valPaint)

        // --- Main color selector ---
        // Shadow
        canvas.drawCircle(selectorX, selectorY, selectorR + 4f, shadowPaint)
        // White disk
        canvas.drawCircle(selectorX, selectorY, selectorR, whiteDiskPaint)
        // Color disk (the actual selected color)
        colorDiskPaint.color = getColor()
        canvas.drawCircle(selectorX, selectorY, selectorR - 3.5f * density, colorDiskPaint)

        // --- Hue strip ---
        val hueRect = RectF(pl, hueTop, pr, hueTop + hueStripH)
        canvas.drawRoundRect(hueRect, hueBarRadius, hueBarRadius, huePaint)

        // --- Hue selector ---
        val hx = hueSelectorX.coerceIn(pl + hueSelectorR / 2, pr - hueSelectorR / 2)
        val hy = hueTop + hueStripH / 2
        // shadow
        canvas.drawCircle(hx, hy, hueSelectorR + 3f, shadowPaint)
        // fill with hue color
        hueSelectorFillPaint.color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        canvas.drawCircle(hx, hy, hueSelectorR, hueSelectorFillPaint)
        // white border
        canvas.drawCircle(hx, hy, hueSelectorR, hueSelectorBorderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val pt = paddedTop()
        val hueTop = pt + mainH() + hueStripMargin

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggingHue = y >= hueTop
                draggingMain = y < hueTop
            }
        }

        val pl = paddedLeft(); val pr = paddedRight()

        if (draggingHue) {
            hueSelectorX = x.coerceIn(pl, pr)
            hue = ((hueSelectorX - pl) / (pr - pl)) * 360f
            rebuildShaders()
            notifyColor()
            invalidate()
        } else if (draggingMain) {
            selectorX = x.coerceIn(pl, pr)
            selectorY = y.coerceIn(pt, pt + mainH())
            sat = (selectorX - pl) / (pr - pl)
            bri = 1f - (selectorY - pt) / mainH()
            notifyColor()
            invalidate()
        }
        return true
    }

    private fun notifyColor() = onColorChanged?.invoke(getColor())

    fun getColor(): Int = Color.HSVToColor(floatArrayOf(hue, sat, bri))

    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0]; sat = hsv[1]; bri = hsv[2]
        if (width > 0) { rebuildShaders(); syncSelectorPositions(); invalidate() }
    }
}
