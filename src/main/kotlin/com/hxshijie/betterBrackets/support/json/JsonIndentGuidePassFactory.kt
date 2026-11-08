package com.hxshijie.betterBrackets.support.json

import com.hxshijie.betterBrackets.core.BetterBracketsFileUtil
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.intellij.codeHighlighting.*
import com.intellij.json.psi.JsonFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * JSON 语言代码分析高亮 Pass 工厂。
 */
class JsonIndentGuidePassFactory :
    TextEditorHighlightingPassFactoryRegistrar,
    TextEditorHighlightingPassFactory,
    DumbAware {

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        if (!BetterBracketsFileUtil.isHighlightingAllowed(file)) return null
        if (file !is JsonFile) return null
        val state = BetterBracketsState.instance
        if (!state.isJsonEnabled || (!state.isJsonIndentGuideEnabled && !state.isJsonScopeEnabled)) return null
        return JsonIndentGuidePass(file.project, editor, file)
    }

    override fun registerHighlightingPassFactory(
        registrar: TextEditorHighlightingPassRegistrar,
        project: Project
    ) {
        registrar.registerTextEditorHighlightingPass(
            this,
            TextEditorHighlightingPassRegistrar.Anchor.LAST,
            Pass.LAST_PASS,
            false,
            false
        )
    }
}
