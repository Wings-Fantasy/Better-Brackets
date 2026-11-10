package com.hxshijie.betterBrackets.support.yaml

import com.hxshijie.betterBrackets.core.BetterBracketsFileUtil
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.intellij.codeHighlighting.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YAMLFile

/**
 * YAML 语言代码分析高亮 Pass 工厂。
 */
class YamlIndentGuidePassFactory :
    TextEditorHighlightingPassFactoryRegistrar,
    TextEditorHighlightingPassFactory,
    DumbAware {

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        if (!BetterBracketsFileUtil.isHighlightingAllowed(file)) return null
        if (file !is YAMLFile) return null
        val state = BetterBracketsState.instance
        if (!state.isYamlEnabled || (!state.isYamlIndentGuideEnabled && !state.isYamlScopeEnabled)) return null
        return YamlIndentGuidePass(file.project, editor, file)
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
