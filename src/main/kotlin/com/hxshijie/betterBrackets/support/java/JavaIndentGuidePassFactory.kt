package com.hxshijie.betterBrackets.support.java

import com.hxshijie.betterBrackets.core.BetterBracketsFileUtil
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.intellij.codeHighlighting.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile

/**
 * Java 语言代码分析高亮 Pass 工厂。
 */
class JavaIndentGuidePassFactory :
    TextEditorHighlightingPassFactoryRegistrar,
    TextEditorHighlightingPassFactory,
    DumbAware {

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        if (!BetterBracketsFileUtil.isHighlightingAllowed(file)) return null
        if (file !is PsiJavaFile) return null
        val state = BetterBracketsState.instance
        if (!state.isJavaEnabled || (!state.isJavaIndentGuideEnabled && !state.isJavaScopeEnabled)) return null
        return JavaIndentGuidePass(file.project, editor, file)
    }

    override fun registerHighlightingPassFactory(
        registrar: TextEditorHighlightingPassRegistrar,
        project: Project
    ) {
        registrar.registerTextEditorHighlightingPass(
            this,
            TextEditorHighlightingPassRegistrar.Anchor.AFTER,
            Pass.UPDATE_ALL,
            false,
            false
        )
    }
}
