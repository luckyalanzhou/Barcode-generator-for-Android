package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Beta Release 的元数据、制品与固定 tag 必须由单个可回滚事务发布。 */
class BetaReleaseTagSourceContractTest {
    @Test
    fun betaReleaseUsesTheCompensatingTransactionEntryPoint() {
        val workflow = File("../.github/workflows/build-android-testing.yml").readText()
            .replace("\r\n", "\n")
        val publishJobMarker = "\n  publish:\n"
        assertTrue("Beta 发布 job 必须存在", workflow.contains(publishJobMarker))
        val publishJobRemainder = workflow.substringAfter(publishJobMarker)
        val nextJobIndex = Regex("\n  [A-Za-z0-9_-]+:\n").find(publishJobRemainder)?.range?.first
        val publishJob = nextJobIndex?.let { publishJobRemainder.substring(0, it) } ?: publishJobRemainder
        val transactionCommand = "python3 android/tools/beta_release_transaction.py"

        assertTrue(
            "Beta 发布必须唯一调用补偿事务入口",
            publishJob.windowed(transactionCommand.length).count { it == transactionCommand } == 1,
        )
        assertTrue("发布 job 不再拆分执行不可回滚的 Release 上传或 tag 移动", !publishJob.contains("softprops/action-gh-release"))
        assertTrue("元数据和固定 tag 不能由独立步骤更新", !publishJob.contains("gh release edit") &&
            !publishJob.contains("git/refs/tags/android-test-v9.9.9"))

        val transactionIndex = publishJob.indexOf(transactionCommand)
        assertTrue("事务入口必须位于一个工作流步骤内", transactionIndex >= 0)
        val checkoutAction = "uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803"
        val checkoutIndex = publishJob.indexOf(checkoutAction)
        assertTrue("发布 job 必须 checkout 事务脚本所在的源码", checkoutIndex >= 0)
        assertTrue("发布 job 必须先 checkout 源码再调用事务脚本", checkoutIndex < transactionIndex)
        val checkoutStepStart = publishJob.lastIndexOf("      - name:", checkoutIndex)
        val checkoutStepEnd = publishJob.indexOf("\n      - name:", checkoutIndex)
            .takeIf { it >= 0 } ?: publishJob.length
        val checkoutStep = publishJob.substring(checkoutStepStart, checkoutStepEnd)
        assertTrue("发布 job checkout 不应持久化发布凭据", checkoutStep.contains("persist-credentials: false"))
        val transactionStepStart = publishJob.lastIndexOf("      - name:", transactionIndex)
        assertTrue("事务步骤必须有明确名称", transactionStepStart >= 0)
        val transactionLines = publishJob.substring(transactionStepStart)
            .substringBefore("\n      - name:")

        assertTrue("事务步骤必须调用有 loopback 回归覆盖的 Python 发布入口", transactionLines.contains("python3 android/tools/beta_release_transaction.py"))
        assertTrue("事务必须使用固定 Beta tag", transactionLines.contains("--tag \"android-test-v9.9.9\""))
        assertTrue("事务必须读取当前构建 APK", transactionLines.contains("--asset \"beta-release/BarcodeGeneratorBeta\${VERSION_NAME}.apk\""))
        assertTrue("事务必须校验当前构建 APK 的 SHA-256", transactionLines.contains("--sha256 \"\$SHA256\""))
        assertTrue("发布凭据必须仅传给事务步骤", transactionLines.contains("GITHUB_TOKEN: \${{ secrets.RELEASE_PUBLISH_TOKEN }}"))
        assertTrue("事务失败不得被条件跳过或忽略", !transactionLines.contains("\n        if:") &&
            !transactionLines.contains("\n        continue-on-error:"))
    }
}
