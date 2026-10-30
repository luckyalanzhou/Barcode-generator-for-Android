package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 确保 Beta 重跑可将当前 Actions run 身份交给版本分配器。 */
class BetaWorkflowVersionResumeContractTest {
    @Test
    fun quotedStepPolicyKeysCannotBypassTheDefaultSuccessGate() {
        assertFalse(
            hasDefaultSuccessGate(
                listOf(
                    "      - name: Test beta version allocation",
                    "        \"if\": false",
                    "        run: python3 -m unittest",
                ),
            ),
        )
        assertFalse(
            hasDefaultSuccessGate(
                listOf(
                    "      - name: Test beta version allocation",
                    "        'continue-on-error': true",
                    "        run: python3 -m unittest",
                ),
            ),
        )
        assertFalse(
            hasDefaultSuccessGate(
                listOf(
                    "      - name: Test beta version allocation",
                    "        <<: *conditional_step_policy",
                    "        run: python3 -m unittest",
                ),
            ),
        )
        assertFalse(
            hasDefaultSuccessGate(
                listOf(
                    "      - name: Test beta version allocation",
                    "        ? \"if\"",
                    "        : false",
                    "        run: python3 -m unittest",
                ),
            ),
        )
    }

    @Test
    fun betaReleaseLookupPassesCurrentWorkflowRunIdToVersionAllocator() {
        val workflow = File("../.github/workflows/build-android-testing.yml").readText()
        val releaseLookup = workflow.substringAfter("python3 tools/beta_release_version.py --properties")
            .substringBefore("version_name=")

        assertTrue(
            "查询已有 Beta Release 时必须传入 GITHUB_RUN_ID，以便同 run 重用已发布的 versionCode",
            releaseLookup.contains("--run-id \"\$GITHUB_RUN_ID\""),
        )
    }

    @Test
    fun betaWorkflowRunsVersionAllocatorTestsBeforePublishing() {
        val workflow = File("../.github/workflows/build-android-testing.yml").readText()
        val workflowLines = workflow.lines()
        val versionTests = "python3 -m unittest discover -s tools -p 'test_beta_release_*.py' -v"
        val jobsHeaderMatches = workflowLines.indices.filter { workflowLines[it] == "jobs:" }
        assertTrue("Beta 工作流必须唯一声明 jobs 区域", jobsHeaderMatches.size == 1)
        val jobsHeader = jobsHeaderMatches.single()
        val jobsEnd = (jobsHeader + 1 until workflowLines.size).firstOrNull { index ->
            val line = workflowLines[index]
            line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("#")
        } ?: workflowLines.size
        val buildJobMatches = (jobsHeader + 1 until jobsEnd).filter {
            workflowLines[it] == "  build:"
        }
        assertTrue("Beta 工作流必须唯一声明 build job", buildJobMatches.size == 1)
        val buildJobStart = buildJobMatches.single()
        val buildJobEnd = (buildJobStart + 1 until jobsEnd).firstOrNull { index ->
            val line = workflowLines[index]
            line.isNotBlank() && line.startsWith("  ") && !line.startsWith("   ") &&
                !line.trimStart().startsWith("#")
        } ?: jobsEnd
        val stepsHeaderMatches = (buildJobStart + 1 until buildJobEnd).filter {
            workflowLines[it] == "    steps:"
        }
        assertTrue("Beta build job 必须唯一声明步骤列表", stepsHeaderMatches.size == 1)
        val stepsHeader = stepsHeaderMatches.single()
        val stepsEnd = (stepsHeader + 1 until buildJobEnd).firstOrNull { index ->
            val line = workflowLines[index]
            line.isNotBlank() && line.startsWith("    ") && !line.startsWith("     ") &&
                !line.trimStart().startsWith("#")
        } ?: buildJobEnd
        val stepStarts = (stepsHeader + 1 until stepsEnd).filter {
            workflowLines[it].startsWith("      - ")
        }
        val testStepMatches = stepStarts.filter {
            workflowLines[it] == "      - name: Test beta release allocation and transaction"
        }
        val calculateVersionStepMatches = stepStarts.filter {
            workflowLines[it] == "      - name: Calculate beta version"
        }
        assertTrue("Beta 工作流必须唯一声明版本分配测试步骤", testStepMatches.size == 1)
        assertTrue("Beta 工作流必须唯一声明版本计算步骤", calculateVersionStepMatches.size == 1)
        val testStepStart = testStepMatches.single()
        val calculateVersionStepStart = calculateVersionStepMatches.single()
        fun stepLines(start: Int): List<String> {
            val end = stepStarts.firstOrNull { it > start } ?: stepsEnd
            return workflowLines.subList(start, end)
        }
        val testStep = stepLines(testStepStart)
        val calculateVersionStep = stepLines(calculateVersionStepStart)

        assertTrue(
            "版本分配测试命令必须是该步骤实际运行的命令",
            testStep.any { it == "        run: $versionTests" },
        )
        assertTrue(
            "版本分配测试不能被条件跳过或忽略失败",
            hasDefaultSuccessGate(testStep),
        )
        assertTrue(
            "版本分配测试步骤必须早于版本计算步骤",
            testStepStart < calculateVersionStepStart,
        )
        val runStepStarts = stepStarts.filter { start ->
            stepLines(start).any { it.startsWith("        run:") }
        }
        assertTrue(
            "版本分配测试必须是 Beta 工作流第一个实际执行 shell 命令的步骤",
            runStepStarts.firstOrNull() == testStepStart,
        )
        assertTrue(
            "版本计算步骤必须遵循默认成功门禁",
            hasDefaultSuccessGate(calculateVersionStep),
        )
        assertTrue(
            "版本计算步骤必须直接包含 shell 脚本",
            calculateVersionStep.any { it == "        run: |" },
        )
    }

    // 只识别步骤级 YAML 属性；对映射合并和显式键 fail-closed，避免隐藏门禁覆盖且忽略 run 脚本正文。
    private fun hasDefaultSuccessGate(stepLines: List<String>): Boolean =
        stepLines.none {
            val indentation = it.takeWhile { character -> character == ' ' }.length
            if (indentation != 8) {
                false
            } else {
                val key = it.substring(indentation).substringBefore(':').trim()
                    .removeSurrounding("\"").removeSurrounding("'")
                key == "if" || key == "continue-on-error" || key == "<<" || key.startsWith("?")
            }
        }
}
