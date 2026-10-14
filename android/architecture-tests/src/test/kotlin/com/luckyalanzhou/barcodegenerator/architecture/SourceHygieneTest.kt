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
}
