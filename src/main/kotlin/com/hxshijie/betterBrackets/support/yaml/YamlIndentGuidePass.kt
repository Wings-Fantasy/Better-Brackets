package com.hxshijie.betterBrackets.support.yaml

import com.hxshijie.betterBrackets.core.ColorCalculator
import com.hxshijie.betterBrackets.core.highlighter.IndentGuideRenderer
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.hxshijie.betterBrackets.settings.configurable.ui.BracketPreviewEditor
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.IndentGuideDescriptor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.SoftWrap
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.EditorPainter
import com.intellij.openapi.editor.impl.view.VisualLinesIterator
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.ui.paint.LinePainter2D
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLSequenceItem
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.util.ArrayList
import java.util.HashSet
import kotlin.math.max
import kotlin.math.min

/**
 * YAML 语言代码分析高亮 Pass（包含垂直缩进引导线与当前作用域高亮）。
 */
class YamlIndentGuidePass(
    project: Project,
    editor: Editor,
    private val psiFile: YAMLFile
) : TextEditorHighlightingPass(project, editor.document, false), DumbAware {

    private val myEditor: EditorEx = editor as EditorEx
    @Volatile
    private var myResult: CollectResult? = null

    override fun doCollectInformation(progress: ProgressIndicator) {
        val currentStamp = nowStamp(myEditor)
        val stamp = myEditor.getUserData(LAST_TIME_INDENTS_BUILT)
        if (stamp != null && stamp == currentStamp) return

        myResult = collectInformation(myEditor, psiFile, progress, getState(myEditor), currentStamp)
    }

    override fun doApplyInformationToEditor() {
        val result = myResult ?: return
        applyInformationToEditor(myEditor, result)
    }

    data class IndentLineInfo(
        val range: TextRange,
        val descriptor: IndentGuideDescriptor,
        val color: Color
    )

    data class ScopeInfo(
        val activeRange: TextRange,
        val highlightRange: TextRange,
        val color: Color
    )

    data class CollectResult(
        val descriptors: List<IndentGuideDescriptor>,
        val lineInfos: List<IndentLineInfo>,
        val scopeInfos: List<ScopeInfo>,
        val stamp: Long = -1L
    )

    companion object {
        private val INDENT_HIGHLIGHTERS_KEY =
            Key.create<MutableList<RangeHighlighter>>("BETTER_BRACKETS_YAML_INDENT_HIGHLIGHTERS_KEY")
        private val LAST_TIME_INDENTS_BUILT =
            Key.create<Long>("BETTER_BRACKETS_YAML_LAST_TIME_INDENTS_BUILT")
        private val KEY_CARET_LISTENER_ATTACHED =
            Key.create<Boolean>("BETTER_BRACKETS_YAML_CARET_LISTENER_ATTACHED")

        val SCOPE_INFOS_KEY: Key<List<ScopeInfo>> =
            Key.create("BETTER_BRACKETS_YAML_SCOPE_INFOS_KEY")
        val KEY_ACTIVE_SCOPE_HIGHLIGHTER: Key<RangeHighlighter> =
            Key.create("BETTER_BRACKETS_YAML_ACTIVE_SCOPE_HIGHLIGHTER")
        val KEY_ACTIVE_SCOPE_RANGE: Key<TextRange> =
            Key.create("BETTER_BRACKETS_YAML_ACTIVE_SCOPE_RANGE")

        fun getState(editor: Editor): BetterBracketsState {
            return editor.getUserData(BracketPreviewEditor.STATE_PROVIDER_KEY)?.invoke() ?: BetterBracketsState.instance
        }

        fun applyForPreview(
            editor: Editor,
            psiFile: YAMLFile,
            previewState: BetterBracketsState = getState(editor)
        ) {
            val editorEx = editor as? EditorEx ?: return
            editorEx.putUserData(LAST_TIME_INDENTS_BUILT, null)
            val result = collectInformation(editorEx, psiFile, null, previewState)
            applyInformationToEditor(editorEx, result)
        }

        fun collectInformation(
            editor: EditorEx,
            psiFile: YAMLFile,
            progress: ProgressIndicator? = null,
            state: BetterBracketsState = getState(editor),
            stamp: Long = nowStamp(editor)
        ): CollectResult {
            val isIndentEnabled = state.isYamlEnabled && state.isYamlIndentGuideEnabled
            val isScopeEnabled = state.isYamlEnabled && state.isYamlScopeEnabled

            if (!isIndentEnabled && !isScopeEnabled) {
                return CollectResult(emptyList(), emptyList(), emptyList(), stamp)
            }

            val curlyColors = state.yamlCurlyColors
            val squareColors = state.yamlSquareColors
            val document = editor.document
            val tabSize = EditorUtil.getTabSize(editor).coerceAtLeast(1)

            val descriptors = ArrayList<IndentGuideDescriptor>()
            val lineInfos = ArrayList<IndentLineInfo>()
            val scopeInfos = ArrayList<ScopeInfo>()
            val seenBlocks = HashSet<Triple<Int, Int, Int>>()
            val seenScopes = HashSet<Pair<Int, Int>>()

            psiFile.accept(object : PsiRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) {
                    progress?.checkCanceled()

                    // 1. YAMLKeyValue 处理（键值对与代码块结构）
                    if (element is YAMLKeyValue) {
                        val key = element.key
                        val value = element.value
                        if (key != null) {
                            val depth = calculateStructuralDepth(element)
                            val color = getColorForDepth(depth, curlyColors)
                            val keyRange = key.textRange

                            val startLine = document.getLineNumber(keyRange.startOffset)
                            val valEndOffset = if (value != null) {
                                (value.textRange.endOffset - 1).coerceAtLeast(value.textRange.startOffset)
                            } else {
                                keyRange.endOffset
                            }
                            val lastLine = document.getLineNumber(valEndOffset)

                            // 作用域高亮
                            if (isScopeEnabled) {
                                val blockEndOffset = minOf(
                                    element.textRange.endOffset,
                                    document.getLineEndOffset(lastLine)
                                )
                                if (keyRange.startOffset < blockEndOffset) {
                                    val blockRange = TextRange(keyRange.startOffset, blockEndOffset)
                                    if (seenScopes.add(Pair(blockRange.startOffset, blockRange.endOffset))) {
                                        scopeInfos.add(ScopeInfo(blockRange, blockRange, color))
                                    }
                                }
                            }

                            // 缩进引导线
                            if (isIndentEnabled && value != null && startLine < lastLine) {
                                val indentColumn = getLineIndentColumn(document, startLine, tabSize)
                                if (indentColumn > 0) {
                                    val endLine = minOf(document.lineCount, lastLine + 1)
                                    val blockKey = Triple(startLine, endLine, indentColumn)
                                    if (seenBlocks.add(blockKey)) {
                                        val descriptor = IndentGuideDescriptor(indentColumn, startLine, endLine)
                                        val endOffset = if (endLine < document.lineCount) {
                                            document.getLineStartOffset(endLine)
                                        } else {
                                            document.textLength
                                        }
                                        val textRange = TextRange(document.getLineStartOffset(startLine), endOffset)
                                        descriptors.add(descriptor)
                                        lineInfos.add(IndentLineInfo(textRange, descriptor, color))
                                    }
                                }
                            }
                        }
                    }

                    // 2. YAMLSequenceItem 处理（列表项）
                    if (element is YAMLSequenceItem) {
                        val range = element.textRange
                        val depth = calculateStructuralDepth(element)
                        val color = getColorForDepth(depth, squareColors)

                        val itemStartLine = document.getLineNumber(range.startOffset)
                        val itemLastLine = document.getLineNumber((range.endOffset - 1).coerceAtLeast(range.startOffset))

                        // 作用域高亮
                        if (isScopeEnabled && itemStartLine < itemLastLine) {
                            val itemEndOffset = minOf(range.endOffset, document.getLineEndOffset(itemLastLine))
                            if (range.startOffset < itemEndOffset) {
                                val blockRange = TextRange(range.startOffset, itemEndOffset)
                                if (seenScopes.add(Pair(blockRange.startOffset, blockRange.endOffset))) {
                                    scopeInfos.add(ScopeInfo(blockRange, blockRange, color))
                                }
                            }
                        }

                        // 缩进引导线
                        if (isIndentEnabled && itemStartLine < itemLastLine) {
                            val indentColumn = getLineIndentColumn(document, itemStartLine, tabSize)
                            if (indentColumn > 0) {
                                val endLine = minOf(document.lineCount, itemLastLine + 1)
                                val blockKey = Triple(itemStartLine, endLine, indentColumn)
                                if (seenBlocks.add(blockKey)) {
                                    val descriptor = IndentGuideDescriptor(indentColumn, itemStartLine, endLine)
                                    val endOffset = if (endLine < document.lineCount) {
                                        document.getLineStartOffset(endLine)
                                    } else {
                                        document.textLength
                                    }
                                    val textRange = TextRange(document.getLineStartOffset(itemStartLine), endOffset)
                                    descriptors.add(descriptor)
                                    lineInfos.add(IndentLineInfo(textRange, descriptor, color))
                                }
                            }
                        }
                    }

                    // 3. 流式括号结构处理
                    val node = element.node
                    if (node != null) {
                        val lBrace = node.findChildByType(YAMLTokenTypes.LBRACE)
                        val rBrace = node.findChildByType(YAMLTokenTypes.RBRACE)
                        if (lBrace != null && rBrace != null) {
                            val range = TextRange(lBrace.startOffset, rBrace.startOffset + rBrace.textLength)
                            val depth = YamlBraceSupport.calculateAstDepth(lBrace.psi)
                            val color = getColorForDepth(depth, curlyColors)

                            if (isScopeEnabled && range.startOffset < range.endOffset) {
                                if (seenScopes.add(Pair(range.startOffset, range.endOffset))) {
                                    scopeInfos.add(ScopeInfo(range, range, color))
                                }
                            }

                            if (isIndentEnabled) {
                                val startLine = document.getLineNumber(lBrace.startOffset)
                                val endLine = document.getLineNumber(rBrace.startOffset)
                                if (startLine + 1 < endLine) {
                                    val indentColumn = getLineIndentColumn(document, startLine, tabSize)
                                    if (indentColumn > 0) {
                                        val blockKey = Triple(startLine, endLine, indentColumn)
                                        if (seenBlocks.add(blockKey)) {
                                            val descriptor = IndentGuideDescriptor(indentColumn, startLine, endLine)
                                            val textRange = TextRange(
                                                document.getLineStartOffset(startLine),
                                                document.getLineEndOffset(endLine)
                                            )
                                            descriptors.add(descriptor)
                                            lineInfos.add(IndentLineInfo(textRange, descriptor, color))
                                        }
                                    }
                                }
                            }
                        }

                        val lBracket = node.findChildByType(YAMLTokenTypes.LBRACKET)
                        val rBracket = node.findChildByType(YAMLTokenTypes.RBRACKET)
                        if (lBracket != null && rBracket != null) {
                            val range = TextRange(lBracket.startOffset, rBracket.startOffset + rBracket.textLength)
                            val depth = YamlBraceSupport.calculateAstDepth(lBracket.psi)
                            val color = getColorForDepth(depth, squareColors)

                            if (isScopeEnabled && range.startOffset < range.endOffset) {
                                if (seenScopes.add(Pair(range.startOffset, range.endOffset))) {
                                    scopeInfos.add(ScopeInfo(range, range, color))
                                }
                            }

                            if (isIndentEnabled) {
                                val startLine = document.getLineNumber(lBracket.startOffset)
                                val endLine = document.getLineNumber(rBracket.startOffset)
                                if (startLine + 1 < endLine) {
                                    val indentColumn = getLineIndentColumn(document, startLine, tabSize)
                                    if (indentColumn > 0) {
                                        val blockKey = Triple(startLine, endLine, indentColumn)
                                        if (seenBlocks.add(blockKey)) {
                                            val descriptor = IndentGuideDescriptor(indentColumn, startLine, endLine)
                                            val textRange = TextRange(
                                                document.getLineStartOffset(startLine),
                                                document.getLineEndOffset(endLine)
                                            )
                                            descriptors.add(descriptor)
                                            lineInfos.add(IndentLineInfo(textRange, descriptor, color))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    super.visitElement(element)
                }
            })

            lineInfos.sortWith { a, b -> compareRanges(a.range, b.range) }
            descriptors.sortBy { it.startLine }

            return CollectResult(descriptors, lineInfos, scopeInfos, stamp)
        }

        fun applyInformationToEditor(
            editor: EditorEx,
            result: CollectResult
        ) {
            val currentStamp = nowStamp(editor)
            val stamp = editor.getUserData(LAST_TIME_INDENTS_BUILT)

            if (stamp == currentStamp && stamp == result.stamp) return
            editor.putUserData(LAST_TIME_INDENTS_BUILT, result.stamp)

            val oldHighlighters = editor.getUserData(INDENT_HIGHLIGHTERS_KEY)
            if (oldHighlighters != null) {
                for (h in oldHighlighters) {
                    h.dispose()
                }
                oldHighlighters.clear()
            }

            if (currentStamp == -1L) {
                editor.putUserData(IndentGuideRenderer.INDENT_DESCRIPTORS_KEY, emptyList())
                editor.putUserData(IndentGuideRenderer.KEY_ACTIVE_INDENT_GUIDE, null)
                editor.putUserData(SCOPE_INFOS_KEY, emptyList())
                updateScopeHighlight(editor)
                return
            }

            val newHighlighters = ArrayList<RangeHighlighter>()
            val mm = editor.markupModel
            for (lineInfo in result.lineInfos) {
                newHighlighters.add(createHighlighter(mm, lineInfo))
            }

            editor.putUserData(INDENT_HIGHLIGHTERS_KEY, newHighlighters)
            editor.putUserData(IndentGuideRenderer.INDENT_DESCRIPTORS_KEY, result.descriptors)
            editor.indentsModel.assumeIndents(result.descriptors)

            val initialGuide = IndentGuideRenderer.findActiveGuide(editor)
            editor.putUserData(IndentGuideRenderer.KEY_ACTIVE_INDENT_GUIDE, initialGuide)

            editor.putUserData(SCOPE_INFOS_KEY, result.scopeInfos)
            updateScopeHighlight(editor)

            ensureCaretListenerAttached(editor)
        }

        private fun createHighlighter(mm: MarkupModel, lineInfo: IndentLineInfo): RangeHighlighter {
            val highlighter = mm.addRangeHighlighter(
                lineInfo.range.startOffset,
                lineInfo.range.endOffset,
                0,
                null,
                HighlighterTargetArea.EXACT_RANGE
            )
            highlighter.customRenderer = YamlIndentGuideRenderer(
                lineInfo.color,
                lineInfo.descriptor.indentLevel,
                lineInfo.descriptor.startLine,
                lineInfo.descriptor.endLine
            )
            return highlighter
        }

        private fun getLineIndentColumn(document: Document, line: Int, tabSize: Int): Int {
            if (line !in 0 until document.lineCount) return 0
            val start = document.getLineStartOffset(line)
            val end = document.getLineEndOffset(line)
            val chars = document.charsSequence
            var column = 0
            var offset = start
            while (offset < end) {
                val c = chars[offset]
                when (c) {
                    ' ' -> column++
                    '\t' -> column = (column / tabSize + 1) * tabSize
                    else -> break
                }
                offset++
            }
            return column
        }

        private fun nowStamp(editor: EditorEx): Long {
            val state = getState(editor)
            val isIndentEnabled = state.isYamlEnabled && state.isYamlIndentGuideEnabled
            val isScopeEnabled = state.isYamlEnabled && state.isYamlScopeEnabled

            return if (isIndentEnabled || isScopeEnabled) {
                val colorHash = (state.yamlCurlyColors.hashCode().toLong() shl 16) xor state.yamlSquareColors.hashCode().toLong()
                val flags = (if (isIndentEnabled) 1L else 0L) or
                        (if (isScopeEnabled) 2L else 0L)
                editor.document.modificationStamp xor (EditorUtil.getTabSize(editor).toLong() shl 24) xor (colorHash shl 32) xor (flags shl 48)
            } else {
                -1L
            }
        }

        private fun compareRanges(r1: TextRange, r2: TextRange): Int {
            val answer = r1.startOffset - r2.startOffset
            return if (answer != 0) answer else r1.endOffset - r2.endOffset
        }

        fun updateScopeHighlight(editor: Editor) {
            val state = getState(editor)
            val oldHighlighter = editor.getUserData(KEY_ACTIVE_SCOPE_HIGHLIGHTER)

            if (!state.isYamlEnabled || !state.isYamlScopeEnabled) {
                if (oldHighlighter != null) {
                    oldHighlighter.dispose()
                    editor.putUserData(KEY_ACTIVE_SCOPE_HIGHLIGHTER, null)
                    editor.putUserData(KEY_ACTIVE_SCOPE_RANGE, null)
                }
                return
            }

            val scopeInfos = editor.getUserData(SCOPE_INFOS_KEY) ?: emptyList()
            val caretOffset = editor.caretModel.offset

            val activeScope = scopeInfos
                .filter { caretOffset in it.activeRange.startOffset..it.activeRange.endOffset }
                .minByOrNull { it.activeRange.length }

            val lastRange = editor.getUserData(KEY_ACTIVE_SCOPE_RANGE)
            val newRange = activeScope?.highlightRange

            if (newRange != lastRange || oldHighlighter == null || !oldHighlighter.isValid) {
                oldHighlighter?.dispose()
                editor.putUserData(KEY_ACTIVE_SCOPE_RANGE, newRange)

                if (activeScope != null && activeScope.highlightRange.endOffset <= editor.document.textLength) {
                    val attributes = TextAttributes().apply {
                        effectType = EffectType.ROUNDED_BOX
                        effectColor = activeScope.color
                    }
                    val newHighlighter = editor.markupModel.addRangeHighlighter(
                        activeScope.highlightRange.startOffset,
                        activeScope.highlightRange.endOffset,
                        HighlighterLayer.ELEMENT_UNDER_CARET,
                        attributes,
                        HighlighterTargetArea.EXACT_RANGE
                    )
                    editor.putUserData(KEY_ACTIVE_SCOPE_HIGHLIGHTER, newHighlighter)
                } else {
                    editor.putUserData(KEY_ACTIVE_SCOPE_HIGHLIGHTER, null)
                }
            }
        }

        fun ensureCaretListenerAttached(editor: Editor) {
            if (editor.getUserData(KEY_CARET_LISTENER_ATTACHED) == true) return
            editor.putUserData(KEY_CARET_LISTENER_ATTACHED, true)

            val listener = object : CaretListener {
                override fun caretPositionChanged(e: CaretEvent) {
                    val state = getState(editor)

                    if (state.isYamlEnabled && state.isYamlIndentGuideEnabled) {
                        val currentGuide = IndentGuideRenderer.findActiveGuide(editor)
                        val lastGuide = editor.getUserData(IndentGuideRenderer.KEY_ACTIVE_INDENT_GUIDE)
                        if (currentGuide != lastGuide) {
                            editor.putUserData(IndentGuideRenderer.KEY_ACTIVE_INDENT_GUIDE, currentGuide)
                            editor.contentComponent.repaint()
                        }
                    }

                    if (state.isYamlEnabled && state.isYamlScopeEnabled) {
                        updateScopeHighlight(editor)
                    }
                }
            }

            if (editor is Disposable) {
                editor.caretModel.addCaretListener(listener, editor)
            } else {
                editor.caretModel.addCaretListener(listener)
            }
        }

        fun calculateStructuralDepth(element: PsiElement): Int {
            var depth = 0
            var current: PsiElement? = element.parent
            while (current != null && current !is YAMLFile) {
                if (current is YAMLKeyValue || current is YAMLSequenceItem ||
                    current.node?.findChildByType(YAMLTokenTypes.LBRACE) != null ||
                    current.node?.findChildByType(YAMLTokenTypes.LBRACKET) != null
                ) {
                    depth++
                }
                current = current.parent
            }
            return depth
        }

        private fun getColorForDepth(depth: Int, colors: List<String>): Color {
            return if (colors.isNotEmpty()) {
                val hex = colors[depth % colors.size]
                ColorCalculator.parseHexColor(hex)
            } else {
                Color(0x3F, 0x91, 0x01)
            }
        }
    }

    /**
     * YAML 缩进引导线渲染器。
     */
    private class YamlIndentGuideRenderer(
        val color: Color,
        val indentColumn: Int,
        val targetStartLine: Int,
        val targetEndLine: Int
    ) : CustomHighlighterRenderer {

        override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
            if (editor !is EditorEx) return

            val doc = highlighter.document
            val startOffset = highlighter.startOffset
            if (startOffset >= doc.textLength) return

            val startLine = targetStartLine
            val endLine = targetEndLine
            if (startLine + 1 >= endLine) return

            val lineHeight = editor.lineHeight

            val startVisualPos = editor.logicalToVisualPosition(LogicalPosition(startLine + 1, indentColumn))
            val pointX = editor.visualPositionToXY(startVisualPos).x
            val targetX = max(0, pointX + getIndentGuideShift(editor))

            val startY = editor.visualPositionToXY(startVisualPos).y
            var maxY = if (endLine >= doc.lineCount) {
                val lastLinePos = editor.logicalToVisualPosition(LogicalPosition(doc.lineCount - 1, indentColumn))
                editor.visualPositionToXY(lastLinePos).y + lineHeight
            } else {
                val endVisualPos = editor.logicalToVisualPosition(LogicalPosition(endLine, indentColumn))
                editor.visualPositionToXY(endVisualPos).y
            }

            val clip = g.clipBounds
            if (clip != null) {
                if (clip.y >= maxY || clip.y + clip.height <= startY) {
                    return
                }
                maxY = min(maxY, clip.y + clip.height)
            }
            if (startY >= maxY) return

            val guide = editor.getUserData(IndentGuideRenderer.KEY_ACTIVE_INDENT_GUIDE)
                ?: IndentGuideRenderer.findActiveGuide(editor)
            val selected = guide != null &&
                guide.indentLevel == indentColumn &&
                guide.startLine == startLine &&
                guide.endLine == endLine

            val g2d = g as? Graphics2D ?: return
            val oldColor = g2d.color
            try {
                g2d.color = color

                val softWraps = editor.softWrapModel.registeredSoftWraps
                if (selected || softWraps.isEmpty()) {
                    if (selected) {
                        LinePainter2D.paint(g2d, targetX.toDouble(), startY.toDouble(), targetX.toDouble(), (maxY - 1).toDouble())
                        LinePainter2D.paint(g2d, (targetX + 1).toDouble(), startY.toDouble(), (targetX + 1).toDouble(), (maxY - 1).toDouble())
                    } else {
                        LinePainter2D.paint(g2d, targetX.toDouble(), startY.toDouble(), targetX.toDouble(), (maxY - 1).toDouble())
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
                            if (it.startsWithSoftWrap()) {
                                val softWrap: SoftWrap = softWraps[it.startOrPrevWrapIndex]
                                if (softWrap.indentInColumns < indentColumn) {
                                    if (curY < lineY) {
                                        LinePainter2D.paint(g2d, targetX.toDouble(), curY.toDouble(), targetX.toDouble(), (lineY - 1).toDouble())
                                    }
                                    curY = lineY + lineHeight
                                }
                            }
                        }
                        it.advance()
                    }
                    if (curY < maxY) {
                        LinePainter2D.paint(g2d, targetX.toDouble(), curY.toDouble(), targetX.toDouble(), (maxY - 1).toDouble())
                    }
                }
            } finally {
                g2d.color = oldColor
            }
        }

        private fun getIndentGuideShift(editor: EditorEx): Int {
            return try {
                EditorPainter.getIndentGuideShift(editor)
            } catch (_: Throwable) {
                0
            }
        }
    }
}
