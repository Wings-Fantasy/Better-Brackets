package com.hxshijie.betterBrackets.support.cpp

import com.hxshijie.betterBrackets.core.ColorCalculator
import com.hxshijie.betterBrackets.core.highlighter.IndentGuideRenderer
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.hxshijie.betterBrackets.settings.configurable.ui.BracketPreviewEditor
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.IndentGuideDescriptor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.util.DocumentUtil
import java.awt.Color
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/**
 * C/C++ 语言代码分析高亮 Pass（包含垂直缩进引导线与当前作用域高亮）。
 */
class CppIndentGuidePass(
    project: Project,
    editor: Editor,
    private val psiFile: PsiFile
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
            Key.create<MutableList<RangeHighlighter>>("BETTER_BRACKETS_CPP_INDENT_HIGHLIGHTERS_KEY")
        private val LAST_TIME_INDENTS_BUILT =
            Key.create<Long>("BETTER_BRACKETS_CPP_LAST_TIME_INDENTS_BUILT")
        private val KEY_CARET_LISTENER_ATTACHED =
            Key.create<Boolean>("BETTER_BRACKETS_CPP_CARET_LISTENER_ATTACHED")

        val SCOPE_INFOS_KEY: Key<List<ScopeInfo>> =
            Key.create("BETTER_BRACKETS_CPP_SCOPE_INFOS_KEY")
        val KEY_ACTIVE_SCOPE_HIGHLIGHTER: Key<RangeHighlighter> =
            Key.create("BETTER_BRACKETS_CPP_ACTIVE_SCOPE_HIGHLIGHTER")
        val KEY_ACTIVE_SCOPE_RANGE: Key<TextRange> =
            Key.create("BETTER_BRACKETS_CPP_ACTIVE_SCOPE_RANGE")

        fun getState(editor: Editor): BetterBracketsState {
            return editor.getUserData(BracketPreviewEditor.STATE_PROVIDER_KEY)?.invoke() ?: BetterBracketsState.instance
        }

        fun applyForPreview(
            editor: Editor,
            psiFile: PsiFile,
            previewState: BetterBracketsState = getState(editor)
        ) {
            val editorEx = editor as? EditorEx ?: return
            editorEx.putUserData(LAST_TIME_INDENTS_BUILT, null)
            val result = collectInformation(editorEx, psiFile, null, previewState)
            applyInformationToEditor(editorEx, result)
        }

        fun collectInformation(
            editor: EditorEx,
            psiFile: PsiFile,
            progress: ProgressIndicator?,
            state: BetterBracketsState = getState(editor),
            stamp: Long = nowStamp(editor)
        ): CollectResult {
            val isEnvironmentSupported = CppSupportUtil.isCppSupportedEnvironment()
            val isIndentEnabled = isEnvironmentSupported && state.isCppEnabled && state.isCppIndentGuideEnabled
            val isScopeEnabled = isEnvironmentSupported && state.isCppEnabled && state.isCppScopeEnabled

            if (!isIndentEnabled && !isScopeEnabled) {
                return CollectResult(emptyList(), emptyList(), emptyList(), stamp)
            }

            val document = editor.document
            val tabSize = EditorUtil.getTabSize(editor).coerceAtLeast(1)

            val descriptors = ArrayList<IndentGuideDescriptor>()
            val lineInfos = ArrayList<IndentLineInfo>()
            val scopeInfos = ArrayList<ScopeInfo>()
            val seenBlocks = HashSet<Triple<Int, Int, Int>>()
            val seenScopeRanges = HashSet<Pair<Int, Int>>()

            val colors = state.cppCurlyColors

            psiFile.accept(object : PsiRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) {
                    progress?.checkCanceled()

                    val braces = findBlockBraces(element)
                    if (braces != null) {
                        val (lBrace, rBrace) = braces
                        val startOffset = lBrace.textOffset
                        val endOffset = rBrace.textRange.endOffset
                        val lBraceLine = document.getLineNumber(startOffset)
                        val rBraceLine = document.getLineNumber(rBrace.textOffset)

                        val depth = CppBraceSupport.calculateAstDepth(lBrace, CppBracketType.BRACES)
                        val color = if (colors.isNotEmpty()) {
                            val hex = colors[depth % colors.size]
                            ColorCalculator.parseHexColor(hex)
                        } else {
                            Color(0x3F, 0x91, 0x01)
                        }

                        val declLine = findDeclarationLine(element, lBrace, document)
                        val declIndent = getLineIndentColumn(document, declLine, tabSize)
                        val lBraceIndent = getLineIndentColumn(document, lBraceLine, tabSize)
                        val rBraceIndent = getLineIndentColumn(document, rBraceLine, tabSize)

                        // 作用域高亮
                        if (isScopeEnabled && startOffset < endOffset) {
                            if (seenScopeRanges.add(Pair(startOffset, endOffset))) {
                                val activeStart = minOf(document.getLineStartOffset(declLine), startOffset)
                                val activeRange = TextRange(activeStart, endOffset)
                                val highlightRange = TextRange(startOffset, endOffset)
                                scopeInfos.add(ScopeInfo(activeRange, highlightRange, color))
                            }
                        }

                        // 缩进引导线
                        if (isIndentEnabled && lBraceLine + 1 < rBraceLine) {
                            val indentColumn = when {
                                rBraceIndent > 0 && declIndent > 0 -> minOf(rBraceIndent, declIndent)
                                rBraceIndent > 0 -> rBraceIndent
                                declIndent > 0 -> declIndent
                                else -> lBraceIndent
                            }

                            if (indentColumn > 0) {
                                val guideStartLine = if (declLine in 0 until lBraceLine && declIndent == indentColumn) {
                                    declLine
                                } else {
                                    lBraceLine
                                }

                                val key = Triple(guideStartLine, rBraceLine, indentColumn)
                                if (seenBlocks.add(key)) {
                                    val descriptor = IndentGuideDescriptor(indentColumn, guideStartLine, rBraceLine)
                                    val textRange = TextRange(
                                        document.getLineStartOffset(guideStartLine),
                                        document.getLineEndOffset(rBraceLine)
                                    )

                                    descriptors.add(descriptor)
                                    lineInfos.add(IndentLineInfo(textRange, descriptor, color))
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
            if (currentStamp == -1L) {
                if (oldHighlighters != null) {
                    for (h in oldHighlighters) {
                        h.dispose()
                    }
                    oldHighlighters.clear()
                }
                editor.putUserData(IndentGuideRenderer.INDENT_DESCRIPTORS_KEY, emptyList())
                editor.putUserData(IndentGuideRenderer.KEY_ACTIVE_INDENT_GUIDE, null)
                editor.putUserData(SCOPE_INFOS_KEY, emptyList())
                updateScopeHighlight(editor)
                return
            }

            val newHighlighters = ArrayList<RangeHighlighter>()
            val mm = editor.markupModel
            val lineInfos = result.lineInfos
            var curRange = 0

            if (oldHighlighters != null) {
                oldHighlighters.sortWith(
                    Comparator.comparing { h: RangeHighlighter -> !h.isValid }
                        .thenComparing(Segment.BY_START_OFFSET_THEN_END_OFFSET)
                )

                var curHighlight = 0
                while (curRange < lineInfos.size && curHighlight < oldHighlighters.size) {
                    val lineInfo = lineInfos[curRange]
                    val highlighter = oldHighlighters[curHighlight]
                    if (!highlighter.isValid) break

                    val cmp = compare(lineInfo.range, highlighter)
                    when {
                        cmp < 0 -> {
                            newHighlighters.add(createHighlighter(mm, lineInfo))
                            curRange++
                        }
                        cmp > 0 -> {
                            highlighter.dispose()
                            curHighlight++
                        }
                        else -> {
                            highlighter.customRenderer = IndentGuideRenderer(lineInfo.color, lineInfo.descriptor.indentLevel)
                            newHighlighters.add(highlighter)
                            curHighlight++
                            curRange++
                        }
                    }
                }
                while (curHighlight < oldHighlighters.size) {
                    val highlighter = oldHighlighters[curHighlight]
                    if (!highlighter.isValid) break
                    highlighter.dispose()
                    curHighlight++
                }
            }

            val startRangeIndex = curRange
            DocumentUtil.executeInBulk(editor.document, lineInfos.size > 10000) {
                for (i in startRangeIndex until lineInfos.size) {
                    newHighlighters.add(createHighlighter(mm, lineInfos[i]))
                }
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
            highlighter.customRenderer = IndentGuideRenderer(lineInfo.color, lineInfo.descriptor.indentLevel)
            return highlighter
        }

        /**
         * 寻找复合代码块的花括号对。
         */
        private fun findBlockBraces(element: PsiElement): Pair<PsiElement, PsiElement>? {
            val simpleName = element.javaClass.simpleName
            val elementTypeStr = element.node?.elementType?.toString() ?: ""

            if (CppSupportUtil.isStatementOrDefinitionContainer(simpleName, elementTypeStr)) {
                return null
            }

            var lBrace: PsiElement? = null
            var rBrace: PsiElement? = null

            var child: PsiElement? = element.firstChild
            while (child != null) {
                if (child.text == "{") {
                    lBrace = child
                    break
                }
                child = child.nextSibling
            }

            child = element.lastChild
            while (child != null) {
                if (child.text == "}") {
                    rBrace = child
                    break
                }
                child = child.prevSibling
            }

            return if (lBrace != null && rBrace != null) Pair(lBrace, rBrace) else null
        }

        /**
         * 计算代码块关联语句的起始行。
         */
        private fun findDeclarationLine(element: PsiElement, lBrace: PsiElement, document: Document): Int {
            val lBraceLine = document.getLineNumber(lBrace.textOffset)

            // 0. 优先检查 element 自身是否即为声明节点（如 class, struct, namespace, 函数等）
            val elemStartLine = document.getLineNumber(element.textRange.startOffset)
            if (elemStartLine <= lBraceLine && (lBraceLine - elemStartLine) in 0..15) {
                val elemText = element.text
                if (elemText.startsWith("class") || elemText.startsWith("struct") ||
                    elemText.startsWith("namespace") || elemText.startsWith("enum") ||
                    elemText.startsWith("template")
                ) {
                    return elemStartLine
                }
            }

            // 1. 尝试向前查找紧邻的前驱兄弟节点
            var prev = element.prevSibling
            while (prev != null && (prev.text.isBlank() || prev.javaClass.simpleName.contains("Comment", ignoreCase = true))) {
                prev = prev.prevSibling
            }
            if (prev != null) {
                val prevStartLine = document.getLineNumber(prev.textRange.startOffset)
                val prevEndLine = document.getLineNumber(prev.textRange.endOffset)
                if (prevEndLine in prevStartLine..lBraceLine) {
                    return prevStartLine
                }
            }

            // 2. 检查父节点
            val parent = element.parent
            if (parent != null && !parent.javaClass.simpleName.contains("File", ignoreCase = true)) {
                val parentStartLine = document.getLineNumber(parent.textOffset)
                if (parentStartLine <= lBraceLine) {
                    val lineDiff = lBraceLine - parentStartLine
                    if (lineDiff in 0..5) {
                        return parentStartLine
                    }
                }
            }

            // 3. 基于文本向上扫描连续的多行条件语句头
            var scanLine = lBraceLine
            while (scanLine > 0) {
                val prevLine = scanLine - 1
                val lineStart = document.getLineStartOffset(prevLine)
                val lineEnd = document.getLineEndOffset(prevLine)
                val lineText = document.charsSequence.subSequence(lineStart, lineEnd).toString().trim()
                if (lineText.isEmpty() || lineText.startsWith("//") || lineText.startsWith("/*") || lineText.endsWith("*/")) {
                    break
                }
                if (lineText.endsWith(";") && !lineText.contains("for") && !lineText.contains("if")) {
                    break
                }
                scanLine = prevLine
                if (lineText.startsWith("if") || lineText.startsWith("for") || lineText.startsWith("while") ||
                    lineText.startsWith("switch") || lineText.startsWith("catch") || lineText.startsWith("else")) {
                    break
                }
            }
            return scanLine
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
            val isEnvironmentSupported = CppSupportUtil.isCppSupportedEnvironment()
            val isIndentEnabled = isEnvironmentSupported && state.isCppEnabled && state.isCppIndentGuideEnabled
            val isScopeEnabled = isEnvironmentSupported && state.isCppEnabled && state.isCppScopeEnabled

            return if (isIndentEnabled || isScopeEnabled) {
                val colorHash = state.cppCurlyColors.hashCode().toLong()
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

        private fun compare(r: TextRange, h: RangeHighlighter): Int {
            val answer = r.startOffset - h.startOffset
            return if (answer != 0) answer else r.endOffset - h.endOffset
        }

        fun updateScopeHighlight(editor: Editor) {
            val state = getState(editor)
            val oldHighlighter = editor.getUserData(KEY_ACTIVE_SCOPE_HIGHLIGHTER)

            if (!CppSupportUtil.isCppSupportedEnvironment() || !state.isCppEnabled || !state.isCppScopeEnabled) {
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
                .minByOrNull { it.highlightRange.length }

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
                    if (CppSupportUtil.isCppSupportedEnvironment() && state.isCppEnabled && state.isCppIndentGuideEnabled) {
                        val currentGuide = IndentGuideRenderer.findActiveGuide(editor)
                        val lastGuide = editor.getUserData(IndentGuideRenderer.KEY_ACTIVE_INDENT_GUIDE)
                        if (currentGuide != lastGuide) {
                            editor.putUserData(IndentGuideRenderer.KEY_ACTIVE_INDENT_GUIDE, currentGuide)
                            editor.contentComponent.repaint()
                        }
                    }
                    if (CppSupportUtil.isCppSupportedEnvironment() && state.isCppEnabled && state.isCppScopeEnabled) {
                        updateScopeHighlight(editor)
                    }
                }
            }
            editor.caretModel.addCaretListener(listener, editor as? Disposable ?: Disposable {})
        }
    }
}
