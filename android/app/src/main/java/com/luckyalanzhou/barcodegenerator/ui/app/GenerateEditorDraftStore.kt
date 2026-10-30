package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.presentation.GenerateEditorState
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

/** 保存超过 Bundle 预算的生成草稿，文件只放在应用私有、不会进入备份的目录中。 */
internal class GenerateEditorDraftStore(
    noBackupFilesDir: File,
    draftId: String? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val directory = File(noBackupFilesDir, DIRECTORY_NAME)
    private val draftFileName = draftId?.let {
        require(isValidDraftId(it)) { "生成草稿 ID 无效" }
        "$INSTANCE_FILE_PREFIX$it.draft"
    } ?: FILE_NAME
    internal val draftFile = File(directory, draftFileName)
    private val backupFile = File(draftFile.path + ".bak")
    private val newFile = File(draftFile.path + ".new")
    /** 写入成功后才返回 true；失败草稿不会被 Bundle 标记为可恢复。 */
    fun save(state: GenerateEditorState): Boolean {
        try {
            if (state.inputDraft.size > MAX_SAVED_ROWS || serializedSize(state) > MAX_DRAFT_FILE_BYTES) return false
            if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) return false

            recoverInterruptedWrite()
            deleteQuietly(newFile)
            FileOutputStream(newFile).use { output ->
                val data = DataOutputStream(BufferedOutputStream(output))
                data.writeInt(FILE_MAGIC)
                data.writeInt(FILE_VERSION)
                data.writeInt(state.inputDraft.size)
                writeString(data, state.formatName)
                state.inputDraft.forEach { writeString(data, it) }
                data.flush()
                output.fd.sync()
            }

            if (draftFile.exists()) {
                if (backupFile.exists() && !backupFile.delete()) return false
                if (!draftFile.renameTo(backupFile)) return false
            }
            if (!newFile.renameTo(draftFile)) {
                if (!draftFile.exists() && backupFile.exists()) backupFile.renameTo(draftFile)
                return false
            }
            draftFile.setLastModified(clock())
            deleteQuietly(backupFile)
            return true
        } catch (failure: Exception) {
            deleteQuietly(newFile)
            if (!draftFile.exists() && backupFile.exists()) backupFile.renameTo(draftFile)
            return false
        }
    }

    /** 文件校验失败、过期或格式未知时删除它，不把损坏内容交给生成页。 */
    fun restore(): GenerateEditorDraft? {
        return try {
            if (deleteCurrentDraftIfExpired(clock())) return null
            val candidates = listOf(draftFile, backupFile, newFile)
            val restored = candidates.firstNotNullOfOrNull { file -> readDraftFile(file)?.let { file to it } }
            if (restored == null) {
                delete()
                return null
            }
            val (source, draft) = restored
            if (source != draftFile) {
                if (draftFile.exists() && !draftFile.delete()) return draft
                if (!source.renameTo(draftFile)) return draft
            }
            deleteQuietly(backupFile)
            deleteQuietly(newFile)
            draftFile.setLastModified(clock())
            draft
        } catch (_: Exception) {
            delete()
            null
        }
    }

    /** Activity 创建或保存状态时调用；清理超过 24 小时未访问的文件。 */
    fun cleanupExpired(nowMillis: Long = clock()) {
        val files = directory.listFiles()?.toList() ?: return
        val managedDraftNames = files.mapNotNull { file -> managedDraftName(file.name) }.distinct()
        managedDraftNames.forEach { name ->
            val baseFile = File(directory, name)
            val artifacts = listOf(baseFile, File(directory, "$name.bak"), File(directory, "$name.new"))
                .filter(File::exists)
            if (artifacts.isEmpty()) return@forEach
            val mostRecentWrite = artifacts.maxOf(File::lastModified)
            if (mostRecentWrite <= 0L || nowMillis - mostRecentWrite > EXPIRATION_MILLIS) {
                deleteQuietly(baseFile)
                deleteQuietly(File(directory, "$name.bak"))
                deleteQuietly(File(directory, "$name.new"))
            }
        }
    }

    /** 恢复时只检查当前 Activity 的主文件与替换副本，避免同步扫描整份目录。 */
    private fun deleteCurrentDraftIfExpired(nowMillis: Long): Boolean {
        val artifacts = listOf(draftFile, backupFile, newFile).filter(File::exists)
        if (artifacts.isEmpty()) return false
        val mostRecentWrite = artifacts.maxOf(File::lastModified)
        if (mostRecentWrite > 0L && nowMillis - mostRecentWrite <= EXPIRATION_MILLIS) return false
        delete()
        return true
    }

    /** 只删除本存储器拥有的草稿及其替换副本。 */
    fun delete() {
        deleteQuietly(draftFile)
        deleteQuietly(backupFile)
        deleteQuietly(newFile)
    }

    /** 优先保留完整主文件；主文件损坏或缺失时，回滚到可解析的旧版 AtomicFile 备份。 */
    private fun recoverInterruptedWrite() {
        if (readDraftFile(draftFile) != null) {
            deleteQuietly(backupFile)
            deleteQuietly(newFile)
            return
        }
        if (readDraftFile(backupFile) != null) {
            if (draftFile.exists() && !draftFile.delete()) throw IOException("无法移除损坏的生成草稿")
            if (!backupFile.renameTo(draftFile)) throw IOException("无法恢复生成草稿备份")
            deleteQuietly(newFile)
            return
        }
        if (draftFile.exists()) deleteQuietly(draftFile)
        deleteQuietly(backupFile)
        deleteQuietly(newFile)
    }

    private fun deleteQuietly(file: File) {
        try {
            file.delete()
        } catch (_: Exception) {
            // 私有草稿清理失败不应遮蔽当前输入或原始写入错误。
        }
    }

    private fun readDraftFile(file: File): GenerateEditorDraft? {
        if (!file.isFile || file.length() !in 1..MAX_DRAFT_FILE_BYTES.toLong()) return null
        return try {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                if (input.readInt() != FILE_MAGIC) throw IOException("未知的生成草稿文件")
                if (input.readInt() != FILE_VERSION) throw IOException("不支持的生成草稿版本")
                val rowCount = input.readInt()
                if (rowCount !in 0..MAX_SAVED_ROWS) throw IOException("生成草稿行数无效")
                val format = readString(input, MAX_FORMAT_NAME_BYTES)
                var decodedBytes = format.toByteArray(Charsets.UTF_8).size.toLong()
                val values = ArrayList<String>(rowCount)
                repeat(rowCount) {
                    val value = readString(input, MAX_DRAFT_FILE_BYTES)
                    decodedBytes += value.toByteArray(Charsets.UTF_8).size
                    if (decodedBytes > MAX_DRAFT_FILE_BYTES) throw IOException("生成草稿超过文件上限")
                    values += value
                }
                if (input.read() != -1) throw IOException("生成草稿包含多余数据")
                GenerateEditorDraft(values, format)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun serializedSize(state: GenerateEditorState): Long =
        FILE_HEADER_BYTES + utf8Size(state.formatName) + state.inputDraft.sumOf { ROW_LENGTH_BYTES + utf8Size(it) }

    private fun writeString(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readString(input: DataInputStream, maxBytes: Int): String {
        val byteCount = input.readInt()
        if (byteCount !in 0..maxBytes) throw IOException("生成草稿字段长度无效")
        val bytes = ByteArray(byteCount)
        try {
            input.readFully(bytes)
        } catch (failure: EOFException) {
            throw IOException("生成草稿内容不完整", failure)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (failure: Exception) {
            throw IOException("生成草稿不是有效 UTF-8", failure)
        }
    }

    private fun utf8Size(value: String): Long = value.toByteArray(Charsets.UTF_8).size.toLong()

    companion object {
        internal const val DIRECTORY_NAME = "generate-editor-drafts"
        internal const val FILE_NAME = "current.draft"
        internal const val EXPIRATION_MILLIS = 24L * 60L * 60L * 1_000L
        internal const val MAX_DRAFT_FILE_BYTES = 8 * 1024 * 1024
        internal const val MAX_SAVED_ROWS = 1_000
        private const val INSTANCE_FILE_PREFIX = "activity-"

        internal fun newDraftId(): String = UUID.randomUUID().toString()

        internal fun isValidDraftId(value: String): Boolean = runCatching {
            UUID.fromString(value).toString() == value
        }.getOrDefault(false)

        private fun managedDraftName(fileName: String): String? {
            val baseName = when {
                fileName.endsWith(".bak") -> fileName.removeSuffix(".bak")
                fileName.endsWith(".new") -> fileName.removeSuffix(".new")
                else -> fileName
            }
            return baseName.takeIf {
                it == FILE_NAME ||
                    (it.startsWith(INSTANCE_FILE_PREFIX) && it.endsWith(".draft") &&
                        isValidDraftId(it.removePrefix(INSTANCE_FILE_PREFIX).removeSuffix(".draft")))
            }
        }

        private const val FILE_MAGIC = 0x42474431 // BGD1
        private const val FILE_VERSION = 1
        private const val MAX_FORMAT_NAME_BYTES = 1_024
        private const val FILE_HEADER_BYTES = 16L
        private const val ROW_LENGTH_BYTES = 4L
    }
}

/** 仅包含进程恢复需要的输入和格式，不包含 pendingFormat 等瞬时编辑信号。 */
internal data class GenerateEditorDraft(
    val inputs: List<String>,
    val formatName: String,
)
