package com.example.yolobeep.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

class GridMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    // Grid data
    var grid: Array<IntArray> = arrayOf()
        set(value) {
            field = value
            invalidate()
        }
    var start: Pair<Int, Int>? = null
        set(value) {
            field = value
            invalidate()
        }
    var end: Pair<Int, Int>? = null
        set(value) {
            field = value
            invalidate()
        }
    var path: List<Pair<Int, Int>> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var current: Pair<Int, Int>? = null
        set(value) {
            field = value
            invalidate()
        }
    var onCellTapped: ((row: Int, col: Int) -> Unit)? = null
    var rotationDegrees: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val wallPaint = Paint().apply { 
        color = Color.BLACK
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val walkablePaint = Paint().apply { 
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val gridLinePaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 2f
    }
    private val startPaint = Paint().apply { color = Color.GREEN }
    private val endPaint = Paint().apply { color = Color.RED }
    private val pathPaint = Paint().apply { color = Color.BLUE }
    private val currentPaint = Paint().apply { color = Color.YELLOW }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (grid.isEmpty()) return
        val rows = grid.size
        val cols = grid[0].size

        val rot = ((rotationDegrees % 360) + 360) % 360
        val scaleFactor = 0.9f // Make the map 90% of the max possible size
        val (cellSize, gridWidth, gridHeight, offsetX, offsetY) = when (rot) {
            90f, 270f -> {
                val cellWidth = width / rows.toFloat()
                val cellHeight = height / cols.toFloat()
                val cellSize = minOf(cellWidth, cellHeight) * scaleFactor
                val gridWidth = cellSize * rows
                val gridHeight = cellSize * cols
                val offsetX = (width - gridWidth) / 2f
                val offsetY = (height - gridHeight) / 2f
                listOf(cellSize, gridWidth, gridHeight, offsetX, offsetY)
            }
            else -> {
                val cellWidth = width / cols.toFloat()
                val cellHeight = height / rows.toFloat()
                val cellSize = minOf(cellWidth, cellHeight) * scaleFactor
                val gridWidth = cellSize * cols
                val gridHeight = cellSize * rows
                val offsetX = (width - gridWidth) / 2f
                val offsetY = (height - gridHeight) / 2f
                listOf(cellSize, gridWidth, gridHeight, offsetX, offsetY)
            }
        }

        // Fill background with white
        canvas.drawColor(Color.WHITE)

        // Draw only wall cells as black rectangles, scaled and centered
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val (drawRow, drawCol) = when (rot) {
                    90f -> col to (rows - 1 - row)
                    180f -> (rows - 1 - row) to (cols - 1 - col)
                    270f -> (cols - 1 - col) to row
                    else -> row to col
                }
                if (grid[row][col] == 1) {
                    val left = offsetX + drawCol * cellSize
                    val top = offsetY + drawRow * cellSize
                    val right = left + cellSize
                    val bottom = top + cellSize
                    canvas.drawRect(left, top, right, bottom, wallPaint)
                }
            }
        }

        // Draw path
        path.forEach { (row, col) ->
            val (drawRow, drawCol) = when (rot) {
                90f -> col to (rows - 1 - row)
                180f -> (rows - 1 - row) to (cols - 1 - col)
                270f -> (cols - 1 - col) to row
                else -> row to col
            }
            val left = offsetX + drawCol * cellSize
            val top = offsetY + drawRow * cellSize
            val right = left + cellSize
            val bottom = top + cellSize
            canvas.drawRect(left, top, right, bottom, pathPaint)
        }

        // Draw start/end/current
        start?.let { (row, col) ->
            val (drawRow, drawCol) = when (rot) {
                90f -> col to (rows - 1 - row)
                180f -> (rows - 1 - row) to (cols - 1 - col)
                270f -> (cols - 1 - col) to row
                else -> row to col
            }
            val left = offsetX + drawCol * cellSize
            val top = offsetY + drawRow * cellSize
            val right = left + cellSize
            val bottom = top + cellSize
            canvas.drawRect(left, top, right, bottom, startPaint)
        }
        end?.let { (row, col) ->
            val (drawRow, drawCol) = when (rot) {
                90f -> col to (rows - 1 - row)
                180f -> (rows - 1 - row) to (cols - 1 - col)
                270f -> (cols - 1 - col) to row
                else -> row to col
            }
            val left = offsetX + drawCol * cellSize
            val top = offsetY + drawRow * cellSize
            val right = left + cellSize
            val bottom = top + cellSize
            canvas.drawRect(left, top, right, bottom, endPaint)
        }
        current?.let { (row, col) ->
            val (drawRow, drawCol) = when (rot) {
                90f -> col to (rows - 1 - row)
                180f -> (rows - 1 - row) to (cols - 1 - col)
                270f -> (cols - 1 - col) to row
                else -> row to col
            }
            val left = offsetX + drawCol * cellSize
            val top = offsetY + drawRow * cellSize
            val right = left + cellSize
            val bottom = top + cellSize
            canvas.drawRect(left, top, right, bottom, currentPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && grid.isNotEmpty()) {
            val rows = grid.size
            val cols = grid[0].size
            val rot = ((rotationDegrees % 360) + 360) % 360
            val scaleFactor = 0.9f // Keep in sync with onDraw
            val (cellSize, gridWidth, gridHeight, offsetX, offsetY) = when (rot) {
                90f, 270f -> {
                    val cellWidth = width / rows.toFloat()
                    val cellHeight = height / cols.toFloat()
                    val cellSize = minOf(cellWidth, cellHeight) * scaleFactor
                    val gridWidth = cellSize * rows
                    val gridHeight = cellSize * cols
                    val offsetX = (width - gridWidth) / 2f
                    val offsetY = (height - gridHeight) / 2f
                    listOf(cellSize, gridWidth, gridHeight, offsetX, offsetY)
                }
                else -> {
                    val cellWidth = width / cols.toFloat()
                    val cellHeight = height / rows.toFloat()
                    val cellSize = minOf(cellWidth, cellHeight) * scaleFactor
                    val gridWidth = cellSize * cols
                    val gridHeight = cellSize * rows
                    val offsetX = (width - gridWidth) / 2f
                    val offsetY = (height - gridHeight) / 2f
                    listOf(cellSize, gridWidth, gridHeight, offsetX, offsetY)
                }
            }
            // Map touch to grid cell (drawRow, drawCol)
            val x = event.x - offsetX
            val y = event.y - offsetY
            var drawCol = (x / cellSize).toInt()
            var drawRow = (y / cellSize).toInt()
            // Map (drawRow, drawCol) to (row, col) in grid based on rotation
            val (row, col) = when (rot) {
                90f -> (rows - 1 - drawCol) to drawRow
                180f -> (rows - 1 - drawRow) to (cols - 1 - drawCol)
                270f -> drawCol to (cols - 1 - drawRow)
                else -> drawRow to drawCol
            }
            if (row in 0 until rows && col in 0 until cols) {
                onCellTapped?.invoke(row, col)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
} 