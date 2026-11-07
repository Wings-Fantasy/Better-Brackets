package com.hxshijie.betterBrackets.support.cpp

import com.hxshijie.betterBrackets.core.BraceMatch
import com.hxshijie.betterBrackets.core.BracePairProvider
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

enum class CppBracketType {
    PARENTHESES,
    BRACKETS,
    BRACES,
    ANGLE
}

/**
 * C/C++ 语言括号匹配提供器。
 */
class CppBraceSupport : BracePairProvider {

    override val languageId: String = "CPP"
    override val isEnvironmentSupported: Boolean
        get() = CppSupportUtil.isCppSupportedEnvironment()

    override fun isSupported(file: PsiFile): Boolean {
        return CppSupportUtil.isCppSupportedEnvironment() && isCppFile(file)
    }

    override fun applyPreviewExtras(editor: Editor, psiFile: PsiFile, state: BetterBracketsState) {
        if (isSupported(psiFile)) {
            CppIndentGuidePass.applyForPreview(editor, psiFile, state)
        }
    }

    override fun isEnabled(state: BetterBracketsState): Boolean {
        return CppSupportUtil.isCppSupportedEnvironment() && state.isCppEnabled
    }

    override fun findBraceMatch(element: PsiElement, state: BetterBracketsState): BraceMatch? {
        if (!isEnabled(state)) return null

        val bracketType = getBracketType(element) ?: return null
        if (!isBracketTypeEnabled(bracketType, state)) return null

        val depth = calculateAstDepth(element, bracketType)
        val colors = getBracketColors(bracketType, state)
        return BraceMatch(depth, colors)
    }

    override fun findBraceDepth(element: PsiElement): Int? {
        val state = BetterBracketsState.instance
        return findBraceMatch(element, state)?.depth
    }

    companion object {

        fun isCppFile(file: PsiFile): Boolean {
            val fileName = file.name
            val langId = file.language.id
            val fileTypeName = file.fileType.name

            return langId.equals("ObjectiveC", ignoreCase = true) ||
                    langId.equals("C/C++", ignoreCase = true) ||
                    fileTypeName.equals("C/C++", ignoreCase = true) ||
                    fileTypeName.equals("ObjectiveC", ignoreCase = true) ||
                    fileName.endsWith(".cpp", ignoreCase = true) ||
                    fileName.endsWith(".c", ignoreCase = true) ||
                    fileName.endsWith(".h", ignoreCase = true) ||
                    fileName.endsWith(".hpp", ignoreCase = true) ||
                    fileName.endsWith(".cc", ignoreCase = true) ||
                    fileName.endsWith(".cxx", ignoreCase = true)
        }

        private fun isBracketTypeEnabled(bracketType: CppBracketType, state: BetterBracketsState): Boolean {
            return when (bracketType) {
                CppBracketType.PARENTHESES -> state.isCppRoundEnabled
                CppBracketType.BRACKETS -> state.isCppSquareEnabled
                CppBracketType.BRACES -> state.isCppCurlyEnabled
                CppBracketType.ANGLE -> state.isCppAngleEnabled
            }
        }

        fun getBracketColors(bracketType: CppBracketType, state: BetterBracketsState): List<String> {
            return when (bracketType) {
                CppBracketType.PARENTHESES -> state.cppRoundColors
                CppBracketType.BRACKETS -> state.cppSquareColors
                CppBracketType.BRACES -> state.cppCurlyColors
                CppBracketType.ANGLE -> state.cppAngleColors
            }
        }

        private fun getBracketType(element: PsiElement): CppBracketType? {
            return when (element.text) {
                "(", ")" -> CppBracketType.PARENTHESES
                "[", "]" -> CppBracketType.BRACKETS
                "{", "}" -> CppBracketType.BRACES
                "<", ">" -> if (isGenericAngleBracket(element)) CppBracketType.ANGLE else null
                else -> null
            }
        }

        private fun isGenericAngleBracket(token: PsiElement): Boolean {
            val parent = token.parent ?: return false
            val parentName = parent.javaClass.simpleName
            val parentType = parent.node?.elementType?.toString() ?: ""

            return parentName.contains("Template", ignoreCase = true) ||
                    parentName.contains("TypeArgument", ignoreCase = true) ||
                    parentType.contains("TEMPLATE", ignoreCase = true)
        }

        /**
         * 计算指定括号类型的嵌套深度。
         */
        fun calculateAstDepth(element: PsiElement, bracketType: CppBracketType): Int {
            var depth = 0
            var current: PsiElement? = element.parent

            while (current != null && current !is PsiFile) {
                val className = current.javaClass.simpleName
                val nodeType = current.node?.elementType?.toString() ?: ""

                val matches = when (bracketType) {
                    CppBracketType.PARENTHESES ->
                        className.contains("Paren", ignoreCase = true) ||
                                nodeType.contains("PAREN", ignoreCase = true) ||
                                current.children.any { it.text == "(" }

                    CppBracketType.BRACKETS ->
                        className.contains("Bracket", ignoreCase = true) ||
                                nodeType.contains("BRACKET", ignoreCase = true) ||
                                current.children.any { it.text == "[" }

                    CppBracketType.BRACES ->
                        !CppSupportUtil.isStatementOrDefinitionContainer(className, nodeType) &&
                                (className.contains("Block", ignoreCase = true) ||
                                        className.contains("Compound", ignoreCase = true) ||
                                        nodeType.contains("BLOCK", ignoreCase = true) ||
                                        current.children.any { it.text == "{" })

                    CppBracketType.ANGLE ->
                        className.contains("Template", ignoreCase = true) ||
                                nodeType.contains("TEMPLATE", ignoreCase = true)
                }

                if (matches) {
                    depth++
                }
                current = current.parent
            }

            return if (depth > 0) depth - 1 else 0
        }
    }
}
