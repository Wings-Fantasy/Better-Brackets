package com.hxshijie.betterBrackets.settings.configurable.ui

import com.hxshijie.betterBrackets.bundle.BetterBracketsBundle
import com.hxshijie.betterBrackets.core.BracePairProvider
import com.hxshijie.betterBrackets.core.ColorCalculator
import com.hxshijie.betterBrackets.settings.BetterBracketsState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.PsiFile
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.ui.JBUI
import java.awt.Dimension

/**
 * 设置页面代码预览编辑器。
 */
open class BracketPreviewEditor(
    val languageId: String,
    val extension: String = getExtension(languageId),
    val previewCode: String = getPreviewCode(languageId),
    var stateProvider: () -> BetterBracketsState = { BetterBracketsState.instance }
) : EditorTextField(
    EditorFactory.getInstance().createDocument(previewCode),
    null,
    resolveFileType(extension),
    true,
    false
) {
    var activeEditor: Editor? = null
        private set

    init {
        preferredSize = JBUI.size(-1, 165)
        minimumSize = Dimension(0, JBUI.scale(100))
        addSettingsProvider { editor ->
            activeEditor = editor
            editor.putUserData(STATE_PROVIDER_KEY, stateProvider)
            editor.settings.apply {
                isLineNumbersShown = true
                isFoldingOutlineShown = false
                isRightMarginShown = false
                isCaretRowShown = false
                additionalLinesCount = 0
            }
            editor.setVerticalScrollbarVisible(true)
            editor.setHorizontalScrollbarVisible(true)

            val initialCaretOffset = findInitialCaretOffset(previewCode)
            if (initialCaretOffset >= 0) {
                editor.caretModel.moveToOffset(initialCaretOffset)
            }

            applyBracketHighlighting(editor)
        }
    }

    /**
     * 刷新括号渲染与高亮
     */
    fun refreshHighlighting() {
        activeEditor?.let { editor ->
            applyBracketHighlighting(editor)
            editor.contentComponent.repaint()
        }
        repaint()
    }

    open fun applyBracketHighlighting(editor: Editor) {
        editor.putUserData(STATE_PROVIDER_KEY, stateProvider)
        val state = stateProvider()
        val project = ProjectManager.getInstance().defaultProject

        ApplicationManager.getApplication().runReadAction {
            val oldHighlighters = editor.getUserData(PREVIEW_BRACKET_HIGHLIGHTERS_KEY)
            oldHighlighters?.forEach { it.dispose() }
            val newHighlighters = mutableListOf<RangeHighlighter>()

            val psiFile = getOrCreatePsiFile(project)

            val provider = BracePairProvider.braceSupportExtensionPoint.extensionList
                .firstOrNull { it.isSupported(psiFile) }

            if (provider != null) {
                if (provider.isEnabled(state)) {
                    psiFile.accept(object : PsiRecursiveElementWalkingVisitor() {
                        override fun visitElement(element: PsiElement) {
                            val match = provider.findBraceMatch(element, state)
                            if (match != null) {
                                val attributes = ColorCalculator.getTextAttributes(match.depth, match.colors)
                                val highlighter = editor.markupModel.addRangeHighlighter(
                                    element.textRange.startOffset,
                                    element.textRange.endOffset,
                                    HighlighterLayer.SYNTAX,
                                    attributes,
                                    HighlighterTargetArea.EXACT_RANGE
                                )
                                newHighlighters.add(highlighter)
                            }
                            super.visitElement(element)
                        }
                    })
                }
                provider.applyPreviewExtras(editor, psiFile, state)
            }
            editor.putUserData(PREVIEW_BRACKET_HIGHLIGHTERS_KEY, newHighlighters)
        }
    }

    private var cachedPsiFile: PsiFile? = null

    private fun getOrCreatePsiFile(project: Project): PsiFile {
        return cachedPsiFile ?: PsiFileFactory.getInstance(project).createFileFromText(
            "Preview.$extension",
            resolveFileType(extension),
            previewCode
        ).also { cachedPsiFile = it }
    }

    private fun findInitialCaretOffset(code: String): Int {
        val markers = listOf("System.out", "grid[0][0]", "const config", "return items", "transform:", "<span>Better Brackets</span>", "{{ user.profile.name }}", "environment:", "\"parameters\": {")
        for (marker in markers) {
            val idx = code.indexOf(marker)
            if (idx >= 0) return idx
        }
        return -1
    }

    data class LanguagePreviewDefinition(
        val extension: String,
        val code: String
    )

    companion object {
        val STATE_PROVIDER_KEY: Key<() -> BetterBracketsState> =
            Key.create("BETTER_BRACKETS_PREVIEW_STATE_PROVIDER_KEY")

        private val PREVIEW_BRACKET_HIGHLIGHTERS_KEY =
            Key.create<MutableList<RangeHighlighter>>("BETTER_BRACKETS_PREVIEW_BRACKET_HIGHLIGHTERS")

        val PREVIEW_DEFINITIONS: Map<String, LanguagePreviewDefinition> = mapOf(
            "JAVA" to LanguagePreviewDefinition(
                extension = "java",
                code = """
                    public class BracketPreview<T> {
                        public void compute(List<List<String>> list) {
                            int[][] matrix = {{1, 2}, {3, 4}};
                            if ((((matrix[0][0] + 1) * 2) - 3) > 0) {
                                System.out.println(list.get(0));
                            }
                        }
                    }
                """.trimIndent()
            ),
            "KOTLIN" to LanguagePreviewDefinition(
                extension = "kt",
                code = """
                    class BracketPreview<T : Map<String, List<List<Int>>>> {
                        fun compute(matrix: Array<Array<Int>>) {
                            if ((((matrix[0][0] + 1) * 2) - 3) > 0) {
                                listOf(matrix).forEach { row ->
                                    row[matrix[0][0]].let { println("val: ${'$'}{it[0] + (1 * (2 + 3))}") }
                                }
                            }
                        }
                    }
                """.trimIndent()
            ),
            "CPP" to LanguagePreviewDefinition(
                extension = "cpp",
                code = """
                    template <typename T>
                    class MatrixProcessor {
                    public:
                        void process(std::vector<std::vector<int>>& grid) {
                            int arr[2][2] = {{1, 2}, {3, 4}};
                            if ((((arr[0][0] + 1) * 2) - 3) > 0) {
                                grid[0][0] = (arr[1][1] + (10 * (2 + 3)));
                            }
                        }
                    };
                """.trimIndent()
            ),
            "HTML" to LanguagePreviewDefinition(
                extension = "html",
                code = """
                    <div class="container">
                        <section class="card">
                            <header>
                                <h1><span>Better Brackets</span></h1>
                            </header>
                            <p><strong>Better</strong> Tag Highlight</p>
                        </section>
                    </div>
                """.trimIndent()
            ),
            "JS" to LanguagePreviewDefinition(
                extension = "js",
                code = """
                    function transformData(items) {
                        const matrix = [[1, 2], [3, [4, 5]]];
                        if ((((matrix[0][0] + 1) * 2) - 3) > 0) {
                            return items.map(item => {
                                const config = {
                                    metadata: {
                                        id: item.id,
                                        val: (item.val * (2 + 1))
                                    }
                                };
                                return config;
                            });
                        }
                    }
                """.trimIndent()
            ),
            "CSS" to LanguagePreviewDefinition(
                extension = "css",
                code = """
                    @media screen and (min-width: calc(768px + (2 * 16px))) {
                        @supports (display: grid) {
                            .container[data-theme="dark"][data-active="true"] {
                                width: calc(100% - ((20px + 10px) * 2));
                                grid-template-columns: repeat(3, minmax(0, 1fr));
                                transform: translate3d(0, calc(10px * (1 + 2)), 0);
                            }
                        }
                    }
                """.trimIndent()
            ),
            "VUE" to LanguagePreviewDefinition(
                extension = "vue",
                code = """
                    <template>
                        <div class="user-card" :class="{ active: isActive }">
                            <header class="card-header">
                                <h3>{{ user.profile.name }}</h3>
                            </header>
                            <main class="card-body">
                                <p>{{ formatRole(user.role) }}</p>
                            </main>
                        </div>
                    </template>

                    <script>
                    export default {
                        data() {
                            return {
                                isActive: true
                            };
                        },
                        methods: {
                            formatRole(role) {
                                return role ? role.toUpperCase() : "GUEST";
                            }
                        }
                    };
                    </script>

                    <style scoped>
                    .user-card {
                        display: flex;
                        .card-header {
                            color: #333333;
                        }
                    }
                    </style>
                """.trimIndent()
            ),
            "YAML" to LanguagePreviewDefinition(
                extension = "yml",
                code = """
                    services:
                      sillytavern:
                        build: ..
                        container_name: sillytavern
                        hostname: sillytavern
                        image: ghcr.io/sillytavern/sillytavern:latest
                        environment: { NODE_ENV: production, FORCE_COLOR: "1", SILLYTAVERN_HEARTBEATINTERVAL: 30 }
                        ports: ["8000:8000"]
                        volumes:
                          - { type: bind, source: "./config", target: "/home/node/app/config" }
                          - { type: bind, source: "./data", target: "/home/node/app/data" }
                          - "./plugins:/home/node/app/plugins"
                          - "./extensions:/home/node/app/public/scripts/extensions/third-party"
                        healthcheck: { test: ["CMD", "node", "src/healthcheck.js"], interval: 30s, timeout: 10s, start_period: 20s, retries: 3 }
                        restart: unless-stopped
                """.trimIndent()
            ),
            "JSON" to LanguagePreviewDefinition(
                extension = "json",
                code = """
                    [
                      {
                        "type": "function",
                        "function": {
                          "name": "schedule_meeting",
                          "description": "创建日历日程并邀请参会人员",
                          "parameters": {
                            "type": "object",
                            "properties": {
                              "title": { "type": "string" },
                              "attendees": {
                                "type": "array",
                                "items": {
                                  "type": "object",
                                  "properties": {
                                    "email": { "type": "string" },
                                    "role": { "type": "string", "enum": ["required", "optional"] }
                                  },
                                  "required": ["email", "role"]
                                }
                              }
                            },
                            "required": ["title", "attendees"]
                          }
                        }
                      }
                    ]
                """.trimIndent()
            )
        )

        fun getExtension(languageId: String): String =
            PREVIEW_DEFINITIONS[languageId.uppercase()]?.extension ?: "txt"

        fun getPreviewCode(languageId: String): String =
            PREVIEW_DEFINITIONS[languageId.uppercase()]?.code ?: ""

        private fun resolveFileType(extension: String) =
            FileTypeManager.getInstance().getFileTypeByExtension(extension)
                .takeIf { it != PlainTextFileType.INSTANCE && it !is UnknownFileType }
                ?: FileTypeManager.getInstance().getFileTypeByExtension("html")
    }
}

/**
 * 向 Kotlin UI DSL 面板中添加预览分组
 */
fun Panel.previewGroup(previewEditor: BracketPreviewEditor) {
    group(BetterBracketsBundle.message("settings.common.group.preview")) {
        row {
            cell(previewEditor).align(Align.FILL)
        }.resizableRow()
    }
}
