package com.hxshijie.betterBrackets.support.js

import com.hxshijie.betterBrackets.core.BraceMatch
import com.hxshijie.betterBrackets.core.BracePairProvider
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeArgumentList
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAssertion
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeParameterList
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil

/**
 * 括号类型枚举。
 */
enum class BracketType {
    PARENTHESES, // ()
    BRACKETS,    // []
    BRACES,      // {}
    ANGLE        // <>
}

/**
 * JavaScript / TypeScript 语言成对括号提取与深度计算提供器。
 */
class JsBraceSupport : BracePairProvider {

    override val languageId: String = "JS"

    override fun isSupported(file: PsiFile): Boolean = file is JSFile

    override fun applyPreviewExtras(editor: Editor, psiFile: PsiFile, state: BetterBracketsState) {
        if (psiFile is JSFile) {
            JsIndentGuidePass.applyForPreview(editor, psiFile, state)
        }
    }

    override fun isEnabled(state: BetterBracketsState): Boolean = state.isJsEnabled

    override fun findBraceMatch(element: PsiElement, state: BetterBracketsState): BraceMatch? {
        if (!isEnabled(state)) return null

        val tokenType: IElementType = element.node?.elementType ?: return null
        val bracketType = getBracketType(element, tokenType) ?: return null

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
                BracketType.PARENTHESES -> state.isJsRoundEnabled
                BracketType.BRACKETS -> state.isJsSquareEnabled
                BracketType.BRACES -> state.isJsCurlyEnabled
                BracketType.ANGLE -> state.isJsAngleEnabled
            }
        }

        fun getBracketColors(bracketType: BracketType, state: BetterBracketsState): List<String> {
            return when (bracketType) {
                BracketType.PARENTHESES -> state.jsRoundColors
                BracketType.BRACKETS -> state.jsSquareColors
                BracketType.BRACES -> state.jsCurlyColors
                BracketType.ANGLE -> state.jsAngleColors
            }
        }

        private fun getBracketType(element: PsiElement, tokenType: IElementType): BracketType? {
            return when (tokenType) {
                JSTokenTypes.LPAR, JSTokenTypes.RPAR -> BracketType.PARENTHESES
                JSTokenTypes.LBRACKET, JSTokenTypes.RBRACKET -> BracketType.BRACKETS
                JSTokenTypes.LBRACE, JSTokenTypes.RBRACE -> BracketType.BRACES
                JSTokenTypes.LT, JSTokenTypes.GT -> {
                    if (isGenericAngleBracket(element)) BracketType.ANGLE else null
                }
                else -> null
            }
        }

        private fun isGenericAngleBracket(token: PsiElement): Boolean {
            val parent = token.parent ?: return false
            return parent is TypeScriptTypeArgumentList ||
                    parent is TypeScriptTypeParameterList ||
                    parent is TypeScriptTypeAssertion
        }

        /**
         * 检查当前括号所在的容器节点是否已闭合。
         */
        private fun isBracePairClosed(token: PsiElement, bracketType: BracketType): Boolean {
            val parent = token.parent ?: return false

            return when (bracketType) {
                BracketType.BRACES -> hasMatchingToken(parent, JSTokenTypes.RBRACE)
                BracketType.PARENTHESES -> hasMatchingToken(parent, JSTokenTypes.RPAR)
                BracketType.BRACKETS -> hasMatchingToken(parent, JSTokenTypes.RBRACKET)
                BracketType.ANGLE -> hasMatchingToken(parent, JSTokenTypes.GT)
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
                if (node is JSStatement) {
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

            while (current != null && current !is JSFile) {
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
            return when (psi) {
                // 花括号容器
                is JSBlockStatement,
                is JSObjectLiteralExpression -> true

                // 类体花括号：仅当 element 处于类体花括号区域内时才计入容器
                is JSClass -> {
                    val lBrace = psi.node.findChildByType(JSTokenTypes.LBRACE)?.psi
                    if (lBrace != null) {
                        val rBrace = psi.node.findChildByType(JSTokenTypes.RBRACE)?.psi
                        element.textRange.startOffset >= lBrace.textRange.startOffset &&
                                (rBrace == null || element.textRange.endOffset <= rBrace.textRange.endOffset)
                    } else {
                        false
                    }
                }

                // 圆括号容器
                is JSParenthesizedExpression,
                is JSParameterList,
                is JSArgumentList -> true

                // 控制流语句圆括号区域（仅当 element 处于条件/头部区域时计入）
                is JSIfStatement -> {
                    val thenBranch = psi.thenBranch
                    val elseBranch = psi.elseBranch
                    (thenBranch == null || !PsiTreeUtil.isAncestor(thenBranch, element, false)) &&
                            (elseBranch == null || !PsiTreeUtil.isAncestor(elseBranch, element, false))
                }
                is JSLoopStatement -> {
                    val body = psi.body
                    body == null || !PsiTreeUtil.isAncestor(body, element, false)
                }
                is JSSwitchStatement -> true
                is JSCatchBlock -> {
                    val stmt = psi.statement
                    stmt == null || !PsiTreeUtil.isAncestor(stmt, element, false)
                }
                is JSWithStatement -> {
                    val stmt = psi.statement
                    stmt == null || !PsiTreeUtil.isAncestor(stmt, element, false)
                }

                // 方括号容器
                is JSArrayLiteralExpression,
                is JSIndexedPropertyAccessExpression -> true

                // 泛型尖括号容器
                is TypeScriptTypeArgumentList,
                is TypeScriptTypeParameterList,
                is TypeScriptTypeAssertion -> true

                else -> false
            }
        }
    }
}
