package com.example.yolobeep.model

import java.util.PriorityQueue

object AStarPathfinder {
    data class Node(val row: Int, val col: Int, val g: Int, val h: Int, val parent: Node?) : Comparable<Node> {
        val f: Int get() = g + h
        override fun compareTo(other: Node): Int = f.compareTo(other.f)
    }

    private val directions = listOf(
        0 to 1, 1 to 0, 0 to -1, -1 to 0 // right, down, left, up
    )

    fun findPath(
        grid: Array<IntArray>,
        start: Pair<Int, Int>,
        end: Pair<Int, Int>
    ): List<Pair<Int, Int>> {
        val rows = grid.size
        val cols = grid[0].size
        val (startRow, startCol) = start
        val (endRow, endCol) = end
        val openSet = PriorityQueue<Node>()
        val closedSet = mutableSetOf<Pair<Int, Int>>()
        openSet.add(Node(startRow, startCol, 0, heuristic(startRow, startCol, endRow, endCol), null))

        while (openSet.isNotEmpty()) {
            val current = openSet.poll()
            if (current.row == endRow && current.col == endCol) {
                return reconstructPath(current)
            }
            closedSet.add(current.row to current.col)
            for ((dr, dc) in directions) {
                val nr = current.row + dr
                val nc = current.col + dc
                if (nr in 0 until rows && nc in 0 until cols && grid[nr][nc] == 0 && (nr to nc) !in closedSet) {
                    val neighbor = Node(nr, nc, current.g + 1, heuristic(nr, nc, endRow, endCol), current)
                    openSet.add(neighbor)
                }
            }
        }
        return emptyList() // No path found
    }

    private fun heuristic(r1: Int, c1: Int, r2: Int, c2: Int): Int {
        // Manhattan distance
        return kotlin.math.abs(r1 - r2) + kotlin.math.abs(c1 - c2)
    }

    private fun reconstructPath(node: Node): List<Pair<Int, Int>> {
        val path = mutableListOf<Pair<Int, Int>>()
        var curr: Node? = node
        while (curr != null) {
            path.add(0, curr.row to curr.col)
            curr = curr.parent
        }
        return path
    }
} 