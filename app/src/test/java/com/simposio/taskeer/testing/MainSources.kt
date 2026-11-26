package com.simposio.taskeer.testing

import java.io.File

object MainSources {
    private val root: File = run {
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        generateSequence(userDir) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré app/src/main subiendo desde $userDir")
    }

    val kotlinRoot: File = File(root, "java/com/simposio/taskeer")

    fun kotlin(relativePath: String): String = File(kotlinRoot, relativePath).readText()

    fun kotlinFiles(relativeDirectory: String): List<File> =
        File(kotlinRoot, relativeDirectory).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    fun strings(): String = File(root, "res/values/strings.xml").readText()
}
