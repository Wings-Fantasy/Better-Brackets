package com.hxshijie.betterBrackets.support.json

import com.hxshijie.betterBrackets.core.BraceMatch
import com.hxshijie.betterBrackets.core.BracePairProvider
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.intellij.json.JsonElementTypes
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

/**
 * JSON 括号类型枚举。
 */
enum class BracketType {
    SQUARE, // []
    CURLY   // {}
}

/**
 * JSON 语言成对括号提取与深度计算提供器。
 */
class JsonBraceSupport : BracePairProvider {

    override val languageId: String = "JSON"

    override fun isSupported(file: PsiFile): Boolean = file is JsonFile

    override fun applyPreviewExtras(editor: Editor, psiFile: PsiFile, state: BetterBracketsState) {
        if (psiFile is JsonFile) {
            JsonIndentGuidePass.applyForPreview(editor, psiFile, state)
        }
    }

    override fun isEnabled(state: BetterBracketsState): Boolean = state.isJsonEnabled

    override fun findBraceMatch(element: PsiElement, state: BetterBracketsState): BraceMatch? {
        if (!isEnabled(state)) return null

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
                BracketType.SQUARE -> state.isJsonSquareEnabled
                BracketType.CURLY -> state.isJsonCurlyEnabled
            }
        }

        fun getBracketColors(bracketType: BracketType, state: BetterBracketsState): List<String> {
            return when (bracketType) {
                BracketType.SQUARE -> state.jsonSquareColors
                BracketType.CURLY -> state.jsonCurlyColors
            }
        }

        private fun getBracketType(tokenType: IElementType): BracketType? {
            return when (tokenType) {
                JsonElementTypes.L_BRACKET, JsonElementTypes.R_BRACKET -> BracketType.SQUARE
                JsonElementTypes.L_CURLY, JsonElementTypes.R_CURLY -> BracketType.CURLY
                else -> null
            }
        }

        /**
         * 检查当前括号所在的容器节点是否已闭合。
         */
        private fun isBracePairClosed(token: PsiElement, bracketType: BracketType): Boolean {
            val parent = token.parent ?: return false
            return when (bracketType) {
                BracketType.CURLY -> {
                    hasMatchingToken(parent, JsonElementTypes.L_CURLY) &&
                            hasMatchingToken(parent, JsonElementTypes.R_CURLY)
                }
                BracketType.SQUARE -> {
                    hasMatchingToken(parent, JsonElementTypes.L_BRACKET) &&
                            hasMatchingToken(parent, JsonElementTypes.R_BRACKET)
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

            while (current != null && current !is JsonFile) {
                if (isBraceContainer(current)) {
                    depth++
                }
                current = current.parent
            }

            return if (depth > 0) depth - 1 else 0
        }

        /**
         * 判断语法节点是否为括号容器节点。
         */
        private fun isBraceContainer(psi: PsiElement): Boolean {
            if (psi is JsonObject || psi is JsonArray) return true
            val node = psi.node ?: return false
            return node.findChildByType(JsonElementTypes.L_CURLY) != null ||
                    node.findChildByType(JsonElementTypes.L_BRACKET) != null
        }
    }
}
