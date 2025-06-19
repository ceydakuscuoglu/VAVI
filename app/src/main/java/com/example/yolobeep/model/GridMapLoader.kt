package com.example.yolobeep.model

import android.content.Context
import org.json.JSONArray

object GridMapLoader {
    fun loadGridMap(context: Context, fileName: String = "grid_map.json"): Array<IntArray> {
        val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(jsonString)
        val grid = Array(jsonArray.length()) { row ->
            val rowArray = jsonArray.getJSONArray(row)
            IntArray(rowArray.length()) { col ->
                rowArray.getInt(col)
            }
        }
        return grid
    }
} 