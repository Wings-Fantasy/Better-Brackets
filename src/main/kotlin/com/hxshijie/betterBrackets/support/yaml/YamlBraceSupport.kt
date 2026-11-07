package com.hxshijie.betterBrackets.support.yaml

import com.hxshijie.betterBrackets.core.BraceMatch
import com.hxshijie.betterBrackets.core.BracePairProvider
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.psi.YAMLFile

/**
 * YAML 括号类型枚举。
 */
enum class BracketType {
    SQUARE, // []
    CURLY   // {}
}

/**
 * YAML 语言成对括号提取与深度计算提供器。
 */
class YamlBraceSupport : BracePairProvider {

    override val languageId: String = "YAML"

    override fun isSupported(file: PsiFile): Boolean = file is YAMLFile

    override fun applyPreviewExtras(editor: Editor, psiFile: PsiFile, state: BetterBracketsState) {
        if (psiFile is YAMLFile) {
            YamlIndentGuidePass.applyForPreview(editor, psiFile, state)
        }
    }

    override fun isEnabled(state: BetterBracketsState): Boolean = state.isYamlEnabled

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
                BracketType.SQUARE -> state.isYamlSquareEnabled
                BracketType.CURLY -> state.isYamlCurlyEnabled
            }
        }

        fun getBracketColors(bracketType: BracketType, state: BetterBracketsState): List<String> {
            return when (bracketType) {
                BracketType.SQUARE -> state.yamlSquareColors
                BracketType.CURLY -> state.yamlCurlyColors
            }
        }

        private fun getBracketType(tokenType: IElementType): BracketType? {
            return when (tokenType) {
                YAMLTokenTypes.LBRACKET, YAMLTokenTypes.RBRACKET -> BracketType.SQUARE
                YAMLTokenTypes.LBRACE, YAMLTokenTypes.RBRACE -> BracketType.CURLY
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
                    hasMatchingToken(parent, YAMLTokenTypes.LBRACE) &&
                            hasMatchingToken(parent, YAMLTokenTypes.RBRACE)
                }
                BracketType.SQUARE -> {
                    hasMatchingToken(parent, YAMLTokenTypes.LBRACKET) &&
                            hasMatchingToken(parent, YAMLTokenTypes.RBRACKET)
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

            while (current != null && current !is YAMLFile) {
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
            val node = psi.node ?: return false
            return node.findChildByType(YAMLTokenTypes.LBRACE) != null ||
                    node.findChildByType(YAMLTokenTypes.LBRACKET) != null
        }
    }
}
