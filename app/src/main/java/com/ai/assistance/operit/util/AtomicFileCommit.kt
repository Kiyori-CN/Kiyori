package com.ai.assistance.operit.util

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val ATOMIC_FILE_COMMIT_LOCK_STRIPES = 64
private val atomicFileCommitLocks = Array(ATOMIC_FILE_COMMIT_LOCK_STRIPES) { Any() }

internal fun commitFileAtomicallyWithoutReplacement(
    stagedFile: File,
    targetFile: File,
) {
    val stagedParent = requireNotNull(stagedFile.parentFile).canonicalFile
    val targetParent = requireNotNull(targetFile.parentFile).canonicalFile
    require(stagedParent == targetParent) {
        "Atomic commit requires source and target in the same directory"
    }
    val canonicalTargetPath = targetFile.canonicalPath
    val commitLock =
        atomicFileCommitLocks[
            Math.floorMod(
                canonicalTargetPath.hashCode(),
                atomicFileCommitLocks.size,
            )
        ]

    synchronized(commitLock) {
        require(stagedFile.isFile && stagedFile.length() > 0L) {
            "Atomic commit source is missing or empty: ${stagedFile.path}"
        }
        require(!targetFile.exists()) {
            "Atomic commit target already exists: ${targetFile.path}"
        }

        // ATOMIC_MOVE is the commit point. The striped lock closes same-process target races;
        // an unsupported filesystem must fail explicitly because copying or a non-atomic rename
        // could expose a partial media file as a completed output.
        Files.move(
            stagedFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
        )
        check(targetFile.isFile && targetFile.length() > 0L && !stagedFile.exists()) {
            "Atomic commit did not produce the expected target"
        }
    }
}
