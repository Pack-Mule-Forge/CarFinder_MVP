package com.packmuleforge.carfindermvp.shared.platform.android

import java.io.File

/** Test helper for source-scan tests: finds the repository root and lists Kotlin files under it. */
internal object SourceTree {
    val root: File by lazy {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        dir ?: error("No settings.gradle.kts above ${System.getProperty("user.dir")}")
    }

    fun kotlinFiles(relativeDir: String): List<File> {
        val dir = File(root, relativeDir)
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    fun relativePath(file: File): String = file.relativeTo(root).invariantSeparatorsPath
}
