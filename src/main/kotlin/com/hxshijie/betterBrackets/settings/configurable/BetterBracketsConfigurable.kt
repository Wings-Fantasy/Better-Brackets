package com.hxshijie.betterBrackets.settings.configurable

import com.hxshijie.betterBrackets.bundle.BetterBracketsBundle
import com.intellij.ide.DataManager
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import java.awt.Component

/**
 * 主设置面板 (Better Brackets)。
 */
class BetterBracketsConfigurable : BoundConfigurable(
    BetterBracketsBundle.message("settings.menu.main")
) {

    companion object {
        private const val ROOT_CONFIGURABLE_ID = "com.hxshijie.betterBrackets.settings"
    }

    override fun createPanel(): DialogPanel {
        lateinit var panel: DialogPanel

        val childExtensions = Configurable.APPLICATION_CONFIGURABLE.extensionList
            .filter { it.parentId == ROOT_CONFIGURABLE_ID }

        panel = panel {
            row {
                text(BetterBracketsBundle.message("settings.menu.introduction"))
            }

            indent {
                for (child in childExtensions) {
                    @Suppress("DialogTitleCapitalization")
                    val displayName = child.key?.let { BetterBracketsBundle.message(it) }
                        ?: child.displayName
                        ?: child.id
                        ?: continue
                    val targetId = child.id

                    row {
                        link(displayName) { event ->
                            val sourceComponent = event.source as? Component ?: panel
                            val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(sourceComponent))

                            targetId?.let { id ->
                                settings?.find(id)?.let { configurable ->
                                    settings.select(configurable)
                                }
                            }
                        }
                    }
                }
            }
        }

        return panel
    }
}
