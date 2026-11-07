package com.hxshijie.betterBrackets.support.css

import com.hxshijie.betterBrackets.core.BraceMatch
import com.hxshijie.betterBrackets.core.BracePairProvider
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.css.CssAtRule
import com.intellij.psi.css.CssBlock
import com.intellij.psi.css.CssElement
import com.intellij.psi.css.CssFile
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.tree.IElementType

/**
 * 括号类型枚举。
 */
enum class BracketType {
    PARENTHESES, // ()
    BRACKETS,    // []
    BRACES       // {}
}

/**
 * CSS 语言成对括号提取与深度计算提供器。
 */
class CssBraceSupport : BracePairProvider {

    override val languageId: String = "CSS"

    override fun isSupported(file: PsiFile): Boolean = file is CssFile

    override fun applyPreviewExtras(editor: Editor, psiFile: PsiFile, state: BetterBracketsState) {
        if (psiFile is CssFile) {
            CssIndentGuidePass.applyForPreview(editor, psiFile, state)
        }
    }

    override fun isEnabled(state: BetterBracketsState): Boolean = state.isCssEnabled

    override fun findBraceMatch(element: PsiElement, state: BetterBracketsState): BraceMatch? {
        val tokenType: IElementType = element.node?.elementType ?: return null
        val bracketType = getBracketType(tokenType) ?: return null

        if (!isBracketTypeEnabled(bracketType, state)) {
            return null
        }

        if (!isBracePairClosed(element, bracketType)) {
            return null
        }

        val depth = calculateAstDepth(element)
        val colors = getBracketColors(bracketType, state)
        return BraceMatch(depth, colors)
    }

    override fun findBraceDepth(element: PsiElement): Int? {
        val state = BetterBracketsState.instance
        return findBraceMatch(element, state)?.depth
    }

    companion object {

        private fun isBracketTypeEnabled(bracketType: BracketType, state: BetterBracketsState): Boolean {
            return when (bracketType) {
                BracketType.PARENTHESES -> state.isCssRoundEnabled
                BracketType.BRACKETS -> state.isCssSquareEnabled
                BracketType.BRACES -> state.isCssCurlyEnabled
            }
        }

        fun getBracketColors(bracketType: BracketType, state: BetterBracketsState): List<String> {
            return when (bracketType) {
                BracketType.PARENTHESES -> state.cssRoundColors
                BracketType.BRACKETS -> state.cssSquareColors
                BracketType.BRACES -> state.cssCurlyColors
            }
        }

        private fun getBracketType(tokenType: IElementType): BracketType? {
            return when (tokenType) {
                CssElementTypes.CSS_LPAREN, CssElementTypes.CSS_RPAREN -> BracketType.PARENTHESES
                CssElementTypes.CSS_LBRACKET, CssElementTypes.CSS_RBRACKET -> BracketType.BRACKETS
                CssElementTypes.CSS_LBRACE, CssElementTypes.CSS_RBRACE -> BracketType.BRACES
                else -> null
            }
        }

        /**
         * 检查当前括号所在的容器节点是否已闭合。
         */
        private fun isBracePairClosed(token: PsiElement, bracketType: BracketType): Boolean {
            val parent = token.parent ?: return false

            return when (bracketType) {
                BracketType.BRACES -> {
                    hasMatchingToken(parent, CssElementTypes.CSS_LBRACE) &&
                            hasMatchingToken(parent, CssElementTypes.CSS_RBRACE)
                }
                BracketType.PARENTHESES -> {
                    hasMatchingToken(parent, CssElementTypes.CSS_LPAREN) &&
                            hasMatchingToken(parent, CssElementTypes.CSS_RPAREN)
                }
                BracketType.BRACKETS -> {
                    hasMatchingToken(parent, CssElementTypes.CSS_LBRACKET) &&
                            hasMatchingToken(parent, CssElementTypes.CSS_RBRACKET)
                }
            }
        }

        private fun hasMatchingToken(parent: PsiElement, expectedTokenType: IElementType): Boolean {
            if (parent.node?.findChildByType(expectedTokenType) != null) {
                return true
            }
            var node: PsiElement? = parent.lastChild
            while (node != null) {
                if (node.node?.elementType == expectedTokenType) {
                    return true
                }
                if (node is CssElement) {
                    break
                }
                node = node.prevSibling
            }
            return false
        }

        /**
         * 回溯 PSI 树统计嵌套深度（从 0 开始）。
         */
        fun calculateAstDepth(element: PsiElement): Int {
            var depth = 0
            var current: PsiElement? = element.parent

            while (current != null && current !is CssFile) {
                if (isBraceContainer(current, element)) {
                    depth++
                }
                current = current.parent
            }

            return if (depth > 0) depth - 1 else 0
        }

        /**
         * 判断语法节点是否为括号容器节点。
         */
        private fun isBraceContainer(psi: PsiElement, element: PsiElement): Boolean {
            // 花括号容器
            if (psi is CssBlock) return true
            if (psi is CssAtRule) {
                val lBrace = psi.node?.findChildByType(CssElementTypes.CSS_LBRACE)
                if (lBrace != null) {
                    val rBrace = psi.node?.findChildByType(CssElementTypes.CSS_RBRACE)
                    return element.textRange.startOffset >= lBrace.startOffset &&
                            (rBrace == null || element.textRange.endOffset <= rBrace.startOffset + rBrace.textLength)
                }
                return false
            }

            // 圆括号容器
            if (psi.node?.findChildByType(CssElementTypes.CSS_LPAREN) != null) {
                return true
            }

            // 方括号容器
            if (psi.node?.findChildByType(CssElementTypes.CSS_LBRACKET) != null) {
                return true
            }

            return false
        }
    }
}
