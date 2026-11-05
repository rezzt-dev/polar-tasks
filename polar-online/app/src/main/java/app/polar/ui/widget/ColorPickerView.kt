package app.polar.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Selector de color hsv: un cuadro de saturacion (horizontal) y brillo (vertical) y debajo una
 * barra de tono. Se arrastra dentro de listas y hojas inferiores sin que estas se desplacen.
 *
 * [onColorChanged] solo se llama al mover los controles, nunca al fijar el color con [setColor].
 */
class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onColorChanged: ((Int) -> Unit)? = null

    private var hue = 200f
    private var sat = 0.8f
    private var bri = 0.9f

    private val density = resources.displayMetrics.density
    private val hueStripHeight = 28f * density
    private val hueStripMargin = 16f * density
    private val areaRadius = 12f * density
    private val hueRadius = hueStripHeight / 2f
    private val selectorRadius = 12f * density
    private val ringWidth = 3f * density
    private val shadowRadius = 1.5f * density

    private val areaRect = RectF()
    private val hueRect = RectF()
    private val hsv = FloatArray(3)

    private val satPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth + 2 * shadowRadius
        color = SHADOW
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth
        color = Color.WHITE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private enum class Drag { NONE, AREA, HUE }
    private var drag = Drag.NONE

    init {
        isFocusable = true
    }

    /** Color actual (opaco). */
    fun getColor(): Int = hsvColor(hue, sat, bri)

    /**
     * Mueve los controles a [color] sin avisar a [onColorChanged]. Los grises y el negro no tienen
     * tono: se conserva el anterior para que la barra no salte al rojo.
     */
    fun setColor(color: Int) {
        Color.colorToHSV(color, hsv)
        if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]
        if (hsv[2] > 0f) sat = hsv[1]
        bri = hsv[2]
        updateShaders()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Margen para que los selectores no se recorten al llegar a los bordes.
        val edge = selectorRadius + ringWidth
        val left = paddingLeft + edge
        val right = w - paddingRight - edge
        val top = paddingTop + edge
        // El selector de tono cabe dentro de la barra: abajo basta con el grosor del anillo.
        val bottom = h - paddingBottom - ringWidth
        hueRect.set(left, bottom - hueStripHeight, right, bottom)
        areaRect.set(left, top, right, (hueRect.top - hueStripMargin).coerceAtLeast(top + 1f))
        huePaint.shader = LinearGradient(hueRect.left, 0f, hueRect.right, 0f, HUE_COLORS, null, Shader.TileMode.CLAMP)
        valPaint.shader = LinearGradient(0f, areaRect.top, 0f, areaRect.bottom, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        updateShaders()
    }

    private fun updateShaders() {
        if (areaRect.isEmpty) return
        satPaint.shader = LinearGradient(
            areaRect.left, 0f, areaRect.right, 0f,
            Color.WHITE, hsvColor(hue, 1f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (areaRect.isEmpty) return
        canvas.drawRoundRect(areaRect, areaRadius, areaRadius, satPaint)
        canvas.drawRoundRect(areaRect, areaRadius, areaRadius, valPaint)
        canvas.drawRoundRect(hueRect, hueRadius, hueRadius, huePaint)

        drawSelector(canvas, areaRect.left + sat * areaRect.width(), areaRect.top + (1f - bri) * areaRect.height(), getColor())
        val hueX = (hueRect.left + hue / 360f * hueRect.width()).coerceIn(hueRect.left + hueRadius, hueRect.right - hueRadius)
        drawSelector(canvas, hueX, hueRect.centerY(), hsvColor(hue, 1f, 1f))
    }

    /** Circulo con el color elegido, anillo blanco y una sombra fina para verse sobre cualquier color. */
    private fun drawSelector(canvas: Canvas, x: Float, y: Float, color: Int) {
        fillPaint.color = color
        canvas.drawCircle(x, y, selectorRadius, fillPaint)
        canvas.drawCircle(x, y, selectorRadius, shadowPaint)
        canvas.drawCircle(x, y, selectorRadius, ringPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                drag = if (event.y >= hueRect.top - hueStripMargin / 2) Drag.HUE else Drag.AREA
                // Que la lista o la hoja inferior que nos contiene no se desplace mientras se arrastra.
                parent?.requestDisallowInterceptTouchEvent(true)
                update(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> update(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                update(event.x, event.y)
                drag = Drag.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                drag = Drag.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun update(x: Float, y: Float) {
        when (drag) {
            Drag.HUE -> {
                hue = ((x - hueRect.left) / hueRect.width()).coerceIn(0f, 1f) * 360f
                updateShaders()
            }
            Drag.AREA -> {
                sat = ((x - areaRect.left) / areaRect.width()).coerceIn(0f, 1f)
                bri = 1f - ((y - areaRect.top) / areaRect.height()).coerceIn(0f, 1f)
            }
            Drag.NONE -> return
        }
        invalidate()
        onColorChanged?.invoke(getColor())
    }

    private fun hsvColor(h: Float, s: Float, v: Float): Int {
        hsv[0] = h % 360f
        hsv[1] = s
        hsv[2] = v
        return Color.HSVToColor(hsv)
    }

    private companion object {
        const val SHADOW = 0x33000000
        val HUE_COLORS = intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
        )
    }
}
