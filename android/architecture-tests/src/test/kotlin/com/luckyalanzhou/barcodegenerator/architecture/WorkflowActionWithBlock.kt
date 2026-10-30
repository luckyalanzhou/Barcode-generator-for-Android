package com.luckyalanzhou.barcodegenerator.architecture

/** 从工作流中提取指定 Action 的 with 配置，避免误读其他步骤的同名字段。 */
internal fun workflowActionWithBlock(workflow: String, action: String): String? =
    workflow.split(Regex("\\r?\\n")).let { lines ->
        val actionLine = lines.indexOfFirst {
            Regex("^ {8}uses:\\s*${Regex.escape(action)}@").containsMatchIn(it)
        }
        if (actionLine < 0) return null

        val stepBoundary = Regex("^ {6}-\\s+.*$")
        val stepStart = (actionLine downTo 0).firstOrNull { stepBoundary.matches(lines[it]) }
            ?: return null
        val stepEnd = (actionLine + 1 until lines.size)
            .firstOrNull { stepBoundary.matches(lines[it]) }
            ?: lines.size
        val step = lines.subList(stepStart, stepEnd)

        val withIndex = step.indexOfFirst { Regex("^ {8}with:\\s*$").matches(it) }
        if (withIndex < 0) return null
        val nextStepProperty = Regex("^ {8}\\S.*$")
        val withEnd = (withIndex + 1 until step.size)
            .firstOrNull { nextStepProperty.matches(step[it]) }
            ?: step.size
        step.subList(withIndex + 1, withEnd).joinToString("\n")
    }
