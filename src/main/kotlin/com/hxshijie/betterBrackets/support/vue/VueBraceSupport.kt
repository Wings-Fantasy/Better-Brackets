package com.hxshijie.betterBrackets.support.vue

import com.hxshijie.betterBrackets.core.BraceMatch
import com.hxshijie.betterBrackets.core.BracePairProvider
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.hxshijie.betterBrackets.support.css.CssBraceSupport
import com.hxshijie.betterBrackets.support.html.HtmlBraceSupport
import com.hxshijie.betterBrackets.support.js.JsBraceSupport
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlTag

/**
 * Vue 语言括号与模板支持提供器。
 */
class VueBraceSupport : BracePairProvider {

    private val htmlSupport = HtmlBraceSupport()
    private val jsSupport = JsBraceSupport()
    private val cssSupport = CssBraceSupport()

    override val languageId: String = "VUE"

    override fun isSupported(file: PsiFile): Boolean = isVueFile(file)

    override fun applyPreviewExtras(editor: Editor, psiFile: PsiFile, state: BetterBracketsState) {
        if (isVueFile(psiFile)) {
            VueIndentGuidePass.applyForPreview(editor, psiFile, state)
        }
    }

    override fun isEnabled(state: BetterBracketsState): Boolean = state.isVueEnabled

    override fun findBraceMatch(element: PsiElement, state: BetterBracketsState): BraceMatch? {
        if (isInterpolationToken(element)) {
            if (!state.isVueInterpolationEnabled) return null
            val color = state.vueInterpolationColor.ifBlank { "#B4960A" }
            return BraceMatch(0, listOf(color))
        }

        val match = htmlSupport.findBraceMatch(element, state)
        if (match != null) return match

        val jsMatch = jsSupport.findBraceMatch(element, state)
        if (jsMatch != null) {
            val offset = getTagDepthOffset(element)
            return BraceMatch(jsMatch.depth + offset, jsMatch.colors)
        }

        val cssMatch = cssSupport.findBraceMatch(element, state)
        if (cssMatch != null) {
            val offset = getTagDepthOffset(element)
            return BraceMatch(cssMatch.depth + offset, cssMatch.colors)
        }

        return null
    }

    override fun findBraceDepth(element: PsiElement): Int? {
        val state = BetterBracketsState.instance
        return findBraceMatch(element, state)?.depth
    }

    companion object {

        fun isVueFile(file: PsiFile): Boolean {
            return file.javaClass.name == "org.jetbrains.vuejs.lang.html.VueFile" ||
                    file.name.endsWith(".vue", ignoreCase = true) ||
                    file.fileType.name.equals("Vue", ignoreCase = true) ||
                    file.fileType.defaultExtension.equals("vue", ignoreCase = true)
        }

        /**
         * 计算嵌入代码元素所在的父级 XML/HTML 标签所带来的嵌套深度偏移。
         */
        fun getTagDepthOffset(element: PsiElement): Int {
            val tag = findEnclosingXmlTag(element) ?: return 1
            return HtmlBraceSupport.calculateAstDepth(tag) + 1
        }

        /**
         * 寻找当前元素所归属的外层 XmlTag。
         */
        fun findEnclosingXmlTag(element: PsiElement): XmlTag? {
            var current: PsiElement? = element.parent
            while (current != null && current !is PsiFile) {
                if (current is XmlTag) {
                    return current
                }
                current = current.parent
            }

            val host = InjectedLanguageManager.getInstance(element.project).getInjectionHost(element)
            if (host != null) {
                var hostCurrent: PsiElement? = host
                while (hostCurrent != null && hostCurrent !is PsiFile) {
                    if (hostCurrent is XmlTag) {
                        return hostCurrent
                    }
                    hostCurrent = hostCurrent.parent
                }
            }

            return null
        }

        private fun isInterpolationToken(element: PsiElement): Boolean {
            val tokenName = element.node?.elementType?.toString() ?: ""
            if (tokenName == "INTERPOLATION_START" || tokenName == "INTERPOLATION_END") {
                return true
            }
            val text = element.text
            return text == "{{" || text == "}}"
        }
    }
}
