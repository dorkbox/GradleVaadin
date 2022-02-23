package com.vaadin.flow.server.frontend

import dorkbox.executor.Executor
import dorkbox.gradleVaadin.ConsoleLog
import dorkbox.gradleVaadin.JsonPackageTools
import dorkbox.gradleVaadin.node.NodeInfo
import elemental.json.Json
import elemental.json.JsonObject
import nonapi.io.github.classgraph.utils.VersionFinder.OS
import org.gradle.api.GradleException
import org.slf4j.Logger
import java.io.File
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 *
 */
object Util {
    // REGARDING the current version of polymer.
    // see: com.vaadin.flow.server.frontend.NodeUpdater
    val POLYMER_VERSION = NodeUpdater.POLYMER_VERSION

    val logger = ConsoleLog(messagePreface = "\t\t")

    val MODULES_YAML = ".modules.yaml";

    // .vaadin/vaadin.json contains local installation data inside node_modules
    // This will hep us know to execute even when another developer has pushed
    // a new hash to the code repository.
    val INSTALL_HASH = ".vaadin/vaadin.json"

    val regex = "\\\\".toRegex()

    fun execDebug(ex: Executor) {
        println("\t\tExec: ${ex.getExecutable()}")
        println("\t\tWorking Dir: ${ex.getWorkingDirectory()}")

        println("\t\tEnvironment:")
        ex.environment.forEach {
            println("\t\t\t$it")
        }

        println("\t\tArguments:")
        ex.getArgs().forEach {
            println("\t\t\t$it")
        }
    }

    fun getGeneratedModules(directory: File, excludes: Set<String>): Set<String> {
        return NodeUpdater.getGeneratedModules(directory, excludes)
    }

    fun getJsonFileContent(packageFile: File): JsonObject {
        return try {
            NodeUpdater.getJsonFileContent(packageFile) ?: Json.createObject()
        } catch (e: Exception) {
            println("\tCould not read contents of file $packageFile")
            Json.createObject()
        }
    }

    fun disableVaadinStatistics(packageJson: JsonObject) {
        // for node_modules\@vaadin\vaadin-usage-statistics

        //or you can disable vaadin-usage-statistics for the project by adding
        //```
        //   "vaadin": { "disableUsageStatistics": true }
        //```
        //to your project `package.json` and running `npm install` again (remove `node_modules` if needed).
        //
        //You can verify this by checking that `vaadin-usage-statistics.js` contains an empty function.
        val vaadinKey = JsonPackageTools.getOrCreateKey(packageJson, NodeUpdater.VAADIN_DEP_KEY)
        JsonPackageTools.addDependency(vaadinKey, "disableUsageStatistics", true)
    }

    fun ensureDirectoryExists(frontendGeneratedDir: File) {
        if (!frontendGeneratedDir.exists() && !frontendGeneratedDir.mkdirs()) {
            throw GradleException("Unable to continue. Target generation dir $frontendGeneratedDir cannot be created")
        }
    }

    fun universalPath(file: File): String {
        val universal = if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
            // on windows, we MUST have a "double slash" to properly escape the path separator in the config files
            // the regex is escaped, and the replaced text has to be "double" escaped.
            file.absolutePath.replace(regex, "\\\\\\\\")
        } else {
            file.absolutePath
        }

        return if (file.isDirectory && !(universal.endsWith("\\\\") || universal.endsWith('/'))) {
            if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
                "$universal\\\\"
            } else {
                "$universal/"
            }
        } else {
            universal
        }
    }

    fun relativize(sourceFile: File, targetFile: File): String {
        // the regex is to make sure that the '/' is properly escaped in the file (otherwise it's interpreted incorrectly by webpack)
        val replace = if (sourceFile.isDirectory) {
            targetFile.toRelativeString(sourceFile).replace(JsonPackageTools.regex, "/")
        } else {
            val replace = targetFile.toRelativeString(sourceFile).replace(JsonPackageTools.regex, "/")
            val newStart = replace.indexOf(File.separatorChar) + 1
            replace.substring(newStart)
        }
        //            println("Relativize:")
        //            println("\t$sourceFile")
        //            println("\t$targetFile")
        //            println("\t$replace")
        return replace
    }

    fun compareAndCopy(fileSource: File, fileTarget: File) {
        val relative = relativize(fileSource, fileTarget)

        if (getHash(fileSource) != getHash(fileTarget)) {
            if (fileTarget.exists()) {
                val max = 20
                var count = 0
                while (!fileTarget.delete() && count++ < max) {
                    // sometimes the file is locked, so we should wait
                    Thread.sleep(500L)
                }

                if (!fileTarget.delete()) {
                    println("Unable to overwrite file: $fileTarget")
                }
            }

            val canRead = fileSource.copyTo(fileTarget, true).canRead()
            if (canRead) {
                println("\tCopy SUCCESS: $fileSource -> $relative")
            } else {
                println("\tCopy FAILED: $fileSource -> $relative")
            }
        }
        else {
            println("\tCopy SKIP: $fileSource -> $relative")
        }
    }

    private fun getHash(file: File): String {
        return if (file.canRead()) {
            getHash(file.readText(Charsets.UTF_8))
        } else {
            "cannot read: ${file.absolutePath}"
        }
    }

    private fun getHash(content: String): String {
        return if (content.isEmpty()) {
            content
        } else try {
            val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
            digest.digest(content.toByteArray()).joinToString("", transform = { "%02x".format(it) })
        } catch (e: NoSuchAlgorithmException) {
            // Unrecoverable runtime exception, it should not happen
            throw RuntimeException("Unable to find a provider for SHA-256 algorithm", e)
        }
    }
}
