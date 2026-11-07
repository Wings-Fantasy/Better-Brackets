package com.hxshijie.betterBrackets.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * 插件全局配置状态持久化服务。
 */
@Service(Service.Level.APP)
@State(
    name = "com.hxshijie.betterBrackets.settings.BetterBracketsState",
    storages = [Storage("BetterBrackets.xml")]
)
class BetterBracketsState : PersistentStateComponent<BetterBracketsState> {

    // 语言启用总开关
    var isJavaEnabled: Boolean = true
    var isKotlinEnabled: Boolean = true
    var isCppEnabled: Boolean = true
    var isHtmlEnabled: Boolean = true
    var isJsEnabled: Boolean = true
    var isCssEnabled: Boolean = true
    var isVueEnabled: Boolean = true
    var isYamlEnabled: Boolean = true

    // Java 子功能开关
    var isJavaRoundEnabled: Boolean = true
    var isJavaSquareEnabled: Boolean = true
    var isJavaCurlyEnabled: Boolean = true
    var isJavaAngleEnabled: Boolean = true
    var isJavaIndentGuideEnabled: Boolean = true
    var isJavaScopeEnabled: Boolean = true

    // Kotlin 子功能开关
    var isKotlinRoundEnabled: Boolean = true
    var isKotlinSquareEnabled: Boolean = true
    var isKotlinCurlyEnabled: Boolean = true
    var isKotlinAngleEnabled: Boolean = true
    var isKotlinIndentGuideEnabled: Boolean = true
    var isKotlinScopeEnabled: Boolean = true

    // C / C++ 子功能开关
    var isCppRoundEnabled: Boolean = true
    var isCppSquareEnabled: Boolean = true
    var isCppCurlyEnabled: Boolean = true
    var isCppAngleEnabled: Boolean = true
    var isCppIndentGuideEnabled: Boolean = true
    var isCppScopeEnabled: Boolean = true

    // HTML 子功能开关
    var isHtmlTagTextEnabled: Boolean = true
    var isHtmlIndentGuideEnabled: Boolean = true
    var isHtmlScopeEnabled: Boolean = true

    // JavaScript 子功能开关
    var isJsRoundEnabled: Boolean = true
    var isJsSquareEnabled: Boolean = true
    var isJsCurlyEnabled: Boolean = true
    var isJsAngleEnabled: Boolean = true
    var isJsIndentGuideEnabled: Boolean = true
    var isJsScopeEnabled: Boolean = true

    // CSS 子功能开关
    var isCssRoundEnabled: Boolean = true
    var isCssSquareEnabled: Boolean = true
    var isCssCurlyEnabled: Boolean = true
    var isCssIndentGuideEnabled: Boolean = true
    var isCssScopeEnabled: Boolean = true

    // Vue 子功能开关
    var isVueInterpolationEnabled: Boolean = true
    var isVueIndentGuideEnabled: Boolean = true
    var isVueScopeEnabled: Boolean = true

    // Yaml 子功能开关
    var isYamlSquareEnabled: Boolean = true
    var isYamlCurlyEnabled: Boolean = true
    var isYamlIndentGuideEnabled: Boolean = true
    var isYamlScopeEnabled: Boolean = true

    // 调色板配置
    var javaRoundColors: MutableList<String> = defaultPalette()
    var javaSquareColors: MutableList<String> = defaultPalette()
    var javaCurlyColors: MutableList<String> = defaultPalette()
    var javaAngleColors: MutableList<String> = defaultPalette()

    var kotlinRoundColors: MutableList<String> = defaultPalette()
    var kotlinSquareColors: MutableList<String> = defaultPalette()
    var kotlinCurlyColors: MutableList<String> = defaultPalette()
    var kotlinAngleColors: MutableList<String> = defaultPalette()

    var cppRoundColors: MutableList<String> = defaultPalette()
    var cppSquareColors: MutableList<String> = defaultPalette()
    var cppCurlyColors: MutableList<String> = defaultPalette()
    var cppAngleColors: MutableList<String> = defaultPalette()

    var htmlTagColors: MutableList<String> = defaultPalette()

    var jsRoundColors: MutableList<String> = defaultPalette()
    var jsSquareColors: MutableList<String> = defaultPalette()
    var jsCurlyColors: MutableList<String> = defaultPalette()
    var jsAngleColors: MutableList<String> = defaultPalette()

    var cssRoundColors: MutableList<String> = defaultPalette()
    var cssSquareColors: MutableList<String> = defaultPalette()
    var cssCurlyColors: MutableList<String> = defaultPalette()

    var vueInterpolationColor: String = "#DAB407"

    var yamlSquareColors: MutableList<String> = defaultPalette()
    var yamlCurlyColors: MutableList<String> = defaultPalette()

    private fun defaultPalette(): MutableList<String> = mutableListOf(
        "#56C701",
        "#14E1E1",
        "#DAB407",
        "#F10FD0"
    )

    fun clone(): BetterBracketsState {
        val copy = BetterBracketsState()
        copy.copyFrom(this)
        return copy
    }

    fun copyFrom(other: BetterBracketsState) {
        XmlSerializerUtil.copyBean(other, this)
        this.javaRoundColors = other.javaRoundColors.toMutableList()
        this.javaSquareColors = other.javaSquareColors.toMutableList()
        this.javaCurlyColors = other.javaCurlyColors.toMutableList()
        this.javaAngleColors = other.javaAngleColors.toMutableList()

        this.kotlinRoundColors = other.kotlinRoundColors.toMutableList()
        this.kotlinSquareColors = other.kotlinSquareColors.toMutableList()
        this.kotlinCurlyColors = other.kotlinCurlyColors.toMutableList()
        this.kotlinAngleColors = other.kotlinAngleColors.toMutableList()

        this.cppRoundColors = other.cppRoundColors.toMutableList()
        this.cppSquareColors = other.cppSquareColors.toMutableList()
        this.cppCurlyColors = other.cppCurlyColors.toMutableList()
        this.cppAngleColors = other.cppAngleColors.toMutableList()

        this.htmlTagColors = other.htmlTagColors.toMutableList()

        this.jsRoundColors = other.jsRoundColors.toMutableList()
        this.jsSquareColors = other.jsSquareColors.toMutableList()
        this.jsCurlyColors = other.jsCurlyColors.toMutableList()
        this.jsAngleColors = other.jsAngleColors.toMutableList()

        this.cssRoundColors = other.cssRoundColors.toMutableList()
        this.cssSquareColors = other.cssSquareColors.toMutableList()
        this.cssCurlyColors = other.cssCurlyColors.toMutableList()

        this.vueInterpolationColor = other.vueInterpolationColor

        this.yamlSquareColors = other.yamlSquareColors.toMutableList()
        this.yamlCurlyColors = other.yamlCurlyColors.toMutableList()
    }

    override fun getState(): BetterBracketsState = this

    override fun loadState(state: BetterBracketsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: BetterBracketsState
            get() = ApplicationManager.getApplication().getService(BetterBracketsState::class.java)
    }
}
