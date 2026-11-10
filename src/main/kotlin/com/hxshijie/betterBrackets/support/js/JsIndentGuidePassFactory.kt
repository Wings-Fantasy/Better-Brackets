package com.hxshijie.betterBrackets.support.js

import com.hxshijie.betterBrackets.core.BetterBracketsFileUtil
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.intellij.codeHighlighting.*
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * JavaScript 语言代码分析高亮 Pass 工厂。
 */
class JsIndentGuidePassFactory :
    TextEditorHighlightingPassFactoryRegistrar,
    TextEditorHighlightingPassFactory,
    DumbAware {

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        if (!BetterBracketsFileUtil.isHighlightingAllowed(file)) return null
        if (file !is JSFile) return null
        val state = BetterBracketsState.instance
        if (!state.isJsEnabled || (!state.isJsIndentGuideEnabled && !state.isJsScopeEnabled)) return null
        return JsIndentGuidePass(file.project, editor, file)
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
