package com.hxshijie.betterBrackets.core

import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * 括号匹配结果模型。
 */
data class BraceMatch(
    val depth: Int,
    val colors: List<String>
)

/**
 * 成对括号提取与嵌套深度计算接口。
 */
interface BracePairProvider {

    companion object {
        val braceSupportExtensionPoint: ExtensionPointName<BracePairProvider> =
            ExtensionPointName.create("com.hxshijie.betterBrackets.braceSupport")

        fun isLanguageSupported(languageId: String): Boolean {
            return braceSupportExtensionPoint.extensionList.any {
                it.languageId.equals(languageId, ignoreCase = true) && it.isEnvironmentSupported
            }
        }
    }

    val languageId: String get() = ""

    val isEnvironmentSupported: Boolean get() = true

    fun isSupported(file: PsiFile): Boolean

    fun isEnabled(state: BetterBracketsState): Boolean = true

    fun findBraceMatch(element: PsiElement, state: BetterBracketsState): BraceMatch? {
        val depth = findBraceDepth(element) ?: return null
        return BraceMatch(depth, emptyList())
    }

    fun findBraceDepth(element: PsiElement): Int? = null

    fun applyPreviewExtras(editor: Editor, psiFile: PsiFile, state: BetterBracketsState = BetterBracketsState.instance) {}
}
