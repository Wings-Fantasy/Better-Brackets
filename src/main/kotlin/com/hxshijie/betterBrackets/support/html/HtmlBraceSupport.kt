package com.hxshijie.betterBrackets.support.html

import com.hxshijie.betterBrackets.core.BraceMatch
import com.hxshijie.betterBrackets.core.BracePairProvider
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.hxshijie.betterBrackets.support.vue.VueBraceSupport
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.util.XmlTagUtil

/**
 * HTML/XML 语言标签与括号支持提供器。
 */
class HtmlBraceSupport : BracePairProvider {

    override val languageId: String = "HTML"

    override fun isSupported(file: PsiFile): Boolean = file is XmlFile && !VueBraceSupport.isVueFile(file)

    override fun applyPreviewExtras(editor: Editor, psiFile: PsiFile, state: BetterBracketsState) {
        if (psiFile is XmlFile && !VueBraceSupport.isVueFile(psiFile)) {
            HtmlIndentGuidePass.applyForPreview(editor, psiFile, state)
        }
    }

    override fun isEnabled(state: BetterBracketsState): Boolean = state.isHtmlEnabled

    override fun findBraceMatch(element: PsiElement, state: BetterBracketsState): BraceMatch? {
        if (!state.isHtmlTagTextEnabled) return null

        val tag = element.parent as? XmlTag
            ?: element.parent?.parent as? XmlTag
            ?: return null

        val tagRanges = extractTagRanges(tag)
        val elementRange = element.textRange ?: return null
        val isMatched = tagRanges.any { it == elementRange || it.contains(elementRange) || elementRange.contains(it) }
        if (!isMatched) return null

        val depth = calculateAstDepth(tag)
        val colors = state.htmlTagColors
        return BraceMatch(depth, colors)
    }

    override fun findBraceDepth(element: PsiElement): Int? {
        val state = BetterBracketsState.instance
        return findBraceMatch(element, state)?.depth
    }

    companion object {

        /**
         * 提取 XML/HTML/Vue 标签中需要高亮的所有区间（包含起始/结束尖括号及标签名）。
         */
        fun extractTagRanges(tag: XmlTag): List<TextRange> {
            val ranges = ArrayList<TextRange>(6)
            val document = tag.containingFile?.viewProvider?.document
            val charsSequence = document?.charsSequence ?: tag.containingFile?.text ?: return ranges
            val textLength = charsSequence.length

            // 1. 开始标签处理
            val startTagRange = XmlTagUtil.getStartTagRange(tag)
            if (startTagRange != null && startTagRange.startOffset in 0..textLength && startTagRange.endOffset in 0..textLength) {
                val startOffset = startTagRange.startOffset
                val endOffset = startTagRange.endOffset
                val tagText = charsSequence.subSequence(startOffset, endOffset)

                // 起始尖括号 '<'
                if (tagText.startsWith("<")) {
                    ranges.add(TextRange(startOffset, startOffset + 1))
                }

                // 起始标签名
                var nameRange = XmlTagUtil.getStartTagNameElement(tag)?.textRange
                if (nameRange == null || nameRange.startOffset < startOffset || nameRange.endOffset > endOffset) {
                    val tagName = tag.name
                    if (tagName.isNotEmpty()) {
                        var idx = 1
                        while (idx < tagText.length && tagText[idx].isWhitespace()) {
                            idx++
                        }
                        if (idx + tagName.length <= tagText.length) {
                            val sub = tagText.subSequence(idx, idx + tagName.length).toString()
                            if (sub.equals(tagName, ignoreCase = true)) {
                                nameRange = TextRange(startOffset + idx, startOffset + idx + tagName.length)
                            }
                        }
                    }
                }
                if (nameRange != null && nameRange.startOffset in 0..textLength && nameRange.endOffset in 0..textLength) {
                    ranges.add(nameRange)
                }

                // 起始标签闭合尖括号 '>' 或 '/>'
                if (tagText.endsWith("/>")) {
                    ranges.add(TextRange(endOffset - 2, endOffset))
                } else if (tagText.endsWith(">")) {
                    ranges.add(TextRange(endOffset - 1, endOffset))
                }
            }

            // 2. 结束标签处理（仅非自闭合标签）
            val endTagRange = XmlTagUtil.getEndTagRange(tag)
            if (endTagRange != null && endTagRange.startOffset in 0..textLength && endTagRange.endOffset in 0..textLength) {
                val startOffset = endTagRange.startOffset
                val endOffset = endTagRange.endOffset
                val tagText = charsSequence.subSequence(startOffset, endOffset)

                // 结束标签起手 '</'
                if (tagText.startsWith("</")) {
                    ranges.add(TextRange(startOffset, startOffset + 2))
                }

                // 结束标签名
                var nameRange = XmlTagUtil.getEndTagNameElement(tag)?.textRange
                if (nameRange == null || nameRange.startOffset < startOffset || nameRange.endOffset > endOffset) {
                    val tagName = tag.name
                    if (tagName.isNotEmpty()) {
                        var idx = 2
                        while (idx < tagText.length && tagText[idx].isWhitespace()) {
                            idx++
                        }
                        if (idx + tagName.length <= tagText.length) {
                            val sub = tagText.subSequence(idx, idx + tagName.length).toString()
                            if (sub.equals(tagName, ignoreCase = true)) {
                                nameRange = TextRange(startOffset + idx, startOffset + idx + tagName.length)
                            }
                        }
                    }
                }
                if (nameRange != null && nameRange.startOffset in 0..textLength && nameRange.endOffset in 0..textLength) {
                    ranges.add(nameRange)
                }

                // 结束标签末尾 '>'
                if (tagText.endsWith(">")) {
                    ranges.add(TextRange(endOffset - 1, endOffset))
                }
            }

            return ranges
        }

        /**
         * 回溯 AST 统计当前标签的嵌套深度（根标签深度为 0）。
         */
        fun calculateAstDepth(tag: XmlTag): Int {
            var depth = 0
            var current: PsiElement? = tag.parent

            while (current != null && current !is XmlFile) {
                if (current is XmlTag) {
                    depth++
                }
                current = current.parent
            }

            return depth
        }
    }
}
