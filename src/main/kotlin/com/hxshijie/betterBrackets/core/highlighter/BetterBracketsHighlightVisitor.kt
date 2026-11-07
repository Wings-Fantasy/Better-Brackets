package com.hxshijie.betterBrackets.core.highlighter

import com.hxshijie.betterBrackets.core.BetterBracketsFileUtil
import com.hxshijie.betterBrackets.core.BracePairProvider
import com.hxshijie.betterBrackets.core.ColorCalculator
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * 括号高亮访问器。
 */
class BetterBracketsHighlightVisitor : HighlightVisitor {

    private var holder: HighlightInfoHolder? = null
    private var activeProvider: BracePairProvider? = null

    private fun findProvider(file: PsiFile): BracePairProvider? {
        val state = BetterBracketsState.instance
        return BracePairProvider.braceSupportExtensionPoint.extensionList.firstOrNull { it.isEnabled(state) && it.isSupported(file) }
    }

    override fun suitableForFile(file: PsiFile): Boolean {
        if (!BetterBracketsFileUtil.isHighlightingAllowed(file)) return false
        return findProvider(file) != null
    }

    override fun analyze(
        file: PsiFile,
        updateWholeFile: Boolean,
        holder: HighlightInfoHolder,
        action: Runnable
    ): Boolean {
        this.holder = holder
        this.activeProvider = findProvider(file)
        try {
            action.run()
        } finally {
            this.holder = null
            this.activeProvider = null
        }
        return true
    }

    override fun visit(element: PsiElement) {
        val currentHolder = holder ?: return
        val provider = activeProvider ?: return

        val state = BetterBracketsState.instance
        val match = provider.findBraceMatch(element, state) ?: return

        val attributes = ColorCalculator.getTextAttributes(match.depth, match.colors)

        val builder = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
            .range(element.textRange)
            .textAttributes(attributes)

        val info = builder.create()
        if (info != null) {
            currentHolder.add(info)
        }
    }

    override fun clone(): HighlightVisitor = BetterBracketsHighlightVisitor()
}
