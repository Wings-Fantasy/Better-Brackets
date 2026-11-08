package com.hxshijie.betterBrackets.settings.configurable

import com.hxshijie.betterBrackets.bundle.BetterBracketsBundle
import com.hxshijie.betterBrackets.core.ColorCalculator
import com.hxshijie.betterBrackets.core.BracePairProvider
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.hxshijie.betterBrackets.settings.configurable.ui.BracketPreviewEditor
import com.hxshijie.betterBrackets.settings.configurable.ui.previewGroup
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CheckBoxList
import com.intellij.ui.CheckBoxListListener
import com.intellij.ui.JBSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.CardLayout
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import kotlin.reflect.KMutableProperty0

/**
 * 功能开关设置面板 (Better Brackets -> Function)。
 */
class BetterBracketsFunctionConfigurable : Configurable {

    private val settings = BetterBracketsState.instance
    private val workingState: BetterBracketsState = settings.clone()
    private val previewEditors = mutableMapOf<String, BracketPreviewEditor>()

    private val rightSubPanels = mutableMapOf<String, DialogPanel>()
    private val langMasterStates = mutableMapOf<String, Boolean>()

    private var languageCheckBoxList: CheckBoxList<LanguageConfigItem>? = null
    private var mainPanel: DialogPanel? = null

    private class LanguageConfigItem(
        val id: String,
        val displayName: String,
        val masterProp: KMutableProperty0<Boolean>,
        val detailBuilder: (settings: BetterBracketsState, workingState: BetterBracketsState, onChanged: () -> Unit) -> DialogPanel
    )

    private val languageItems by lazy {
        listOf(
            LanguageConfigItem(
                id = "JAVA",
                displayName = "Java",
                masterProp = settings::isJavaEnabled
            ) { state, live, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.function.group.bracket-rendering")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.java.round"))
                                .bindSelected(state::isJavaRoundEnabled)
                                .onChanged { cb ->
                                    live.isJavaRoundEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.java.square"))
                                .bindSelected(state::isJavaSquareEnabled)
                                .onChanged { cb ->
                                    live.isJavaSquareEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.java.curly"))
                                .bindSelected(state::isJavaCurlyEnabled)
                                .onChanged { cb ->
                                    live.isJavaCurlyEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.java.angle"))
                                .bindSelected(state::isJavaAngleEnabled)
                                .onChanged { cb ->
                                    live.isJavaAngleEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }.topGap(TopGap.NONE)
                    group(BetterBracketsBundle.message("settings.function.group.visual-aids")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.indent-guide"))
                                .bindSelected(state::isJavaIndentGuideEnabled)
                                .onChanged { cb ->
                                    live.isJavaIndentGuideEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.scope"))
                                .bindSelected(state::isJavaScopeEnabled)
                                .comment(BetterBracketsBundle.message("settings.function.global.scope-comment"))
                                .onChanged { cb ->
                                    live.isJavaScopeEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }
                }
            },

            LanguageConfigItem(
                id = "KOTLIN",
                displayName = "Kotlin",
                masterProp = settings::isKotlinEnabled
            ) { state, live, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.function.group.bracket-rendering")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.kotlin.round"))
                                .bindSelected(state::isKotlinRoundEnabled)
                                .onChanged { cb ->
                                    live.isKotlinRoundEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.kotlin.square"))
                                .bindSelected(state::isKotlinSquareEnabled)
                                .onChanged { cb ->
                                    live.isKotlinSquareEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.kotlin.curly"))
                                .bindSelected(state::isKotlinCurlyEnabled)
                                .onChanged { cb ->
                                    live.isKotlinCurlyEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.kotlin.angle"))
                                .bindSelected(state::isKotlinAngleEnabled)
                                .onChanged { cb ->
                                    live.isKotlinAngleEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }.topGap(TopGap.NONE)
                    group(BetterBracketsBundle.message("settings.function.group.visual-aids")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.indent-guide"))
                                .bindSelected(state::isKotlinIndentGuideEnabled)
                                .onChanged { cb ->
                                    live.isKotlinIndentGuideEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.scope"))
                                .bindSelected(state::isKotlinScopeEnabled)
                                .comment(BetterBracketsBundle.message("settings.function.global.scope-comment"))
                                .onChanged { cb ->
                                    live.isKotlinScopeEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }
                }
            },

            LanguageConfigItem(
                id = "CPP",
                displayName = "C / C++",
                masterProp = settings::isCppEnabled
            ) { state, live, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.function.group.bracket-rendering")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.cpp.round"))
                                .bindSelected(state::isCppRoundEnabled)
                                .onChanged { cb ->
                                    live.isCppRoundEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.cpp.square"))
                                .bindSelected(state::isCppSquareEnabled)
                                .onChanged { cb ->
                                    live.isCppSquareEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.cpp.curly"))
                                .bindSelected(state::isCppCurlyEnabled)
                                .onChanged { cb ->
                                    live.isCppCurlyEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.cpp.angle"))
                                .bindSelected(state::isCppAngleEnabled)
                                .onChanged { cb ->
                                    live.isCppAngleEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }.topGap(TopGap.NONE)
                    group(BetterBracketsBundle.message("settings.function.group.visual-aids")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.indent-guide"))
                                .bindSelected(state::isCppIndentGuideEnabled)
                                .onChanged { cb ->
                                    live.isCppIndentGuideEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.scope"))
                                .bindSelected(state::isCppScopeEnabled)
                                .comment(BetterBracketsBundle.message("settings.function.global.scope-comment"))
                                .onChanged { cb ->
                                    live.isCppScopeEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }
                }
            },

            LanguageConfigItem(
                id = "HTML",
                displayName = "XML / HTML",
                masterProp = settings::isHtmlEnabled
            ) { state, live, onChanged ->
                panel {
                    group("标签渲染") {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.html.tag-text"))
                                .bindSelected(state::isHtmlTagTextEnabled)
                                .onChanged { cb ->
                                    live.isHtmlTagTextEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }.topGap(TopGap.NONE)
                    group(BetterBracketsBundle.message("settings.function.group.visual-aids")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.indent-guide"))
                                .bindSelected(state::isHtmlIndentGuideEnabled)
                                .onChanged { cb ->
                                    live.isHtmlIndentGuideEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.scope"))
                                .bindSelected(state::isHtmlScopeEnabled)
                                .comment(BetterBracketsBundle.message("settings.function.html.scope-comment"))
                                .onChanged { cb ->
                                    live.isHtmlScopeEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }
                }
            },

            LanguageConfigItem(
                id = "JS",
                displayName = "JavaScript",
                masterProp = settings::isJsEnabled
            ) { state, live, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.function.group.bracket-rendering")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.js.round"))
                                .bindSelected(state::isJsRoundEnabled)
                                .onChanged { cb ->
                                    live.isJsRoundEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.js.square"))
                                .bindSelected(state::isJsSquareEnabled)
                                .onChanged { cb ->
                                    live.isJsSquareEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.js.curly"))
                                .bindSelected(state::isJsCurlyEnabled)
                                .onChanged { cb ->
                                    live.isJsCurlyEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.js.angle"))
                                .bindSelected(state::isJsAngleEnabled)
                                .onChanged { cb ->
                                    live.isJsAngleEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }.topGap(TopGap.NONE)
                    group(BetterBracketsBundle.message("settings.function.group.visual-aids")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.indent-guide"))
                                .bindSelected(state::isJsIndentGuideEnabled)
                                .onChanged { cb ->
                                    live.isJsIndentGuideEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.scope"))
                                .bindSelected(state::isJsScopeEnabled)
                                .comment(BetterBracketsBundle.message("settings.function.global.scope-comment"))
                                .onChanged { cb ->
                                    live.isJsScopeEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }
                }
            },

            LanguageConfigItem(
                id = "CSS",
                displayName = "CSS",
                masterProp = settings::isCssEnabled
            ) { state, live, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.function.group.bracket-rendering")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.css.round"))
                                .bindSelected(state::isCssRoundEnabled)
                                .onChanged { cb ->
                                    live.isCssRoundEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.css.square"))
                                .bindSelected(state::isCssSquareEnabled)
                                .onChanged { cb ->
                                    live.isCssSquareEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.css.curly"))
                                .bindSelected(state::isCssCurlyEnabled)
                                .onChanged { cb ->
                                    live.isCssCurlyEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }.topGap(TopGap.NONE)
                    group(BetterBracketsBundle.message("settings.function.group.visual-aids")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.indent-guide"))
                                .bindSelected(state::isCssIndentGuideEnabled)
                                .onChanged { cb ->
                                    live.isCssIndentGuideEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.scope"))
                                .bindSelected(state::isCssScopeEnabled)
                                .comment(BetterBracketsBundle.message("settings.function.css.scope-comment"))
                                .onChanged { cb ->
                                    live.isCssScopeEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }
                }
            },

            LanguageConfigItem(
                id = "VUE",
                displayName = "Vue",
                masterProp = settings::isVueEnabled
            ) { state, live, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.function.group.template-enhancement")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.vue.double-curly"))
                                .bindSelected(state::isVueInterpolationEnabled)
                                .comment(BetterBracketsBundle.message("settings.function.vue.double-tip",
                                    "<template>", "<script>", "<style>"))
                                .onChanged { cb ->
                                    live.isVueInterpolationEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }.topGap(TopGap.NONE)
                    group(BetterBracketsBundle.message("settings.function.group.visual-aids")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.indent-guide"))
                                .bindSelected(state::isVueIndentGuideEnabled)
                                .onChanged { cb ->
                                    live.isVueIndentGuideEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.scope"))
                                .bindSelected(state::isVueScopeEnabled)
                                .comment(BetterBracketsBundle.message("settings.function.vue.scope-comment"))
                                .onChanged { cb ->
                                    live.isVueScopeEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }
                }
            },

            LanguageConfigItem(
                id = "YAML",
                displayName = "YAML",
                masterProp = settings::isYamlEnabled
            ) { state, live, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.function.group.bracket-rendering")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.yaml.square"))
                                .bindSelected(state::isYamlSquareEnabled)
                                .onChanged { cb ->
                                    live.isYamlSquareEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.yaml.curly"))
                                .bindSelected(state::isYamlCurlyEnabled)
                                .onChanged { cb ->
                                    live.isYamlCurlyEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }.topGap(TopGap.NONE)
                    group(BetterBracketsBundle.message("settings.function.group.visual-aids")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.indent-guide"))
                                .bindSelected(state::isYamlIndentGuideEnabled)
                                .onChanged { cb ->
                                    live.isYamlIndentGuideEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.scope"))
                                .bindSelected(state::isYamlScopeEnabled)
                                .comment(BetterBracketsBundle.message("settings.function.global.scope-comment"))
                                .onChanged { cb ->
                                    live.isYamlScopeEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }
                }
            },

            LanguageConfigItem(
                id = "JSON",
                displayName = "JSON",
                masterProp = settings::isJsonEnabled
            ) { state, live, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.function.group.bracket-rendering")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.json.square"))
                                .bindSelected(state::isJsonSquareEnabled)
                                .onChanged { cb ->
                                    live.isJsonSquareEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.json.curly"))
                                .bindSelected(state::isJsonCurlyEnabled)
                                .onChanged { cb ->
                                    live.isJsonCurlyEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }.topGap(TopGap.NONE)
                    group(BetterBracketsBundle.message("settings.function.group.visual-aids")) {
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.indent-guide"))
                                .bindSelected(state::isJsonIndentGuideEnabled)
                                .onChanged { cb ->
                                    live.isJsonIndentGuideEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                        row {
                            checkBox(BetterBracketsBundle.message("settings.function.global.scope"))
                                .bindSelected(state::isJsonScopeEnabled)
                                .comment(BetterBracketsBundle.message("settings.function.global.scope-comment"))
                                .onChanged { cb ->
                                    live.isJsonScopeEnabled = cb.isSelected
                                    onChanged()
                                }
                        }
                    }
                }
            }
        ).filter { BracePairProvider.isLanguageSupported(it.id) }
    }

    override fun getDisplayName(): String = BetterBracketsBundle.message("settings.menu.Function")

    override fun createComponent(): JComponent {
        val checkBoxList = CheckBoxList<LanguageConfigItem>()
        val cardLayout = CardLayout()
        val rightContainer = JPanel(cardLayout)

        previewEditors.clear()
        rightSubPanels.clear()
        langMasterStates.clear()
        workingState.copyFrom(settings)

        languageItems.forEach { item ->
            val initialEnabled = item.masterProp.get()
            langMasterStates[item.id] = initialEnabled
            checkBoxList.addItem(item, item.displayName, initialEnabled)
            rightContainer.add(createLanguageDetailView(item), item.id)
        }

        checkBoxList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        checkBoxList.border = JBUI.Borders.empty(4)

        checkBoxList.addListSelectionListener {
            val selected = checkBoxList.getItemAt(checkBoxList.selectedIndex)
            if (selected != null) {
                cardLayout.show(rightContainer, selected.id)
            }
        }

        checkBoxList.setCheckBoxListListener(CheckBoxListListener { index, value ->
            val item = checkBoxList.getItemAt(index) ?: return@CheckBoxListListener
            langMasterStates[item.id] = value
            when (item.id) {
                "JAVA" -> workingState.isJavaEnabled = value
                "KOTLIN" -> workingState.isKotlinEnabled = value
                "CPP" -> workingState.isCppEnabled = value
                "HTML" -> workingState.isHtmlEnabled = value
                "JS" -> workingState.isJsEnabled = value
                "CSS" -> workingState.isCssEnabled = value
                "VUE" -> workingState.isVueEnabled = value
                "YAML" -> workingState.isYamlEnabled = value
                "JSON" -> workingState.isJsonEnabled = value
            }
            rightSubPanels[item.id]?.let { setSubPanelEnabled(it, value) }
            previewEditors[item.id]?.refreshHighlighting()
        })

        if (languageItems.isNotEmpty()) {
            checkBoxList.selectedIndex = 0
            cardLayout.show(rightContainer, languageItems[0].id)
        }

        val leftScrollPane = ScrollPaneFactory.createScrollPane(checkBoxList).apply {
            preferredSize = JBUI.size(170, -1)
        }

        val splitter = JBSplitter(false, 0.24f).apply {
            firstComponent = leftScrollPane
            secondComponent = rightContainer
            setHonorComponentsMinimumSize(true)
        }

        languageCheckBoxList = checkBoxList

        val rootPanel = panel {
            row {
                cell(splitter).align(Align.FILL)
            }.resizableRow()
        }

        mainPanel = rootPanel
        return rootPanel
    }

    private fun createLanguageDetailView(item: LanguageConfigItem): JComponent {
        val previewEditor = BracketPreviewEditor(item.id).apply {
            stateProvider = { workingState }
        }
        previewEditors[item.id] = previewEditor

        val optionsSubPanel = item.detailBuilder(settings, workingState) {
            previewEditor.refreshHighlighting()
        }
        rightSubPanels[item.id] = optionsSubPanel
        setSubPanelEnabled(optionsSubPanel, langMasterStates[item.id] ?: true)

        return panel {
            row {
                cell(optionsSubPanel).align(AlignX.FILL)
            }
            previewGroup(previewEditor)
        }.apply {
            border = JBUI.Borders.empty(0, 12, 0, 4)
        }
    }

    private fun setSubPanelEnabled(component: Component, enabled: Boolean) {
        component.isEnabled = enabled
        if (component is Container) {
            component.components.forEach { child ->
                setSubPanelEnabled(child, enabled)
            }
        }
    }

    override fun isModified(): Boolean {
        val masterModified = languageItems.any { langMasterStates[it.id] != it.masterProp.get() }
        val subModified = rightSubPanels.values.any { it.isModified() }
        return masterModified || subModified
    }

    override fun apply() {
        languageItems.forEach { item ->
            item.masterProp.set(langMasterStates[item.id] ?: true)
        }
        rightSubPanels.values.forEach { it.apply() }
        workingState.copyFrom(settings)
        ColorCalculator.notifySettingsChanged()
        previewEditors.values.forEach { it.refreshHighlighting() }
    }

    override fun reset() {
        workingState.copyFrom(settings)
        languageItems.forEach { item ->
            val enabled = item.masterProp.get()
            langMasterStates[item.id] = enabled
            languageCheckBoxList?.setItemSelected(item, enabled)
            rightSubPanels[item.id]?.let { subPanel ->
                subPanel.reset()
                setSubPanelEnabled(subPanel, enabled)
            }
        }
        languageCheckBoxList?.repaint()
        previewEditors.values.forEach { it.refreshHighlighting() }
    }

    override fun disposeUIResources() {
        previewEditors.clear()
        rightSubPanels.clear()
        langMasterStates.clear()
        languageCheckBoxList = null
        mainPanel = null
    }
}
