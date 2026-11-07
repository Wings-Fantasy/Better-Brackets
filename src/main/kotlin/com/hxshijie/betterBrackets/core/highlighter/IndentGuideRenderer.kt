package com.hxshijie.betterBrackets.core.highlighter

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.IndentGuideDescriptor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.SoftWrap
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.EditorPainter
import com.intellij.openapi.editor.impl.view.VisualLinesIterator
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Key
import com.intellij.ui.paint.LinePainter2D
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import kotlin.math.max
import kotlin.math.min

/**
 * 缩进引导线自定义渲染器。
 */
class IndentGuideRenderer(
    val color: Color,
    val indentColumn: Int
) : CustomHighlighterRenderer {

    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
        if (editor !is EditorEx) return

        val doc = highlighter.document
        val startOffset = highlighter.startOffset
        val endOffset = highlighter.endOffset
        if (startOffset >= doc.textLength) return

        val startLine = doc.getLineNumber(startOffset)
        val endLine = doc.getLineNumber(endOffset)
        if (startLine + 1 >= endLine) return

        val lineHeight = editor.lineHeight

        val startVisualPos = editor.logicalToVisualPosition(LogicalPosition(startLine + 1, indentColumn))
        val endVisualPos = editor.logicalToVisualPosition(LogicalPosition(endLine, indentColumn))

        val pointX = editor.visualPositionToXY(startVisualPos).x
        val targetX = max(0, pointX + getIndentGuideShift(editor))

        val startY = editor.visualPositionToXY(startVisualPos).y
        var maxY = editor.visualPositionToXY(endVisualPos).y

        val clip = g.clipBounds
        if (clip != null) {
            if (clip.y >= maxY || clip.y + clip.height <= startY) {
                return
            }
            maxY = min(maxY, clip.y + clip.height)
        }
        if (startY >= maxY) return

        val guide = editor.getUserData(KEY_ACTIVE_INDENT_GUIDE) ?: findActiveGuide(editor)
        val selected = guide != null &&
            guide.indentLevel == indentColumn &&
            guide.startLine == startLine &&
            guide.endLine == endLine

        val g2d = g as? Graphics2D ?: return
        val oldColor = g2d.color
        try {
            g2d.color = color

            val tabSize = EditorUtil.getTabSize(editor).coerceAtLeast(1)
            val softWraps = editor.softWrapModel.registeredSoftWraps

            val drawSegment = { segStart: Int, segEnd: Int ->
                val segStartVisual = editor.logicalToVisualPosition(LogicalPosition(segStart, indentColumn))
                val segEndVisual = editor.logicalToVisualPosition(LogicalPosition(segEnd + 1, indentColumn))
                val segStartY = editor.visualPositionToXY(segStartVisual).y
                var segMaxY = editor.visualPositionToXY(segEndVisual).y

                var paintAllowed = true
                if (clip != null) {
                    if (clip.y >= segMaxY || clip.y + clip.height <= segStartY) {
                        paintAllowed = false
                    } else {
                        segMaxY = min(segMaxY, clip.y + clip.height)
                    }
                }
                if (paintAllowed && segStartY < segMaxY) {
                    val actualStartY = if (clip != null && segStartY < clip.y) clip.y else segStartY
                    if (actualStartY < segMaxY) {
                        if (selected) {
                            LinePainter2D.paint(g2d, targetX.toDouble(), actualStartY.toDouble(), targetX.toDouble(), (segMaxY - 1).toDouble())
                            LinePainter2D.paint(g2d, (targetX + 1).toDouble(), actualStartY.toDouble(), (targetX + 1).toDouble(), (segMaxY - 1).toDouble())
                        } else {
                            LinePainter2D.paint(g2d, targetX.toDouble(), actualStartY.toDouble(), targetX.toDouble(), (segMaxY - 1).toDouble())
                        }
                    }
                }
            }

            if (softWraps.isEmpty()) {
                var segStartLine = -1
                for (line in (startLine + 1) until endLine) {
                    if (isLineValidForIndentGuide(doc, line, indentColumn, tabSize)) {
                        if (segStartLine == -1) {
                            segStartLine = line
                        }
                    } else {
                        if (segStartLine != -1) {
                            drawSegment(segStartLine, line - 1)
                            segStartLine = -1
                        }
                    }
                }
                if (segStartLine != -1) {
                    drawSegment(segStartLine, endLine - 1)
                }
            } else {
                var curY = startY
                var startVisualLine = startVisualPos.line
                if (clip != null && curY < clip.y) {
                    curY = clip.y
                    startVisualLine = editor.yToVisualLine(clip.y)
                }
                val it = VisualLinesIterator(editor as EditorImpl, startVisualLine)
                while (!it.atEnd()) {
                    val lineY = it.y
                    if (lineY >= curY) {
                        if (lineY >= maxY) break
                        val logLine = editor.visualToLogicalPosition(VisualPosition(it.visualLine, 0)).line
                        val isLogicalLineValid = isLineValidForIndentGuide(doc, logLine, indentColumn, tabSize)
                        if (!isLogicalLineValid) {
                            if (curY < lineY) {
                                if (selected) {
                                    LinePainter2D.paint(g2d, targetX.toDouble(), curY.toDouble(), targetX.toDouble(), (lineY - 1).toDouble())
                                    LinePainter2D.paint(g2d, (targetX + 1).toDouble(), curY.toDouble(), (targetX + 1).toDouble(), (lineY - 1).toDouble())
                                } else {
                                    LinePainter2D.paint(g2d, targetX.toDouble(), curY.toDouble(), targetX.toDouble(), (lineY - 1).toDouble())
                                }
                            }
                            curY = lineY + lineHeight
                        } else if (it.startsWithSoftWrap()) {
                            val softWrap: SoftWrap = softWraps[it.startOrPrevWrapIndex]
                            if (softWrap.indentInColumns < indentColumn) {
                                if (curY < lineY) {
                                    if (selected) {
                                        LinePainter2D.paint(g2d, targetX.toDouble(), curY.toDouble(), targetX.toDouble(), (lineY - 1).toDouble())
                                        LinePainter2D.paint(g2d, (targetX + 1).toDouble(), curY.toDouble(), (targetX + 1).toDouble(), (lineY - 1).toDouble())
                                    } else {
                                        LinePainter2D.paint(g2d, targetX.toDouble(), curY.toDouble(), targetX.toDouble(), (lineY - 1).toDouble())
                                    }
                                }
                                curY = lineY + lineHeight
                            }
                        }
                    }
                    it.advance()
                }
                if (curY < maxY) {
                    if (selected) {
                        LinePainter2D.paint(g2d, targetX.toDouble(), curY.toDouble(), targetX.toDouble(), (maxY - 1).toDouble())
                        LinePainter2D.paint(g2d, (targetX + 1).toDouble(), curY.toDouble(), (targetX + 1).toDouble(), (maxY - 1).toDouble())
                    } else {
                        LinePainter2D.paint(g2d, targetX.toDouble(), curY.toDouble(), targetX.toDouble(), (maxY - 1).toDouble())
                    }
                }
            }
        } finally {
            g2d.color = oldColor
        }
    }

    private fun isLineValidForIndentGuide(doc: Document, line: Int, indentColumn: Int, tabSize: Int): Boolean {
        if (line !in 0 until doc.lineCount) return false
        val start = doc.getLineStartOffset(line)
        val end = doc.getLineEndOffset(line)
        val chars = doc.charsSequence
        var col = 0
        var offset = start
        while (offset < end) {
            val c = chars[offset]
            when (c) {
                ' ' -> col++
                '\t' -> col = (col / tabSize + 1) * tabSize
                '\r', '\n' -> break
                else -> {
                    return col > indentColumn
                }
            }
            offset++
        }
        return true
    }

    private fun getIndentGuideShift(editor: EditorEx): Int {
        return try {
            EditorPainter.getIndentGuideShift(editor)
        } catch (_: Throwable) {
            0
        }
    }

    companion object {
        val KEY_ACTIVE_INDENT_GUIDE: Key<IndentGuideDescriptor> =
            Key.create("BETTER_BRACKETS_ACTIVE_INDENT_GUIDE")

        val INDENT_DESCRIPTORS_KEY: Key<List<IndentGuideDescriptor>> =
            Key.create("BETTER_BRACKETS_INDENT_DESCRIPTORS_KEY")

        /**
         * 查找当前光标激活的缩进引导线。
         */
        fun findActiveGuide(editor: Editor): IndentGuideDescriptor? {
            val caretModel = editor.caretModel
            val pos = caretModel.logicalPosition
            val line = pos.line
            val column = pos.column

            val descriptors = editor.getUserData(INDENT_DESCRIPTORS_KEY) ?: emptyList()

            val directGuide = editor.indentsModel.caretIndentGuide
            if (directGuide != null && line in (directGuide.startLine + 1)..directGuide.endLine) {
                return directGuide
            }
            val matchAtColumn = descriptors.firstOrNull { d ->
                column == d.indentLevel && line in (d.startLine + 1)..d.endLine
            }
            if (matchAtColumn != null) {
                return matchAtColumn
            }

            val enclosing = descriptors.filter { d ->
                line in (d.startLine + 1)..d.endLine
            }
            if (enclosing.isNotEmpty()) {
                return enclosing.maxWithOrNull(
                    compareBy<IndentGuideDescriptor> { it.indentLevel }
                        .thenBy { it.startLine }
                )
            }

            return null
        }
    }
}
