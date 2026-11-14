package com.hxshijie.betterBrackets.support.java

import com.hxshijie.betterBrackets.core.BraceMatch
import com.hxshijie.betterBrackets.core.BracePairProvider
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
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
 * Java 语言成对括号提取与深度计算提供器。
 */
class JavaBraceSupport : BracePairProvider {

    override val languageId: String = "JAVA"

    override fun isSupported(file: PsiFile): Boolean = file is PsiJavaFile

    override fun applyPreviewExtras(editor: Editor, psiFile: PsiFile, state: BetterBracketsState) {
        if (psiFile is PsiJavaFile) {
            JavaIndentGuidePass.applyForPreview(editor, psiFile, state)
        }
    }

    override fun isEnabled(state: BetterBracketsState): Boolean = state.isJavaEnabled

    override fun findBraceMatch(element: PsiElement, state: BetterBracketsState): BraceMatch? {
        if (!isEnabled(state)) return null
        if (element !is PsiJavaToken) return null

        val tokenType: IElementType = element.tokenType
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
                BracketType.PARENTHESES -> state.isJavaRoundEnabled
                BracketType.BRACKETS -> state.isJavaSquareEnabled
                BracketType.BRACES -> state.isJavaCurlyEnabled
                BracketType.ANGLE -> state.isJavaAngleEnabled
            }
        }

        fun getBracketColors(bracketType: BracketType, state: BetterBracketsState): List<String> {
            return when (bracketType) {
                BracketType.PARENTHESES -> state.javaRoundColors
                BracketType.BRACKETS -> state.javaSquareColors
                BracketType.BRACES -> state.javaCurlyColors
                BracketType.ANGLE -> state.javaAngleColors
            }
        }

        private fun getBracketType(element: PsiJavaToken, tokenType: IElementType): BracketType? {
            return when (tokenType) {
                JavaTokenType.LPARENTH, JavaTokenType.RPARENTH -> BracketType.PARENTHESES
                JavaTokenType.LBRACKET, JavaTokenType.RBRACKET -> BracketType.BRACKETS
                JavaTokenType.LBRACE, JavaTokenType.RBRACE -> BracketType.BRACES
                JavaTokenType.LT, JavaTokenType.GT -> {
                    if (isGenericAngleBracket(element)) BracketType.ANGLE else null
                }
                else -> null
            }
        }

        private fun isGenericAngleBracket(token: PsiJavaToken): Boolean {
            val parent = token.parent ?: return false
            return parent is PsiTypeParameterList ||
                    parent is PsiReferenceParameterList ||
                    parent is PsiTypeElement ||
                    parent is PsiTypeParameter
        }

        /**
         * 检查当前括号所在的容器节点是否已闭合。
         */
        private fun isBracePairClosed(token: PsiJavaToken, bracketType: BracketType): Boolean {
            val parent = token.parent ?: return false

            return when (bracketType) {
                BracketType.BRACES -> when (parent) {
                    is PsiCodeBlock -> parent.rBrace != null
                    is PsiClass -> parent.rBrace != null
                    is PsiSwitchBlock -> {
                        val body = parent.body
                        body?.rBrace != null
                    }
                    is PsiArrayInitializerExpression -> hasMatchingToken(parent, JavaTokenType.RBRACE)
                    is PsiArrayInitializerMemberValue -> hasMatchingToken(parent, JavaTokenType.RBRACE)
                    else -> hasMatchingToken(parent, JavaTokenType.RBRACE)
                }

                BracketType.PARENTHESES -> when (parent) {
                    is PsiIfStatement -> parent.rParenth != null
                    is PsiWhileStatement -> parent.rParenth != null
                    is PsiDoWhileStatement -> parent.rParenth != null
                    is PsiForStatement -> parent.rParenth != null
                    is PsiForeachStatement -> parent.rParenth != null
                    is PsiSwitchBlock -> parent.rParenth != null
                    is PsiCatchSection -> parent.rParenth != null
                    is PsiSynchronizedStatement -> parent.lockExpression != null && hasMatchingToken(parent, JavaTokenType.RPARENTH)
                    else -> hasMatchingToken(parent, JavaTokenType.RPARENTH)
                }

                BracketType.BRACKETS -> hasMatchingToken(parent, JavaTokenType.RBRACKET)
                BracketType.ANGLE -> hasMatchingToken(parent, JavaTokenType.GT)
            }
        }

        private fun hasMatchingToken(parent: PsiElement, expectedTokenType: IElementType): Boolean {
            val lastChild = parent.lastChild ?: return false
            if (lastChild is PsiJavaToken && lastChild.tokenType == expectedTokenType) {
                return true
            }

            var node: PsiElement? = lastChild
            while (node != null) {
                if (node is PsiJavaToken && node.tokenType == expectedTokenType) {
                    return true
                }
                if (node is PsiStatement || node is PsiCodeBlock) {
                    break
                }
                node = node.prevSibling
            }
            return false
        }

        /**
         * 回溯 PSI 树统计嵌套深度（从 0 开始）。
         */
        fun calculateAstDepth(element: PsiJavaToken): Int {
            var depth = 0
            var current: PsiElement? = element.parent

            while (current != null && current !is PsiJavaFile) {
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
        @Suppress("UnstableApiUsage")
        private fun isBraceContainer(psi: PsiElement, element: PsiJavaToken): Boolean {
            return when (psi) {
                // 花括号容器
                is PsiCodeBlock,
                is PsiArrayInitializerExpression,
                is PsiArrayInitializerMemberValue -> true

                // 类体花括号：仅当 element 处于类体花括号区域内时才计入容器
                is PsiClass -> {
                    val lBrace = psi.lBrace
                    if (lBrace != null) {
                        val rBrace = psi.rBrace
                        element.textRange.startOffset >= lBrace.textRange.startOffset &&
                                (rBrace == null || element.textRange.endOffset <= rBrace.textRange.endOffset)
                    } else {
                        false
                    }
                }

                // 圆括号容器
                is PsiParenthesizedExpression,
                is PsiDeconstructionList,
                is PsiExpressionList,
                is PsiParameterList,
                is PsiResourceList,
                is PsiRecordHeader,
                is PsiAnnotationParameterList -> true

                // 类型强转表达式：仅当 element 处于强转类型括号区域时计入
                is PsiTypeCastExpression -> {
                    val operand = psi.operand
                    operand == null || !PsiTreeUtil.isAncestor(operand, element, false)
                }

                // 控制流语句：仅当 element 处于条件/头部区域时计入
                is PsiIfStatement -> {
                    !PsiTreeUtil.isAncestor(psi.thenBranch, element, false) &&
                            !PsiTreeUtil.isAncestor(psi.elseBranch, element, false)
                }
                is PsiWhileStatement -> {
                    !PsiTreeUtil.isAncestor(psi.body, element, false)
                }
                is PsiDoWhileStatement -> {
                    !PsiTreeUtil.isAncestor(psi.body, element, false)
                }
                is PsiForStatement -> {
                    !PsiTreeUtil.isAncestor(psi.body, element, false)
                }
                is PsiForeachStatement -> {
                    !PsiTreeUtil.isAncestor(psi.body, element, false)
                }
                is PsiSwitchBlock -> {
                    val body = psi.body
                    body == null || !PsiTreeUtil.isAncestor(body, element, false)
                }
                is PsiCatchSection -> {
                    val catchBlock = psi.catchBlock
                    catchBlock == null || !PsiTreeUtil.isAncestor(catchBlock, element, false)
                }
                is PsiSynchronizedStatement -> {
                    val body = psi.body
                    body == null || !PsiTreeUtil.isAncestor(body, element, false)
                }

                // 方括号容器
                is PsiArrayAccessExpression -> true
                is PsiTypeElement -> psi.type is PsiArrayType
                is PsiNewExpression -> {
                    (element.tokenType == JavaTokenType.LBRACKET || element.tokenType == JavaTokenType.RBRACKET) &&
                            element.parent == psi
                }
                is PsiVariable -> {
                    (element.tokenType == JavaTokenType.LBRACKET || element.tokenType == JavaTokenType.RBRACKET) &&
                            element.parent == psi
                }

                // 泛型尖括号容器
                is PsiTypeParameterList,
                is PsiReferenceParameterList -> true

                else -> false
            }
        }
    }
}
