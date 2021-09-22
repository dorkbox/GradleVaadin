package com.vaadin.flow.server.frontend

import dorkbox.executor.Executor
import dorkbox.gradleVaadin.ConsoleLog
import dorkbox.gradleVaadin.JsonPackageTools
import dorkbox.gradleVaadin.node.NodeInfo
import elemental.json.Json
import elemental.json.JsonObject
import org.gradle.api.GradleException
import org.slf4j.Logger
import java.io.File

/**
 *
 */
object Util {
    val logger = ConsoleLog(messagePreface = "\t\t")

    val MODULES_YAML = ".modules.yaml";

    // .vaadin/vaadin.json contains local installation data inside node_modules
    // This will hep us know to execute even when another developer has pushed
    // a new hash to the code repository.
    val INSTALL_HASH = ".vaadin/vaadin.json"

    /**
     * Creates the <code>package.json</code> if missing.
     *
     * @since 2.0
     */
    fun createMissingPackageJson(npmFolder: File, generatedPath: File) {
        val task = object: TaskCreatePackageJson(npmFolder, generatedPath) {
            override fun log(): Logger {
                return logger
            }
        }
        task.execute()
    }

    fun addPath(environment: MutableMap<String, String?>, pathToInject: String) {
        // Take care of Windows environments that may contain "Path" OR "PATH" - both existing
        // possibly (but not in parallel as of now)
        if (environment["Path"] != null) {
            environment["Path"] = pathToInject + File.pathSeparator + environment["Path"]
        } else if (environment["PATH"] != null) {
            environment["PATH"] = pathToInject + File.pathSeparator + environment["PATH"]
        } else {
            environment["PATH"] = pathToInject
        }
    }

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

    fun createFrontendDir(nodeInfo: NodeInfo): File {
        val targetDirectory = nodeInfo.generatedNodeModules.resolve(FrontendUtils.FLOW_NPM_PACKAGE_NAME)
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            throw GradleException("Unable to create target directory: $targetDirectory")
        }
        return targetDirectory
    }

}
