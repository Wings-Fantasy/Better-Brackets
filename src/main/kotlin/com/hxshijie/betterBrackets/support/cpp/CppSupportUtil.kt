package com.hxshijie.betterBrackets.support.cpp

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId

/**
 * C/C++ 语言环境支持工具。
 */
object CppSupportUtil {

    /**
     * 判断当前宿主环境是否支持 C/C++。
     */
    fun isCppSupportedEnvironment(): Boolean {
        val platformPrefix = System.getProperty("idea.platform.prefix") ?: ""
        val productCode = runCatching { ApplicationInfo.getInstance().build.productCode }.getOrDefault("")

        val isCLion = platformPrefix.equals("CLion", ignoreCase = true) || productCode.equals("CL", ignoreCase = true)
        val isAndroidStudio = platformPrefix.equals("AndroidStudio", ignoreCase = true) || productCode.equals("AI", ignoreCase = true)

        return isCLion ||
                isAndroidStudio ||
                isPluginLoaded("com.intellij.cidr.lang") ||
                isPluginLoaded("com.intellij.clion") ||
                isPluginLoaded("com.intellij.modules.clion")
    }

    /**
     * 安全检查指定 ID 的插件是否已安装。
     * 检查插件是否已安装，通过反射获取 PluginId 以保证跨版本兼容。
     */
    private fun isPluginLoaded(idString: String): Boolean {
        val pluginId = runCatching {
            val method = PluginId::class.java.getMethod("getId", String::class.java)
            method.invoke(null, idString) as? PluginId
        }.getOrNull() ?: return false

        return PluginManagerCore.isPluginInstalled(pluginId)
    }

    /**
     * 判断 PSI 节点是否为语句或函数定义等外层容器节点（而非纯复合代码块）。
     */
    fun isStatementOrDefinitionContainer(className: String, elementType: String): Boolean {
        val name = className.lowercase()
        val type = elementType.lowercase()

        if (name.contains("compoundstatement") || type.contains("compound_statement")) {
            return false
        }

        return name.contains("function") || name.contains("method") ||
                name.contains("ifstatement") || name.contains("loop") ||
                name.contains("forstatement") || name.contains("whilestatement") ||
                name.contains("switchstatement") || name.contains("trystatement") ||
                name.contains("catchstatement") ||
                type.contains("function_def") || type.contains("if_statement")
    }
}
