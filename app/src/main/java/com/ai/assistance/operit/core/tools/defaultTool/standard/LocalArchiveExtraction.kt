package com.ai.assistance.operit.core.tools.defaultTool.standard

import java.nio.file.*
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.*
import java.util.zip.ZipInputStream

/** 在私有暂存目录完整校验后一次发布；错误包不能留下看似成功的半个目录。 */
internal fun extractManagedZip(
    source: Path, destination: Path, commit: (Path, Path) -> Unit,
    checkActive: () -> Unit = {}, maxBytes: Long = 8L * 1024 * 1024 * 1024,
    maxEntries: Int = 100_000,
) {
    val before = inspectManagedTree(source, checkActive)
    require(before.entries.size == 1 && before.entries.single().attributes.isRegularFile) { "请选择 ZIP 文件" }
    // ZipInputStream 单独使用会接受缺失中央目录的截断包；先要求 ZIP 中央目录完整。
    val expectedEntries = java.util.zip.ZipFile(source.toFile()).use { it.size() }
    require(expectedEntries <= maxEntries) { "压缩包项目过多" }
    val parent = destination.parent.toRealPath()
    val target = parent.resolve(destination.fileName)
    require(!Files.exists(target, NOFOLLOW_LINKS)) { "目标已存在，请更换文件夹名称" }
    val staging = Files.createTempDirectory(parent, ".kiyori-extract-")
    val payload = Files.createDirectory(staging.resolve("payload"))
    var failure: Throwable? = null
    try {
        var count = 0
        var total = 0L
        Files.newInputStream(source, READ, NOFOLLOW_LINKS).use { stream ->
            // 拒绝把任意非 ZIP 文件解释成空压缩包。
            val input = java.io.BufferedInputStream(stream)
            input.mark(4)
            val signature = ByteArray(4)
            require(input.read(signature) == 4 && signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte() &&
                ((signature[2] == 3.toByte() && signature[3] == 4.toByte()) ||
                    (signature[2] == 5.toByte() && signature[3] == 6.toByte()))) { "仅支持未加密的 ZIP 压缩包" }
            input.reset()
            ZipInputStream(input).use { zip ->
                val seen = hashSetOf<String>()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkActive()
                    val entry = zip.nextEntry ?: break
                    require(++count <= maxEntries) { "压缩包项目过多" }
                    val name = entry.name.removeSuffix("/")
                    require(name.isNotBlank() && !name.startsWith('/') && '\\' !in name && ':' !in name &&
                        name.split('/').none { it.isEmpty() || it == "." || it == ".." } &&
                        name.split('/').size <= 128) { "压缩包包含不安全路径" }
                    require(seen.add(name)) { "压缩包包含重复路径" }
                    val output = payload.resolve(name).normalize()
                    require(output.startsWith(payload)) { "解压路径越界" }
                    if (entry.isDirectory) Files.createDirectories(output) else {
                        Files.createDirectories(output.parent)
                        Files.newOutputStream(output, CREATE_NEW, WRITE).use { out ->
                            while (true) {
                                checkActive()
                                val n = zip.read(buffer)
                                if (n < 0) break
                                total += n
                                require(total <= maxBytes) { "解压内容超过 8 GiB 限制" }
                                out.write(buffer, 0, n)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        require(count == expectedEntries) { "ZIP 条目与中央目录不一致" }
        check(inspectManagedTree(source, checkActive).fingerprint == before.fingerprint) { "解压期间压缩包发生变化" }
        checkActive()
        commit(payload, target)
    } catch (error: Throwable) { failure = error }
    try {
        // 只清理本次创建的私有树；Files.walk 默认不跟随符号链接。
        Files.walk(staging).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
    } catch (cleanup: Exception) {
        throw LocalCopyException(FileCopyErrorCode.FAILED, "请检查解压结果与未清理的暂存位置", staging.toString(), failure ?: cleanup)
    }
    failure?.let { throw it }
}
