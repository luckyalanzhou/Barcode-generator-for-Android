package com.luckyalanzhou.barcodegenerator

import android.content.Intent
import android.net.Uri
import java.util.concurrent.CancellationException

/** 迁移时只把普通平台异常作为可重试失败；取消和 JVM 致命错误必须继续传播。 */
internal inline fun <T> captureRecoverableDocumentUriGrantOperation(operation: () -> T): Result<T> =
    try {
        Result.success(operation())
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        Result.failure(error)
    }

/** 旧版局域网附件流程遗留的持久化文档授权。 */
internal data class PersistedDocumentUriGrant(
    val uri: Uri,
    val canRead: Boolean,
    val canWrite: Boolean,
)

/** 逐条释放旧授权；单条失败不阻止清理其他 URI，返回失败数量供迁移门闩决定是否重试。 */
internal fun releasePersistedDocumentUriGrants(
    grants: List<PersistedDocumentUriGrant>,
    release: (Uri, Int) -> Unit,
): Int {
    var failures = 0
    grants.forEach { grant ->
        val modeFlags = (if (grant.canRead) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (grant.canWrite) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        if (modeFlags == 0) return@forEach
        captureRecoverableDocumentUriGrantOperation { release(grant.uri, modeFlags) }
            .onFailure { failures++ }
    }
    return failures
}
