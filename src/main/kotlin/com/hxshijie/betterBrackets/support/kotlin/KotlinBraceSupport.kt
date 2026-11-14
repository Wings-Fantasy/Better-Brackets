package com.hxshijie.betterBrackets.support.kotlin

import com.hxshijie.betterBrackets.core.BraceMatch
import com.hxshijie.betterBrackets.core.BracePairProvider
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

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
 * Kotlin 语言成对括号提取与深度计算提供器。
 */
class KotlinBraceSupport : BracePairProvider {

    override val languageId: String = "KOTLIN"

    override fun isSupported(file: PsiFile): Boolean = file is KtFile

    override fun applyPreviewExtras(editor: Editor, psiFile: PsiFile, state: BetterBracketsState) {
        if (psiFile is KtFile) {
            KotlinIndentGuidePass.applyForPreview(editor, psiFile, state)
        }
    }

    override fun isEnabled(state: BetterBracketsState): Boolean = state.isKotlinEnabled

    override fun findBraceMatch(element: PsiElement, state: BetterBracketsState): BraceMatch? {
        if (!isEnabled(state)) return null

        val tokenType: IElementType = element.node?.elementType ?: return null
        val bracketType = getBracketType(element, tokenType) ?: return null

        if (!isBracketTypeEnabled(bracketType, state)) {
            return null
        }

        if (!isBracePairClosed(element, bracketType, tokenType)) {
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
                BracketType.PARENTHESES -> state.isKotlinRoundEnabled
                BracketType.BRACKETS -> state.isKotlinSquareEnabled
                BracketType.BRACES -> state.isKotlinCurlyEnabled
                BracketType.ANGLE -> state.isKotlinAngleEnabled
            }
        }

        fun getBracketColors(bracketType: BracketType, state: BetterBracketsState): List<String> {
            return when (bracketType) {
                BracketType.PARENTHESES -> state.kotlinRoundColors
                BracketType.BRACKETS -> state.kotlinSquareColors
                BracketType.BRACES -> state.kotlinCurlyColors
                BracketType.ANGLE -> state.kotlinAngleColors
            }
        }

        private fun getBracketType(element: PsiElement, tokenType: IElementType): BracketType? {
            return when (tokenType) {
                KtTokens.LPAR, KtTokens.RPAR -> BracketType.PARENTHESES
                KtTokens.LBRACKET, KtTokens.RBRACKET -> BracketType.BRACKETS
                KtTokens.LBRACE, KtTokens.RBRACE -> BracketType.BRACES
                KtTokens.LONG_TEMPLATE_ENTRY_START, KtTokens.LONG_TEMPLATE_ENTRY_END -> BracketType.BRACES
                KtTokens.LT, KtTokens.GT -> {
                    if (isGenericAngleBracket(element)) BracketType.ANGLE else null
                }
                else -> null
            }
        }

        private fun isGenericAngleBracket(token: PsiElement): Boolean {
            val parent = token.parent ?: return false
            return parent is KtTypeParameterList ||
                    parent is KtTypeArgumentList ||
                    parent is KtTypeParameter ||
                    parent is KtTypeProjection
        }

        /**
         * 检查当前括号所在的容器节点是否已闭合。
         */
        private fun isBracePairClosed(token: PsiElement, bracketType: BracketType, tokenType: IElementType): Boolean {
            val parent = token.parent ?: return false

            return when (bracketType) {
                BracketType.BRACES -> {
                    if (tokenType == KtTokens.LONG_TEMPLATE_ENTRY_START || tokenType == KtTokens.LONG_TEMPLATE_ENTRY_END) {
                        hasMatchingToken(parent, KtTokens.LONG_TEMPLATE_ENTRY_END)
                    } else {
                        when (parent) {
                            is KtBlockExpression -> parent.rBrace != null
                            is KtClassBody -> parent.rBrace != null
                            is KtFunctionLiteral -> parent.rBrace != null
                            is KtWhenExpression -> parent.closeBrace != null
                            else -> hasMatchingToken(parent, KtTokens.RBRACE)
                        }
                    }
                }

                BracketType.PARENTHESES -> when (parent) {
                    is KtValueArgumentList -> parent.rightParenthesis != null
                    is KtParameterList -> parent.rightParenthesis != null
                    is KtIfExpression -> parent.rightParenthesis != null
                    is KtWhenExpression -> parent.rightParenthesis != null
                    is KtCatchClause -> parent.parameterList?.rightParenthesis != null
                    else -> hasMatchingToken(parent, KtTokens.RPAR)
                }

                BracketType.BRACKETS -> when (parent) {
                    is KtArrayAccessExpression -> parent.rightBracket != null
                    is KtCollectionLiteralExpression -> parent.rightBracket != null
                    else -> hasMatchingToken(parent, KtTokens.RBRACKET)
                }

                BracketType.ANGLE -> hasMatchingToken(parent, KtTokens.GT)
            }
        }

        private fun hasMatchingToken(parent: PsiElement, expectedTokenType: IElementType): Boolean {
            val lastChild = parent.lastChild ?: return false
            if (lastChild.node?.elementType == expectedTokenType) {
                return true
            }

            var node: PsiElement? = lastChild
            while (node != null) {
                if (node.node?.elementType == expectedTokenType) {
                    return true
                }
                if (node is KtExpression) {
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

            while (current != null && current !is KtFile) {
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
                is KtBlockExpression,
                is KtClassBody,
                is KtFunctionLiteral,
                is KtBlockStringTemplateEntry -> true

                // When 表达式
                is KtWhenExpression -> {
                    val openBrace = psi.openBrace
                    val closeBrace = psi.closeBrace
                    if (openBrace != null && closeBrace != null) {
                        element.textRange.startOffset >= openBrace.textRange.startOffset &&
                                element.textRange.endOffset <= closeBrace.textRange.endOffset
                    } else {
                        val subject = psi.subjectExpression
                        subject == null || !PsiTreeUtil.isAncestor(subject, element, false)
                    }
                }

                // 圆括号容器
                is KtParenthesizedExpression,
                is KtValueArgumentList,
                is KtParameterList,
                is KtDestructuringDeclaration,
                is KtContextReceiverList,
                is KtContextParameterList -> true

                // 控制流语句圆括号区域
                is KtIfExpression -> {
                    val thenBranch = psi.then
                    val elseBranch = psi.`else`
                    (thenBranch == null || !PsiTreeUtil.isAncestor(thenBranch, element, false)) &&
                            (elseBranch == null || !PsiTreeUtil.isAncestor(elseBranch, element, false))
                }
                is KtWhileExpressionBase -> {
                    val body = psi.body
                    body == null || !PsiTreeUtil.isAncestor(body, element, false)
                }
                is KtForExpression -> {
                    val body = psi.body
                    body == null || !PsiTreeUtil.isAncestor(body, element, false)
                }
                is KtCatchClause -> {
                    val catchBody = psi.catchBody
                    catchBody == null || !PsiTreeUtil.isAncestor(catchBody, element, false)
                }

                // 方括号容器
                is KtArrayAccessExpression,
                is KtCollectionLiteralExpression -> true

                // 泛型尖括号容器
                is KtTypeParameterList,
                is KtTypeArgumentList -> true

                else -> false
            }
        }
    }
}
