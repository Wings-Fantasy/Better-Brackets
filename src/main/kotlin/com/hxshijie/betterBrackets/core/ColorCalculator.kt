package com.hxshijie.betterBrackets.core

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBColor
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

/**
 * 括号色彩计算与属性缓存工具类。
 */
class ColorCalculator {

    companion object {
        private val attributesCache = ConcurrentHashMap<String, TextAttributes>()

        fun getTextAttributes(depth: Int, colorHexList: List<String>): TextAttributes {
            if (colorHexList.isEmpty()) {
                return TextAttributes()
            }

            val index = if (depth >= 0) depth % colorHexList.size else 0
            val hexColor = colorHexList[index]

            return attributesCache.computeIfAbsent(hexColor) { hex ->
                val color = parseHexColor(hex)
                TextAttributes(color, null, null, null, 0)
            }
        }

        fun parseHexColor(hex: String): Color {
            return try {
                val cleaned = hex.trim().removePrefix("#")
                val rgb = cleaned.toInt(16)
                Color(rgb)
            } catch (_: Exception) {
                JBColor.foreground()
            }
        }

        fun clearCache() {
            attributesCache.clear()
        }

        @Suppress("DEPRECATION")
        fun notifySettingsChanged() {
            clearCache()
            for (project in ProjectManager.getInstance().openProjects) {
                if (!project.isDisposed) {
                    DaemonCodeAnalyzer.getInstance(project).restart()
                }
            }
        }
    }
}
