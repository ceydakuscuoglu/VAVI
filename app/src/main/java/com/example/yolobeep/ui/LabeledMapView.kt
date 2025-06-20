package com.example.yolobeep.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.*
import org.json.JSONArray
import java.io.InputStream

class LabeledMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs), ScaleGestureDetector.OnScaleGestureListener {

    private var mapBitmap: Bitmap? = null

    // Grid size (set from outside or loaded dynamically)
    var gridRows: Int = 1
    var gridCols: Int = 1

    // Dynamic overlays
    var userPosition: Pair<Int, Int>? = null
    var path: List<Pair<Int, Int>> = emptyList()
    var targetPosition: Pair<Int, Int>? = null
    var startPosition: Pair<Int, Int>? = null
    var endPosition: Pair<Int, Int>? = null

    // Pan/zoom
    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = INVALID_POINTER_ID
    private val scaleDetector = ScaleGestureDetector(context, this)

    var onCellTapped: ((row: Int, col: Int) -> Unit)? = null

    var showGridOverlay: Boolean = true
    var grid: Array<IntArray>? = null
    private var gridOverlayBitmap: Bitmap? = null
    private var lastOverlayParams: Triple<Int, Int, Int>? = null // grid hash, rows, cols

    companion object {
        private const val INVALID_POINTER_ID = -1
    }

    init {
        // Load PNG from assets
        val assetManager = context.assets
        val inputStream: InputStream = assetManager.open("harita_labelli.png")
        mapBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mapBitmap?.let { bmp ->
            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scaleFactor, scaleFactor)

            // Rotate +90° and move origin to bottom left
            canvas.rotate(90f, 0f, 0f)
            canvas.translate(0f, -width.toFloat())

            // Now, width and height are swapped for the image fitting
            val viewAspect = height.toFloat() / width.toFloat()
            val bmpAspect = bmp.width.toFloat() / bmp.height.toFloat()
            val drawRect: RectF
            if (bmpAspect > viewAspect) {
                val scaledHeight = height.toFloat() / bmpAspect
                val top = (width - scaledHeight) / 2f
                drawRect = RectF(0f, top, height.toFloat(), top + scaledHeight)
            } else {
                val scaledWidth = width.toFloat() * bmpAspect
                val left = (height - scaledWidth) / 2f
                drawRect = RectF(left, 0f, left + scaledWidth, width.toFloat())
            }
            canvas.drawBitmap(bmp, null, drawRect, null)

            // Draw grid overlay for debugging (bitmap cached)
            if (showGridOverlay && grid != null) {
                val params = Triple(gridHash(), gridRows, gridCols)
                if (gridOverlayBitmap == null || lastOverlayParams != params ||
                    gridOverlayBitmap?.width != drawRect.width().toInt() ||
                    gridOverlayBitmap?.height != drawRect.height().toInt()) {
                    generateGridOverlayBitmap(RectF(0f, 0f, drawRect.width(), drawRect.height()))
                    lastOverlayParams = params
                }
                gridOverlayBitmap?.let { overlayBmp ->
                    canvas.drawBitmap(overlayBmp, null, drawRect, null)
                }
            }

            // Draw overlays (pass drawRect for correct mapping)
            drawPath(canvas, drawRect)
            drawUser(canvas, drawRect)
            drawTarget(canvas, drawRect)
            canvas.restore()
        }
    }

    private fun drawUser(canvas: Canvas, drawRect: RectF) {
        userPosition?.let { (i, j) ->
            val (x, y) = gridToPixel(i, j, drawRect)
            val paint = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(x, y, 16f, paint)
        }
        // Draw start label
        startPosition?.let { (i, j) ->
            val (x, y) = gridToPixel(i, j, drawRect)
            val paint = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(x, y, 16f, paint)
            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 36f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText("Start", x, y - 24f, textPaint)
        }
        // Draw end label
        endPosition?.let { (i, j) ->
            val (x, y) = gridToPixel(i, j, drawRect)
            val paint = Paint().apply {
                color = Color.RED
                style = Paint.Style.FILL
            }
            canvas.drawCircle(x, y, 16f, paint)
            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 36f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText("End", x, y - 24f, textPaint)
        }
    }

    private fun drawPath(canvas: Canvas, drawRect: RectF) {
        if (path.size < 2) return
        val paint = Paint().apply {
            color = Color.GREEN
            strokeWidth = 8f
            style = Paint.Style.STROKE
        }
        val points = path.map { gridToPixel(it.first, it.second, drawRect) }
        for (k in 0 until points.size - 1) {
            val (x1, y1) = points[k]
            val (x2, y2) = points[k + 1]
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
    }

    private fun drawTarget(canvas: Canvas, drawRect: RectF) {
        targetPosition?.let { (i, j) ->
            val (x, y) = gridToPixel(i, j, drawRect)
            val paint = Paint().apply {
                color = Color.RED
                style = Paint.Style.FILL
            }
            canvas.drawCircle(x, y, 16f, paint)
        }
    }

    // Map grid (i, j) to pixel (x, y) in the rotated image area
    fun gridToPixel(i: Int, j: Int, drawRect: RectF): Pair<Float, Float> {
        val cellWidth = drawRect.width() / gridCols
        val cellHeight = drawRect.height() / gridRows
        val x = drawRect.left + (j + 0.5f) * cellWidth
        val y = drawRect.top + (i + 0.5f) * cellHeight
        return Pair(x, y)
    }

    // Pan/Zoom Gesture Handling
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Only handle scaling for multi-touch events
        if (event.pointerCount > 1) {
            scaleDetector.onTouchEvent(event)
        }

        // Only handle tap for single-finger ACTION_UP
        if (event.pointerCount == 1 && event.action == MotionEvent.ACTION_UP) {
            // Undo the canvas transforms to get the tap in image coordinates
            val x = event.x
            val y = event.y

            // Undo scale and offset
            val px = (x - offsetX) / scaleFactor
            val py = (y - offsetY) / scaleFactor

            // Undo rotation: rotate point -90deg around (0,0), then translate
            val rx = py
            val ry = width - px

            // Now, find the drawRect as in onDraw
            mapBitmap?.let { bmp ->
                val viewAspect = height.toFloat() / width.toFloat()
                val bmpAspect = bmp.width.toFloat() / bmp.height.toFloat()
                val drawRect: RectF = if (bmpAspect > viewAspect) {
                    val scaledHeight = height.toFloat() / bmpAspect
                    val top = (width - scaledHeight) / 2f
                    RectF(0f, top, height.toFloat(), top + scaledHeight)
                } else {
                    val scaledWidth = width.toFloat() * bmpAspect
                    val left = (height - scaledWidth) / 2f
                    RectF(left, 0f, left + scaledWidth, width.toFloat())
                }
                // Check if inside image area
                if (drawRect.contains(rx, ry)) {
                    val col = ((rx - drawRect.left) / drawRect.width() * gridCols).toInt()
                    val row = ((ry - drawRect.top) / drawRect.height() * gridRows).toInt()
                    if (row in 0 until gridRows && col in 0 until gridCols) {
                        onCellTapped?.invoke(row, col)
                    }
                }
            }
        }
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        scaleFactor *= detector.scaleFactor
        scaleFactor = scaleFactor.coerceIn(0.5f, 3.0f)
        invalidate()
        return true
    }
    override fun onScaleBegin(detector: ScaleGestureDetector) = true
    override fun onScaleEnd(detector: ScaleGestureDetector) {}

    // Utility to load grid size from JSON
    fun loadGridSizeFromJson(context: Context, assetName: String = "grid_map.json"): Pair<Int, Int> {
        val inputStream = context.assets.open(assetName)
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(jsonString)
        val rows = jsonArray.length()
        val cols = if (rows > 0) (jsonArray.getJSONArray(0).length()) else 0
        return Pair(rows, cols)
    }

    private fun generateGridOverlayBitmap(drawRect: RectF) {
        val gridArr = grid ?: return
        if (gridRows <= 0 || gridCols <= 0) return
        val bmp = Bitmap.createBitmap(drawRect.width().toInt(), drawRect.height().toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cellWidth = drawRect.width() / gridCols
        val cellHeight = drawRect.height() / gridRows
        for (i in 0 until gridRows) {
            for (j in 0 until gridCols) {
                val value = gridArr.getOrNull(i)?.getOrNull(j) ?: continue
                val color = when (value) {
                    0 -> Color.argb(60, 0, 255, 0) // walkable: semi-transparent green
                    else -> Color.argb(60, 255, 0, 0) // blocked: semi-transparent red
                }
                val paint = Paint().apply { this.color = color }
                val left = j * cellWidth
                val top = i * cellHeight
                canvas.drawRect(left, top, left + cellWidth, top + cellHeight, paint)
            }
        }
        gridOverlayBitmap = bmp
    }

    private fun gridHash(): Int {
        // Simple hash for grid content
        return grid?.contentDeepHashCode() ?: 0
    }
} 