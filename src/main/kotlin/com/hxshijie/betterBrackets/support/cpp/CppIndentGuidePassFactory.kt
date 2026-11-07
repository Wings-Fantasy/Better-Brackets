package com.hxshijie.betterBrackets.support.cpp

import com.hxshijie.betterBrackets.core.BetterBracketsFileUtil
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.intellij.codeHighlighting.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * C/C++ 高亮 Pass 工厂。
 */
class CppIndentGuidePassFactory :
    TextEditorHighlightingPassFactoryRegistrar,
    TextEditorHighlightingPassFactory,
    DumbAware {

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        if (!BetterBracketsFileUtil.isHighlightingAllowed(file)) return null
        if (!CppSupportUtil.isCppSupportedEnvironment()) return null
        if (!CppBraceSupport.isCppFile(file)) return null
        val state = BetterBracketsState.instance
        if (!state.isCppEnabled || (!state.isCppIndentGuideEnabled && !state.isCppScopeEnabled)) return null
        return CppIndentGuidePass(file.project, editor, file)
    }

    override fun registerHighlightingPassFactory(
        registrar: TextEditorHighlightingPassRegistrar,
        project: Project
    ) {
        if (!CppSupportUtil.isCppSupportedEnvironment()) return
        registrar.registerTextEditorHighlightingPass(
            this,
            TextEditorHighlightingPassRegistrar.Anchor.LAST,
            Pass.LAST_PASS,
            false,
            false
        )
    }
}
