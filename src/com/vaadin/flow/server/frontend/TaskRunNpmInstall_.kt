package com.vaadin.flow.server.frontend

import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.ExecutionFailedException
import com.vaadin.flow.server.frontend.scanner.ClassFinder
import dorkbox.executor.Executor
import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.NodeInfo
import dorkbox.gradleVaadin.node.util.PlatformHelper
import elemental.json.Json
import elemental.json.JsonObject
import elemental.json.impl.JsonUtil
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * flow-server-2.4.6
 */
object TaskRunNpmInstall_ {
    private val ignoredNodeFolders = listOf(
        ".bin",
        "pnpm", ".ignored_pnpm", ".pnpm", ".staging", ".vaadin",
        Util.MODULES_YAML
    )

    /**
     * Updates <code>package.json</code> by visiting {@link NpmPackage} annotations
     * found in the classpath. It also visits classes annotated with
     * {@link NpmPackage}
     *
     * @since 2.0
     */
    fun execute(classFinder: ClassFinder, nodeInfo: NodeInfo, packageUpdater: TaskUpdatePackages) {
        val buildDir = nodeInfo.buildDir
        val enablePnpm = nodeInfo.enablePnpm
        val generatedJson = Util.getJsonFileContent(nodeInfo.buildDirJsonPackageFile)

        // WE DO NOT RUN THE BUNDLED TaskRunNpmInstall that is done by the Vaadin app. It does a terrible job....
        // so we run it ourselves.
        val uri = URI("file:///foo/bar")
        val runNpm = TaskRunNpmInstall(classFinder, packageUpdater, enablePnpm, false, "", uri)
        // runNpm.execute()
        // NOTE: we must manually update the hash info!




        // NOTE: first check if we need to run!
        val toolName = if (enablePnpm) "pnpm" else "npm"
        if (!(packageUpdater.modified || shouldRunNpmInstall(packageUpdater, nodeInfo))) {
//            packageUpdater.log().info(
//                "Running `" + toolName + " install` to "
//                        + "resolve and optionally download frontend dependencies. "
//                        + "This may take a moment, please stand by..."
//            )
//            runNpmInstall()
//            updateLocalHash()
            packageUpdater.log().info(
                "Skipping `{} install` because the frontend packages are already "
                        + "installed in the folder '{}' and the hash in the file '{}' is the same as in '{}'",
                toolName,
                packageUpdater.nodeModulesFolder.absolutePath,
                getLocalHashFile(nodeInfo).absolutePath,
                Constants.PACKAGE_JSON
            )
        }




        val pnpmFile = nodeInfo.pnpmFile


        // Do possible cleaning before generating any new files. (nly if something has changed)
        val nodeModulesDir = nodeInfo.nodeModulesDir
        if (nodeModulesDir.exists()) {
            val modulesYaml = nodeModulesDir.resolve(Util.MODULES_YAML)
            val hasModulesYaml = modulesYaml.isFile
            if (enablePnpm && !hasModulesYaml) {
                println("\tCleaning up for pnpm installation")
                nodeModulesDir.listFiles()?.filterNot { it.name == "pnpm" || it.name == ".bin" }?.forEach {
                    it.deleteRecursively()
                }
            } else if (!enablePnpm) {
                buildDir.resolve("pnpmfile.js").also { pnpmScriptFile ->
                    if (pnpmScriptFile.exists()) {
                        println("\tCleaning up after change in pnpm installation")
                        pnpmScriptFile.delete()

                        buildDir.resolve("package.json").also { if (it.exists()) it.delete() } // reset this file
                        buildDir.resolve("versions.json").also { if (it.exists()) it.delete() }
                        buildDir.resolve("pnpm-lock.yaml").also { if (it.exists()) it.delete() }
                    }
                }
            }
        }

        if (enablePnpm) {
            Util.logger.enable = false
            val versionsPath = runNpm.generateVersionsJson()
            Util.logger.enable = true

            if (versionsPath != null) {
                /*
                * The pnpmfile.js file is recreated from scratch every time when `pnpm
                * install` is executed. It doesn't take much time to recreate it and it's
                * not supposed that it can be modified by the user. This is done in the
                * same way as for webpack.generated.js.
                */
                println("\tCreating pnpm directive files")

                try {
                    TaskRunNpmInstall::class.java
                        .getResourceAsStream("/pnpmfile.js").use { content ->
                            if (content == null) {
                                throw IOException(
                                    "Couldn't find template pnpmfile.js in the classpath"
                                )
                            }
                            FileUtils.copyInputStreamToFile(content, pnpmFile)
                            Util.logger.info("Generated pnpmfile hook file: '{}'", pnpmFile)

                            val lines = pnpmFile.readLines().toMutableList()
                            for ((i, line) in lines.withIndex()) {
                                if (line.startsWith("const versionsFile")) {
                                    lines[i] = ("const versionsFile = require('path').resolve(__dirname, '$versionsPath');")
                                }
                            }

                            pnpmFile.writeText(lines.joinToString("\n"))
                        }
                } catch (exception: IOException) {
                    throw ExecutionFailedException(
                        ("Failed to read frontend version data from vaadin-core "
                                + "and make it available to pnpm for locking transitive dependencies.\n"
                                + "Please report an issue, as a workaround try running project "
                                + "with npm by setting system variable -Dvaadin.pnpm.enable=false"),
                        exception
                    )
                }
            }
        }


        // now we have to install the dependencies from package.json! We do this MANUALLY, instead of using the builder
        println("\tInstalling package dependencies")

        val exe = nodeInfo.nodeExe()
        exe.workingDirectory(buildDir)

        exe.environment["ADBLOCK"] = "1"
        exe.environment["NO_UPDATE_NOTIFIER"] = "1"

        val scriptFile = if (enablePnpm) nodeInfo.pnpmScript.absolutePath else nodeInfo.npmScript
        exe.addArg(scriptFile, "install")
        exe.addArg("--scripts-prepend-node-path")

        val debug = VaadinConfig[nodeInfo.project].debug

        if (debug) {
            exe.enableRead()
            Util.execDebug(exe)
        }

        val process = exe.startBlocking()

        if (debug) {
            println("\t\tOutput:")
            process.output.linesAsUtf8().forEach {
                println("\t\t\t$it")
            }
        }

        if (process.exitValue == 0) {
            updateLocalHash(nodeInfo, generatedJson)
        } else {
            println("Process failed with ${process.exitValue}!")
        }
    }

    /**
     * Updates the local hash to node_modules.
     *
     *
     * This is for handling updated package to the code repository by another
     * developer as then the hash is updated and we may just be missing one
     * module.
     */
    private fun updateLocalHash(nodeInfo: NodeInfo, json: JsonObject) {
        if (!json.hasKey(NodeUpdater.VAADIN_DEP_KEY)) {
            Util.logger.warn("No vaadin object in package.json")
            return
        }

        try {
            val vaadin = json.getObject(NodeUpdater.VAADIN_DEP_KEY)
            val hash = vaadin.getString(NodeUpdater.HASH_KEY)
            val localHash = Json.createObject()
            localHash.put(NodeUpdater.HASH_KEY, hash)

            val localHashFile: File = getLocalHashFile(nodeInfo)
            FileUtils.forceMkdirParent(localHashFile)
            FileUtils.writeStringToFile(localHashFile, JsonUtil.stringify(localHash, 2) , StandardCharsets.UTF_8.name())
        } catch (e: IOException) {
            Util.logger.warn("Failed to update node_modules hash.", e)
        }
    }

    private fun getLocalHashFile(nodeInfo: NodeInfo): File {
        return File(nodeInfo.buildDir, Util.INSTALL_HASH)
    }

    private fun shouldRunNpmInstall(packageUpdater: TaskUpdatePackages, nodeInfo: NodeInfo): Boolean {
        val modulesDir = nodeInfo.nodeModulesDir

        if (!modulesDir.isDirectory) {
            return true
        }

        // Ignore .bin and pnpm folders as those are always installed for pnpm execution
        val installedPackages = modulesDir.listFiles()?.filterNot { ignoredNodeFolders.contains(it.name) } ?: listOf()
        return (installedPackages.isEmpty() ||
                installedPackages.size == 1 && FrontendUtils.FLOW_NPM_PACKAGE_NAME.startsWith(installedPackages[0].name) ||
                installedPackages.isNotEmpty() && isVaadinHashUpdated(packageUpdater, nodeInfo))
    }

    private fun isVaadinHashUpdated(packageUpdater: TaskUpdatePackages, nodeInfo: NodeInfo): Boolean {
        val localHashFile = getLocalHashFile(nodeInfo)
        if (localHashFile.exists()) {
            try {
                val fileContent = FileUtils.readFileToString(localHashFile, StandardCharsets.UTF_8.name())

                val content = Json.parse(fileContent)
                if (content.hasKey(NodeUpdater.HASH_KEY)) {
                    val packageJson: JsonObject = packageUpdater.packageJson

                    val localHash = content.getString(NodeUpdater.HASH_KEY)
                    val savedHash = packageJson.getObject(NodeUpdater.VAADIN_DEP_KEY).getString(NodeUpdater.HASH_KEY)
                    return localHash != savedHash
                }
            } catch (e: IOException) {
                Util.logger.warn("Failed to load hashes forcing npm execution", e)
            }
        }

        return true
    }
}
