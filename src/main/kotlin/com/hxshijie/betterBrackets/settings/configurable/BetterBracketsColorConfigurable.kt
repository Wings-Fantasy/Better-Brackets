package com.hxshijie.betterBrackets.settings.configurable

import com.hxshijie.betterBrackets.bundle.BetterBracketsBundle
import com.hxshijie.betterBrackets.core.ColorCalculator
import com.hxshijie.betterBrackets.core.BracePairProvider
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.hxshijie.betterBrackets.settings.configurable.ui.BracketPreviewEditor
import com.hxshijie.betterBrackets.settings.configurable.ui.previewGroup
import com.intellij.icons.AllIcons
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import kotlin.reflect.KMutableProperty0

/**
 * 颜色方案与调色板设置面板 (Better Brackets -> Color)。
 */
class BetterBracketsColorConfigurable : Configurable {
    private val settings = BetterBracketsState.instance
    private var mainPanel: DialogPanel? = null

    private val bindings = mutableListOf<ColorBinding>()
    private val previewEditors = mutableMapOf<String, BracketPreviewEditor>()

    private fun getLiveState(): BetterBracketsState {
        val live = settings.clone()
        bindings.forEach { it.applyTo(live) }
        return live
    }

    private data class LanguageConfigItem(
        val id: String,
        val displayName: String,
        val detailBuilder: (BetterBracketsState, () -> Unit) -> DialogPanel
    )

    private val languageItems by lazy {
        listOf(
            LanguageConfigItem(
                id = "JAVA",
                displayName = "Java"
            ) { state, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.color.group.color-palette")) {
                        paletteHint(BetterBracketsBundle.message("settings.color.global.tip"))
                        panel {
                            paletteRow(BetterBracketsBundle.message("settings.color.global.round"), state::javaRoundColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.square"), state::javaSquareColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.curly"), state::javaCurlyColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.angle"), state::javaAngleColors, onChanged)
                        }
                    }.topGap(TopGap.NONE)
                }
            },

            LanguageConfigItem(
                id = "KOTLIN",
                displayName = "Kotlin"
            ) { state, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.color.group.color-palette")) {
                        paletteHint(BetterBracketsBundle.message("settings.color.global.tip"))
                        panel {
                            paletteRow(BetterBracketsBundle.message("settings.color.global.round"), state::kotlinRoundColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.square"), state::kotlinSquareColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.curly"), state::kotlinCurlyColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.angle"), state::kotlinAngleColors, onChanged)
                        }
                    }.topGap(TopGap.NONE)
                }
            },

            LanguageConfigItem(
                id = "CPP",
                displayName = "C / C++"
            ) { state, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.color.group.color-palette")) {
                        paletteHint(BetterBracketsBundle.message("settings.color.global.tip"))
                        panel {
                            paletteRow(BetterBracketsBundle.message("settings.color.global.round"), state::cppRoundColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.square"), state::cppSquareColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.curly"), state::cppCurlyColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.angle"), state::cppAngleColors, onChanged)
                        }
                    }.topGap(TopGap.NONE)
                }
            },

            LanguageConfigItem(
                id = "HTML",
                displayName = "XML / HTML"
            ) { state, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.color.group.color-palette")) {
                        paletteHint(BetterBracketsBundle.message("settings.color.global.tip"))
                        panel {
                            paletteRow(BetterBracketsBundle.message("settings.color.html.angle"), state::htmlTagColors, onChanged)
                        }
                    }.topGap(TopGap.NONE)
                }
            },

            LanguageConfigItem(
                id = "JS",
                displayName = "JavaScript"
            ) { state, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.color.group.color-palette")) {
                        paletteHint(BetterBracketsBundle.message("settings.color.global.tip"))
                        panel {
                            paletteRow(BetterBracketsBundle.message("settings.color.global.round"), state::jsRoundColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.square"), state::jsSquareColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.curly"), state::jsCurlyColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.angle"), state::jsAngleColors, onChanged)
                        }
                    }.topGap(TopGap.NONE)
                }
            },

            LanguageConfigItem(
                id = "CSS",
                displayName = "CSS"
            ) { state, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.color.group.color-palette")) {
                        paletteHint(BetterBracketsBundle.message("settings.color.global.tip"))
                        panel {
                            paletteRow(BetterBracketsBundle.message("settings.color.global.round"), state::cssRoundColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.square"), state::cssSquareColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.curly"), state::cssCurlyColors, onChanged)
                        }
                    }.topGap(TopGap.NONE)
                }
            },

            LanguageConfigItem(
                id = "VUE",
                displayName = "Vue"
            ) { state, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.color.group.color-palette")) {
                        panel {
                            singleColorRow(
                                BetterBracketsBundle.message("settings.color.vue.double-curly"),
                                state::vueInterpolationColor,
                                comment = BetterBracketsBundle.message(
                                    "settings.color.vue.tip",
                                    "<template>", "<script>", "<style>"
                                ),
                                onChanged = onChanged
                            )
                        }
                    }.topGap(TopGap.NONE)
                }
            },

            LanguageConfigItem(
                id = "YAML",
                displayName = "YAML"
            ) { state, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.color.group.color-palette")) {
                        paletteHint(BetterBracketsBundle.message("settings.color.global.tip"))
                        panel {
                            paletteRow(BetterBracketsBundle.message("settings.color.global.square"), state::yamlSquareColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.curly"), state::yamlCurlyColors, onChanged)
                        }
                    }.topGap(TopGap.NONE)
                }
            },

            LanguageConfigItem(
                id = "JSON",
                displayName = "JSON"
            ) { state, onChanged ->
                panel {
                    group(BetterBracketsBundle.message("settings.color.group.color-palette")) {
                        paletteHint(BetterBracketsBundle.message("settings.color.global.tip"))
                        panel {
                            paletteRow(BetterBracketsBundle.message("settings.color.global.square"), state::jsonSquareColors, onChanged)
                            paletteRow(BetterBracketsBundle.message("settings.color.global.curly"), state::jsonCurlyColors, onChanged)
                        }
                    }.topGap(TopGap.NONE)
                }
            }
        ).filter { BracePairProvider.isLanguageSupported(it.id) }
    }

    override fun getDisplayName(): String = BetterBracketsBundle.message("settings.menu.color")

    override fun createComponent(): JComponent {
        bindings.clear()
        previewEditors.clear()

        val cardLayout = CardLayout()
        val rightContainer = JPanel(cardLayout)

        val langListModel = DefaultListModel<LanguageConfigItem>().apply {
            languageItems.forEach { addElement(it) }
        }
        val langList = JBList(langListModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            border = JBUI.Borders.empty(4)
            cellRenderer = object : ColoredListCellRenderer<LanguageConfigItem>() {
                override fun customizeCellRenderer(
                    list: JList<out LanguageConfigItem>,
                    value: LanguageConfigItem?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean
                ) {
                    if (value != null) {
                        border = JBUI.Borders.empty(0, 8)
                        append(value.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    }
                }
            }
        }

        languageItems.forEach { item ->
            rightContainer.add(createLanguageDetailView(item), item.id)
        }

        langList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = langList.selectedValue ?: return@addListSelectionListener
                cardLayout.show(rightContainer, selected.id)
            }
        }

        if (languageItems.isNotEmpty()) {
            langList.selectedIndex = 0
            cardLayout.show(rightContainer, languageItems[0].id)
        }

        val leftScrollPane = ScrollPaneFactory.createScrollPane(langList).apply {
            preferredSize = JBUI.size(170, -1)
        }

        val splitter = JBSplitter(false, 0.24f).apply {
            firstComponent = leftScrollPane
            secondComponent = rightContainer
            setHonorComponentsMinimumSize(true)
        }

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
            stateProvider = { getLiveState() }
        }
        previewEditors[item.id] = previewEditor
        val optionsSubPanel = item.detailBuilder(settings) {
            previewEditor.refreshHighlighting()
        }

        return panel {
            row {
                cell(optionsSubPanel).align(AlignX.FILL)
            }
            previewGroup(previewEditor)
        }.apply {
            border = JBUI.Borders.empty(0, 12, 0, 4)
        }
    }

    private fun Panel.paletteHint(text: String, showIcon: Boolean = true) {
        row {
            val infoLabel = JBLabel(text).apply {
                if (showIcon) icon = AllIcons.General.Information
                foreground = UIUtil.getContextHelpForeground()
                minimumSize = Dimension(0, preferredSize.height)
            }
            cell(infoLabel)
        }
    }

    private fun Panel.paletteRow(
        labelText: String,
        prop: KMutableProperty0<MutableList<String>>,
        onChanged: (() -> Unit)? = null
    ) {
        val binding = ListColorBinding(prop, onChanged)
        bindings.add(binding)
        row(labelText) {
            cell(binding.component).align(AlignX.FILL)
        }
    }

    private fun Panel.singleColorRow(
        labelText: String,
        prop: KMutableProperty0<String>,
        comment: String? = null,
        onChanged: (() -> Unit)? = null
    ): Row {
        val binding = SingleColorBinding(prop, onChanged)
        bindings.add(binding)
        return row(labelText) {
            cell(binding.component).align(AlignX.FILL)
        }.also {
            if (comment != null) {
                it.rowComment(comment)
            }
        }
    }

    override fun isModified(): Boolean = bindings.any { it.isModified() }

    override fun apply() {
        bindings.forEach { it.apply() }
        ColorCalculator.notifySettingsChanged()
        previewEditors.values.forEach { it.refreshHighlighting() }
    }

    override fun reset() {
        bindings.forEach { it.reset() }
        previewEditors.values.forEach { it.refreshHighlighting() }
    }

    override fun disposeUIResources() {
        previewEditors.clear()
        bindings.clear()
        mainPanel = null
    }

    private interface ColorBinding {
        val component: JComponent
        fun isModified(): Boolean
        fun apply()
        fun reset()
        fun applyTo(targetState: BetterBracketsState)
    }

    private class ListColorBinding(
        val prop: KMutableProperty0<MutableList<String>>,
        private val onChanged: (() -> Unit)? = null
    ) : ColorBinding {
        val workingColors: MutableList<String> = prop.get().toMutableList()
        override val component: JPanel = WrapPanel(JBUI.scale(8), JBUI.scale(8))

        init {
            rebuildSwatches()
        }

        private fun rebuildSwatches() {
            component.removeAll()
            val canDelete = workingColors.size > 1

            workingColors.forEachIndexed { index, hex ->
                val swatch = ColorSwatchComponent(
                    initialHexColor = hex,
                    canDelete = canDelete,
                    onPickColor = { newHex ->
                        workingColors[index] = newHex
                        onChanged?.invoke()
                    },
                    onDelete = {
                        if (workingColors.size > 1) {
                            workingColors.removeAt(index)
                            rebuildSwatches()
                            onChanged?.invoke()
                        }
                    }
                )
                component.add(swatch)
            }

            val addBtn = AddSwatchButton {
                val lastColor = parseHexColor(workingColors.lastOrNull() ?: "#3F9101")
                val nextColor = generateNextColor(lastColor)
                workingColors.add(toHexString(nextColor))
                rebuildSwatches()
                onChanged?.invoke()
            }
            component.add(addBtn)

            component.revalidate()
            component.repaint()
        }

        override fun isModified(): Boolean = workingColors != prop.get()

        override fun apply() {
            prop.set(workingColors.toMutableList())
        }

        override fun reset() {
            workingColors.clear()
            workingColors.addAll(prop.get())
            rebuildSwatches()
        }

        override fun applyTo(targetState: BetterBracketsState) {
            when (prop.name) {
                "javaRoundColors" -> targetState.javaRoundColors = workingColors.toMutableList()
                "javaSquareColors" -> targetState.javaSquareColors = workingColors.toMutableList()
                "javaCurlyColors" -> targetState.javaCurlyColors = workingColors.toMutableList()
                "javaAngleColors" -> targetState.javaAngleColors = workingColors.toMutableList()
                "kotlinRoundColors" -> targetState.kotlinRoundColors = workingColors.toMutableList()
                "kotlinSquareColors" -> targetState.kotlinSquareColors = workingColors.toMutableList()
                "kotlinCurlyColors" -> targetState.kotlinCurlyColors = workingColors.toMutableList()
                "kotlinAngleColors" -> targetState.kotlinAngleColors = workingColors.toMutableList()
                "cppRoundColors" -> targetState.cppRoundColors = workingColors.toMutableList()
                "cppSquareColors" -> targetState.cppSquareColors = workingColors.toMutableList()
                "cppCurlyColors" -> targetState.cppCurlyColors = workingColors.toMutableList()
                "cppAngleColors" -> targetState.cppAngleColors = workingColors.toMutableList()
                "htmlTagColors" -> targetState.htmlTagColors = workingColors.toMutableList()
                "jsRoundColors" -> targetState.jsRoundColors = workingColors.toMutableList()
                "jsSquareColors" -> targetState.jsSquareColors = workingColors.toMutableList()
                "jsCurlyColors" -> targetState.jsCurlyColors = workingColors.toMutableList()
                "jsAngleColors" -> targetState.jsAngleColors = workingColors.toMutableList()
                "cssRoundColors" -> targetState.cssRoundColors = workingColors.toMutableList()
                "cssSquareColors" -> targetState.cssSquareColors = workingColors.toMutableList()
                "cssCurlyColors" -> targetState.cssCurlyColors = workingColors.toMutableList()
                "yamlSquareColors" -> targetState.yamlSquareColors = workingColors.toMutableList()
                "yamlCurlyColors" -> targetState.yamlCurlyColors = workingColors.toMutableList()
                "jsonSquareColors" -> targetState.jsonSquareColors = workingColors.toMutableList()
                "jsonCurlyColors" -> targetState.jsonCurlyColors = workingColors.toMutableList()
            }
        }
    }

    private class SingleColorBinding(
        val prop: KMutableProperty0<String>,
        private val onChanged: (() -> Unit)? = null
    ) : ColorBinding {
        var workingColor: String = prop.get()
        override val component: JPanel = WrapPanel(JBUI.scale(8), JBUI.scale(8))

        init {
            rebuildSwatch()
        }

        private fun rebuildSwatch() {
            component.removeAll()
            val swatch = ColorSwatchComponent(
                initialHexColor = workingColor,
                canDelete = false,
                onPickColor = { newHex ->
                    workingColor = newHex
                    onChanged?.invoke()
                },
                onDelete = {}
            )
            component.add(swatch)
            component.revalidate()
            component.repaint()
        }

        override fun isModified(): Boolean = !workingColor.equals(prop.get(), ignoreCase = true)

        override fun apply() {
            prop.set(workingColor)
        }

        override fun reset() {
            workingColor = prop.get()
            rebuildSwatch()
        }

        override fun applyTo(targetState: BetterBracketsState) {
            if (prop.name == "vueInterpolationColor") {
                targetState.vueInterpolationColor = workingColor
            }
        }
    }

    /**
     * 颜色色块组件，支持取色修改与删除。
     */
    private class ColorSwatchComponent(
        initialHexColor: String,
        private val canDelete: Boolean,
        private val onPickColor: (String) -> Unit,
        private val onDelete: () -> Unit
    ) : JComponent() {
        private var hexColor: String = initialHexColor
        private var isHovered = false
        private var isHoveringDeleteBadge = false

        init {
            val size = JBUI.size(24, 24)
            preferredSize = size
            minimumSize = size
            maximumSize = size
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            updateToolTip()

            val mouseHandler = object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    isHovered = true
                    updateDeleteHoverState(e.point)
                    repaint()
                }

                override fun mouseMoved(e: MouseEvent) {
                    updateDeleteHoverState(e.point)
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    isHovered = false
                    isHoveringDeleteBadge = false
                    repaint()
                }

                override fun mouseClicked(e: MouseEvent) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return
                    if (canDelete && isPointInDeleteZone(e.point)) {
                        onDelete()
                    } else {
                        ColorChooserService.getInstance().showPopup(
                            null,
                            parseHexColor(hexColor),
                            { color, _ ->
                                if (color != null) {
                                    val newHex = toHexString(color)
                                    updateColor(newHex)
                                    onPickColor(newHex)
                                }
                            },
                            RelativePoint.getSouthOf(this@ColorSwatchComponent),
                            false
                        )
                    }
                }
            }
            addMouseListener(mouseHandler)
            addMouseMotionListener(mouseHandler)
        }

        fun updateColor(newHex: String) {
            hexColor = newHex
            updateToolTip()
            repaint()
        }

        private fun updateToolTip() {
            toolTipText = if (canDelete) "$hexColor (点击修改颜色，右上角 × 删除)" else "$hexColor (点击修改颜色)"
        }

        private fun isPointInDeleteZone(p: Point): Boolean {
            val badgeSize = JBUI.scale(12)
            return p.x >= width - badgeSize && p.y <= badgeSize
        }

        private fun updateDeleteHoverState(p: Point) {
            isHoveringDeleteBadge = canDelete && isPointInDeleteZone(p)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val arc = JBUI.scale(6).toFloat()
            val w = width.toFloat()
            val h = height.toFloat()

            // 绘制圆角色块
            val rect = RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc)
            g2.color = parseHexColor(hexColor)
            g2.fill(rect)

            // 绘制边框
            g2.color = if (isHovered) JBColor.foreground() else JBColor(Color(0, 0, 0, 50), Color(255, 255, 255, 50))
            g2.stroke = BasicStroke(1f)
            g2.draw(rect)

            // 悬停且允许删除时显示删除图标
            if (isHovered && canDelete) {
                val badgeDiameter = JBUI.scale(11).toFloat()
                val bx = w - badgeDiameter - JBUI.scale(1)
                val by = JBUI.scale(1).toFloat()

                g2.color = if (isHoveringDeleteBadge) {
                    Color(210, 45, 45, 235)
                } else {
                    Color(0, 0, 0, 175)
                }
                g2.fill(Ellipse2D.Float(bx, by, badgeDiameter, badgeDiameter))

                g2.color = Color.WHITE
                g2.stroke = BasicStroke(JBUI.scale(1.3f.toInt()).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val pad = JBUI.scale(3).toFloat()
                val path = Path2D.Float().apply {
                    moveTo(bx + pad, by + pad)
                    lineTo(bx + badgeDiameter - pad, by + badgeDiameter - pad)
                    moveTo(bx + badgeDiameter - pad, by + pad)
                    lineTo(bx + pad, by + badgeDiameter - pad)
                }
                g2.draw(path)
            }

            g2.dispose()
        }
    }

    private class AddSwatchButton(onClick: () -> Unit) : JComponent() {
        private var isHovered = false

        init {
            val size = JBUI.size(24, 24)
            preferredSize = size
            minimumSize = size
            maximumSize = size
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "添加颜色"

            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    isHovered = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    isHovered = false
                    repaint()
                }

                override fun mouseClicked(e: MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        onClick()
                    }
                }
            })
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val arc = JBUI.scale(6).toFloat()
            val w = width.toFloat()
            val h = height.toFloat()
            val rect = RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc)

            g2.color = if (isHovered) {
                JBColor(Color(0, 0, 0, 20), Color(255, 255, 255, 25))
            } else {
                UIUtil.getPanelBackground()
            }
            g2.fill(rect)

            g2.color = if (isHovered) JBColor.foreground() else JBColor.border()
            g2.stroke = BasicStroke(1f)
            g2.draw(rect)

            g2.color = if (isHovered) JBColor.foreground() else UIUtil.getContextHelpForeground()
            g2.stroke = BasicStroke(JBUI.scale(1.5f.toInt()).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val cx = w / 2f
            val cy = h / 2f
            val arm = JBUI.scale(4).toFloat()
            val path = Path2D.Float().apply {
                moveTo(cx - arm, cy)
                lineTo(cx + arm, cy)
                moveTo(cx, cy - arm)
                lineTo(cx, cy + arm)
            }
            g2.draw(path)

            g2.dispose()
        }
    }

    /**
     * 支持根据可用宽度自动折行的流式布局面板。
     */
    private class WrapPanel(private val hGap: Int, private val vGap: Int) : JPanel() {
        init {
            isOpaque = false
            layout = object : FlowLayout(LEFT, hGap, vGap) {
                override fun preferredLayoutSize(target: Container): Dimension = computePreferredSize(target)
                override fun minimumLayoutSize(target: Container): Dimension = computeMinimumSize(target)

                private fun computePreferredSize(target: Container): Dimension {
                    synchronized(target.treeLock) {
                        val insets = target.insets
                        var width = target.width
                        if (width <= 0 && target.parent != null) {
                            width = target.parent.width
                        }
                        if (width <= 0) {
                            var totalW = insets.left + insets.right
                            var maxH = 0
                            for (i in 0 until target.componentCount) {
                                val comp = target.getComponent(i)
                                if (!comp.isVisible) continue
                                val d = comp.preferredSize
                                if (totalW > insets.left + insets.right) totalW += hGap
                                totalW += d.width
                                maxH = maxOf(maxH, d.height)
                            }
                            return Dimension(totalW, maxH + insets.top + insets.bottom)
                        }

                        val availWidth = width - insets.left - insets.right
                        var x = 0
                        var y = insets.top
                        var rowHeight = 0
                        var maxRowWidth = 0

                        for (i in 0 until target.componentCount) {
                            val comp = target.getComponent(i)
                            if (!comp.isVisible) continue
                            val d = comp.preferredSize
                            if (x == 0 || x + d.width <= availWidth) {
                                if (x > 0) x += hGap
                                x += d.width
                                rowHeight = maxOf(rowHeight, d.height)
                            } else {
                                y += rowHeight + vGap
                                x = d.width
                                rowHeight = d.height
                            }
                            maxRowWidth = maxOf(maxRowWidth, x)
                        }
                        y += rowHeight + insets.bottom
                        return Dimension(maxRowWidth + insets.left + insets.right, y)
                    }
                }

                private fun computeMinimumSize(target: Container): Dimension {
                    val insets = target.insets
                    val swatchSize = JBUI.scale(24)
                    return Dimension(swatchSize + insets.left + insets.right, swatchSize + insets.top + insets.bottom)
                }
            }.apply {
                hgap = hGap
                vgap = 0
            }
        }

        override fun getMinimumSize(): Dimension {
            val insets = insets
            val swatchSize = JBUI.scale(24)
            return Dimension(swatchSize + insets.left + insets.right, swatchSize + insets.top + insets.bottom)
        }
    }

    companion object {
        private fun parseHexColor(hex: String): Color {
            return try {
                ColorUtil.fromHex(hex.removePrefix("#"))
            } catch (_: Exception) {
                Color(0x3F9101)
            }
        }

        private fun toHexString(color: Color): String {
            return String.format("#%02X%02X%02X", color.red, color.green, color.blue)
        }

        private fun generateNextColor(base: Color): Color {
            val hsb = FloatArray(3)
            Color.RGBtoHSB(base.red, base.green, base.blue, hsb)
            val nextHue = (hsb[0] + 0.15f) % 1.0f
            return Color.getHSBColor(nextHue, hsb[1].coerceAtLeast(0.6f), hsb[2].coerceAtLeast(0.8f))
        }
    }
}
