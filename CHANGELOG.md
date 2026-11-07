<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Better-Brackets Changelog

## [Unreleased]

## 0.1.0
### Added
- **多层级括号色彩映射 (Nested Bracket Coloring)**
  - 支持圆括号 `( )`、方括号 `[ ]`、花括号 `{ }` 与泛型尖括号 `< >` 根据嵌套深度自适应循环着色。
  - 基于 JetBrains 原生 AST / PSI 语法深度解析，精准识别代码结构，避免对字符串内容与注释的误匹配。
- **深度多语言支持 (Multi-Language Support)**
  - **Java**：覆盖方法调用、控制流条件、强制类型转换、数组访问、代码块与多级泛型列表。
  - **Kotlin**：覆盖函数体、Lambda 闭包、解构赋值、索引访问、类型实参与泛型约束。
  - **C / C++**：原生集成 CLion 与 Android Studio 宿主环境，动态适配原生环境与语法特性。
  - **XML / HTML**：标签名与尖括号 `<tag>...</tag>` 一体化配对着色。
  - **JavaScript / TypeScript**：覆盖对象字面量、解构表达式、计算属性与 TS 泛型参数。
  - **CSS**：覆盖媒体查询、伪类、`calc`/`var` 函数、属性选择器与样式声明块。
  - **Vue (SFC)**：单文件组件跨区协同，独创 Mustache 双花括号插值 `{{ }}` 专属着色。
  - **YAML**：覆盖行内列表与端口映射 `[ ]`、行内对象与环境配置 `{ }` 专属着色，自适应层级垂直缩进线与作用域高亮。
- **双重视觉辅助增强 (Visual Aids)**
  - **层级缩进引导线 (Indent Guide)**：根据代码块嵌套深度渲染对应的垂直对齐引导线。
  - **当前作用域高亮 (Scope Highlighting)**：随光标位置实时定位并高亮当前所在代码块或标签边界。
- **调色板与实时预览 (Palette & Live Preview)**
  - **功能设置 (Function)**：各语言提供独立的全局与类型细分开关。
  - **颜色定制 (Color)**：提供可视化色块选择器，支持悬停删除与智能梯度补色。
  - **内嵌实时预览编辑器**：参数调整即刻反馈至预览窗口，所见即所得。
