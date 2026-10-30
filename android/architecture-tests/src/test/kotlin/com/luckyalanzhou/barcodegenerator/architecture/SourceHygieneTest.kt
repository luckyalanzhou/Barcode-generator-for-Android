package com.luckyalanzhou.barcodegenerator.architecture

import com.lemonappdev.konsist.api.Konsist
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** 检查源码维护问题，不依赖控件命名或 UI 具体实现。 */
class SourceHygieneTest {
    @Test fun `production sources do not repeat imports`() {
        val violations = Konsist.scopeFromProduction().files.flatMap { source ->
            File(source.path).readLines().filter { it.startsWith("import ") }
                .groupingBy { it.trim() }.eachCount().filterValues { it > 1 }.keys
                .map { "${source.path}: $it" }
        }
        assertTrue("重复导入：\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test fun `local environment secrets stay ignored while example templates remain trackable`() {
        val ignoreRules = File("../.gitignore").readLines().map(String::trim)

        assertTrue("本地 .env 文件必须忽略", ".env" in ignoreRules)
        assertTrue("带后缀的本地环境文件必须忽略", ".env.*" in ignoreRules)
        assertTrue("示例环境模板应可提交", "!.env.example" in ignoreRules)
    }

    @Test fun `local AI instructions session state and generated critiques are ignored`() {
        val ignoreRules = File("../.gitignore").readLines().map(String::trim)
        val localAiRules = listOf(
            "AGENTS.md",
            "CLAUDE.md",
            "GEMINI.md",
            ".codex/",
            ".codex-remote-attachments/",
            ".claude/",
            ".cursor/",
            ".gemini/",
            ".impeccable/critique/",
        )

        assertTrue("本地 AI 文件规则缺失：${localAiRules - ignoreRules.toSet()}", localAiRules.all { it in ignoreRules })
        assertTrue("本地 AI 规则不应只匹配仓库根目录", localAiRules.none { it.startsWith("/") })
    }
}
