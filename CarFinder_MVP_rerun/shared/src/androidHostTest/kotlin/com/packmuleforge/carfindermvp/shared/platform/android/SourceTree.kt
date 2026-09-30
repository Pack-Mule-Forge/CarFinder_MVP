package com.packmuleforge.carfindermvp.shared.platform.android

import java.io.File

/** Resolves repository paths for source-scan tests. Host tests run with the module directory as working dir. */
internal object SourceTree {
    val repoRoot: File = File("").absoluteFile.let { if (File(it, "settings.gradle.kts").exists()) it else it.parentFile }

    fun kotlinFiles(dir: File): List<File> =
        if (!dir.exists()) emptyList() else dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    fun relative(file: File): String = file.relativeTo(repoRoot).invariantSeparatorsPath
}
