package com.hxshijie.betterBrackets.core

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.psi.PsiFile
import com.intellij.psi.SingleRootFileViewProvider

/**
 * 文件工具类，用于接入 JetBrains IDE 的大文件高亮保护及高亮可用性检查。
 */
object BetterBracketsFileUtil {

    fun isHighlightingAllowed(file: PsiFile): Boolean {
        if (!file.isValid) {
            return false
        }

        val virtualFile = file.virtualFile
        if (virtualFile != null) {
            if (SingleRootFileViewProvider.isTooLargeForIntelligence(virtualFile) ||
                SingleRootFileViewProvider.isTooLargeForContentLoading(virtualFile)
            ) {
                return false
            }
        }

        if (!ProblemHighlightFilter.shouldHighlightFile(file)) {
            return false
        }

        val project = file.project
        if (!project.isDisposed && !DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(file)) {
            return false
        }

        return true
    }
}
