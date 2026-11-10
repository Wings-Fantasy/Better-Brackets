package com.hxshijie.betterBrackets.support.json

import com.hxshijie.betterBrackets.core.ColorCalculator
import com.hxshijie.betterBrackets.core.highlighter.IndentGuideRenderer
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.hxshijie.betterBrackets.settings.configurable.ui.BracketPreviewEditor
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.json.JsonElementTypes
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.util.DocumentUtil
import java.awt.Color
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/**
 * JSON 语言代码分析高亮 Pass（包含垂直缩进引导线与当前作用域高亮）。
 */
class JsonIndentGuidePass(
    project: Project,
    editor: Editor,
    private val psiFile: JsonFile
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
        val range: TextRange,
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
            Key.create<MutableList<RangeHighlighter>>("BETTER_BRACKETS_JSON_INDENT_HIGHLIGHTERS_KEY")
        private val LAST_TIME_INDENTS_BUILT =
            Key.create<Long>("BETTER_BRACKETS_JSON_LAST_TIME_INDENTS_BUILT")
        private val KEY_CARET_LISTENER_ATTACHED =
            Key.create<Boolean>("BETTER_BRACKETS_JSON_CARET_LISTENER_ATTACHED")

        val SCOPE_INFOS_KEY: Key<List<ScopeInfo>> =
            Key.create("BETTER_BRACKETS_JSON_SCOPE_INFOS_KEY")
        val KEY_ACTIVE_SCOPE_HIGHLIGHTER: Key<RangeHighlighter> =
            Key.create("BETTER_BRACKETS_JSON_ACTIVE_SCOPE_HIGHLIGHTER")
        val KEY_ACTIVE_SCOPE_RANGE: Key<TextRange> =
            Key.create("BETTER_BRACKETS_JSON_ACTIVE_SCOPE_RANGE")

        fun getState(editor: Editor): BetterBracketsState {
            return editor.getUserData(BracketPreviewEditor.STATE_PROVIDER_KEY)?.invoke() ?: BetterBracketsState.instance
        }

        /**
         * 供预览编辑器直接调用
         */
        fun applyForPreview(
            editor: Editor,
            psiFile: JsonFile,
            state: BetterBracketsState = getState(editor)
        ) {
            val editorEx = editor as? EditorEx ?: return
            editorEx.putUserData(LAST_TIME_INDENTS_BUILT, null)
            val result = collectInformation(editorEx, psiFile, null, state)
            applyInformationToEditor(editorEx, result)
        }

        fun collectInformation(
            editor: EditorEx,
            psiFile: JsonFile,
            progress: ProgressIndicator? = null,
            state: BetterBracketsState = getState(editor),
            stamp: Long = nowStamp(editor)
        ): CollectResult {
            val isIndentEnabled = state.isJsonEnabled && state.isJsonIndentGuideEnabled
            val isScopeEnabled = state.isJsonEnabled && state.isJsonScopeEnabled

            if (!isIndentEnabled && !isScopeEnabled) {
                return CollectResult(emptyList(), emptyList(), emptyList(), stamp)
            }

            val curlyColors = state.jsonCurlyColors
            val squareColors = state.jsonSquareColors
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

                    val node = element.node
                    if (node != null) {
                        // 1. 处理 JSON 对象 (Curly braces: {})
                        if (element is JsonObject) {
                            val lBrace = node.findChildByType(JsonElementTypes.L_CURLY)
                            val rBrace = node.findChildByType(JsonElementTypes.R_CURLY)
                            if (lBrace != null && rBrace != null) {
                                val startOffset = lBrace.startOffset
                                val endOffset = rBrace.startOffset + rBrace.textLength
                                val startLine = document.getLineNumber(startOffset)
                                val endLine = document.getLineNumber(rBrace.startOffset)
                                val depth = JsonBraceSupport.calculateAstDepth(lBrace.psi)
                                val color = getColorForDepth(depth, curlyColors)

                                if (isScopeEnabled && startOffset < endOffset) {
                                    val range = TextRange(startOffset, endOffset)
                                    if (seenScopes.add(Pair(range.startOffset, range.endOffset))) {
                                        scopeInfos.add(ScopeInfo(range, color))
                                    }
                                }

                                if (isIndentEnabled && startLine + 1 < endLine) {
                                    val declarationLine = findDeclarationLine(element, document)
                                    val hostIndent = getLineIndentColumn(document, declarationLine, tabSize)
                                    val lBraceIndent = getLineIndentColumn(document, startLine, tabSize)
                                    val indentColumn = minOf(hostIndent, lBraceIndent)

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

                        // 2. 处理 JSON 数组 (Square brackets: [])
                        if (element is JsonArray) {
                            val lBracket = node.findChildByType(JsonElementTypes.L_BRACKET)
                            val rBracket = node.findChildByType(JsonElementTypes.R_BRACKET)
                            if (lBracket != null && rBracket != null) {
                                val startOffset = lBracket.startOffset
                                val endOffset = rBracket.startOffset + rBracket.textLength
                                val startLine = document.getLineNumber(startOffset)
                                val endLine = document.getLineNumber(rBracket.startOffset)
                                val depth = JsonBraceSupport.calculateAstDepth(lBracket.psi)
                                val color = getColorForDepth(depth, squareColors)

                                if (isScopeEnabled && startOffset < endOffset) {
                                    val range = TextRange(startOffset, endOffset)
                                    if (seenScopes.add(Pair(range.startOffset, range.endOffset))) {
                                        scopeInfos.add(ScopeInfo(range, color))
                                    }
                                }

                                if (isIndentEnabled && startLine + 1 < endLine) {
                                    val declarationLine = findDeclarationLine(element, document)
                                    val hostIndent = getLineIndentColumn(document, declarationLine, tabSize)
                                    val lBracketIndent = getLineIndentColumn(document, startLine, tabSize)
                                    val indentColumn = minOf(hostIndent, lBracketIndent)

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

        private fun findDeclarationLine(element: PsiElement, document: Document): Int {
            val parent = element.parent
            return if (parent is JsonProperty) {
                document.getLineNumber(parent.textOffset)
            } else {
                document.getLineNumber(element.textOffset)
            }
        }

        private fun getColorForDepth(depth: Int, colors: List<String>): Color {
            return if (colors.isNotEmpty()) {
                val hex = colors[if (depth >= 0) depth % colors.size else 0]
                ColorCalculator.parseHexColor(hex)
            } else {
                Color(0x3F, 0x91, 0x01)
            }
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
            val isIndentEnabled = state.isJsonEnabled && state.isJsonIndentGuideEnabled
            val isScopeEnabled = state.isJsonEnabled && state.isJsonScopeEnabled

            return if (isIndentEnabled || isScopeEnabled) {
                val colorHash = (state.jsonCurlyColors.hashCode().toLong() shl 16) xor state.jsonSquareColors.hashCode().toLong()
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

            if (!state.isJsonEnabled || !state.isJsonScopeEnabled) {
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
                .filter { caretOffset in it.range.startOffset..it.range.endOffset }
                .minByOrNull { it.range.length }

            val lastRange = editor.getUserData(KEY_ACTIVE_SCOPE_RANGE)
            val newRange = activeScope?.range

            if (newRange != lastRange || oldHighlighter == null || !oldHighlighter.isValid) {
                oldHighlighter?.dispose()
                editor.putUserData(KEY_ACTIVE_SCOPE_RANGE, newRange)

                if (activeScope != null && activeScope.range.endOffset <= editor.document.textLength) {
                    val attributes = TextAttributes().apply {
                        effectType = EffectType.ROUNDED_BOX
                        effectColor = activeScope.color
                    }
                    val newHighlighter = editor.markupModel.addRangeHighlighter(
                        activeScope.range.startOffset,
                        activeScope.range.endOffset,
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
                override fun caretPositionChanged(event: CaretEvent) {
                    val ed = event.editor
                    updateScopeHighlight(ed)

                    val activeGuide = IndentGuideRenderer.findActiveGuide(ed)
                    val oldGuide = ed.getUserData(IndentGuideRenderer.KEY_ACTIVE_INDENT_GUIDE)
                    if (activeGuide != oldGuide) {
                        ed.putUserData(IndentGuideRenderer.KEY_ACTIVE_INDENT_GUIDE, activeGuide)
                        ed.contentComponent.repaint()
                    }
                }
            }
            editor.caretModel.addCaretListener(listener)

            if (editor is Disposable) {
                Disposer.register(editor) {
                    editor.caretModel.removeCaretListener(listener)
                    editor.putUserData(KEY_CARET_LISTENER_ATTACHED, null)
                }
            }
        }
    }
}
