package com.vaadin.flow.server.frontend

import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.ExecutionFailedException
import com.vaadin.flow.server.frontend.scanner.ClassFinder
import com.vaadin.flow.shared.util.SharedUtil
import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.NodeInfo
import elemental.json.Json
import elemental.json.JsonObject
import elemental.json.impl.JsonUtil
import org.apache.commons.io.FileUtils
import org.slf4j.Logger
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.stream.*

/**
 * WE INSTALL NODE OURSELVES!
 *
 * flow-server-2.9.2
 */
object TaskRunNpmInstall_ {
    private val ignoredNodeFolders = listOf(
        ".bin",
        "pnpm", ".ignored_pnpm", ".pnpm", ".staging", ".vaadin",
        Util.MODULES_YAML
    )
    private var ciBuild = false

    private lateinit var packageUpdater: TaskUpdatePackages
    private lateinit var buildDir: File
    private lateinit var nodeInfo: NodeInfo
    private var enablePnpm = false
    private lateinit var generatedJson: JsonObject
    private lateinit var toolName: String

    private lateinit var runNpm: TaskRunNpmInstall

    /**
     * Updates <code>package.json</code> by visiting {@link NpmPackage} annotations
     * found in the classpath. It also visits classes annotated with
     * {@link NpmPackage}
     *
     * @since 2.0
     */
    fun execute(classFinder: ClassFinder, nodeInfo: NodeInfo, packageUpdater: TaskUpdatePackages) {
        this.nodeInfo = nodeInfo
        this.packageUpdater = packageUpdater
        this.buildDir = nodeInfo.buildDir
        this.enablePnpm = nodeInfo.enablePnpm
        this.ciBuild = nodeInfo.enableCiBuild

        this.generatedJson = Util.getJsonFileContent(nodeInfo.buildDirJsonPackageFile)

        // WE DO NOT RUN THE BUNDLED TaskRunNpmInstall that is done by the Vaadin app. It does a terrible job....
        // so we run it ourselves.
        val uri = URI("file:///foo/bar")
        this.runNpm = TaskRunNpmInstall(classFinder, packageUpdater, enablePnpm, false, "", uri, ciBuild)
//         runNpm.execute()


        // NOTE: first check if we need to run!
        this.toolName = if (enablePnpm) "pnpm" else "npm"
        val command: String = nodeInfo.nodeExec

        if (ciBuild || packageUpdater.modified || shouldRunNpmInstall()) {
            packageUpdater.log().info(
                "Running `$toolName $command` to resolve and optionally download frontend dependencies. This may take a moment, please stand by..."
            )
            runNpmInstall()
            updateLocalHash(generatedJson)
        } else {
            packageUpdater.log().info(
                ("Skipping `{} {}}` because the frontend packages " + "are already installed in the folder '{}' and " + "the hash in the file '{}' is the same as in '{}'"),
                toolName,
                command,
                packageUpdater.nodeModulesFolder.absolutePath,
                getLocalHashFile().absolutePath,
                Constants.PACKAGE_JSON
            )
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
    private fun updateLocalHash(json: JsonObject) {
        if (!json.hasKey(NodeUpdater.VAADIN_DEP_KEY)) {
            Util.logger.warn("No vaadin object in package.json")
            return
        }

        try {
            val vaadin = json.getObject(NodeUpdater.VAADIN_DEP_KEY)
            val hash = vaadin.getString(NodeUpdater.HASH_KEY)
            val localHash = Json.createObject()
            localHash.put(NodeUpdater.HASH_KEY, hash)

            val localHashFile: File = getLocalHashFile()
            FileUtils.forceMkdirParent(localHashFile)
            FileUtils.writeStringToFile(localHashFile, JsonUtil.stringify(localHash, 2) , StandardCharsets.UTF_8.name())
        } catch (e: IOException) {
            Util.logger.warn("Failed to update node_modules hash.", e)
        }
    }

    private fun getLocalHashFile(): File {
        return File(nodeInfo.buildDir, Util.INSTALL_HASH)
    }

    private fun shouldRunNpmInstall(): Boolean {
        val modulesDir = nodeInfo.nodeModulesDir

        if (!modulesDir.isDirectory) {
            return true
        }

        // Ignore .bin and pnpm folders as those are always installed for pnpm execution
        val installedPackages = modulesDir.listFiles()?.filterNot { ignoredNodeFolders.contains(it.name) } ?: listOf()
        return (installedPackages.isEmpty() ||
                installedPackages.size == 1 && FrontendUtils.FLOW_NPM_PACKAGE_NAME.startsWith(installedPackages[0].name) ||
                installedPackages.isNotEmpty() && isVaadinHashUpdated())
    }

    private fun isVaadinHashUpdated(): Boolean {
        val localHashFile = getLocalHashFile()
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

    @Throws(ExecutionFailedException::class)
    private fun cleanUp() {
        val nodeModulesFolder = nodeInfo.nodeModulesDir
        if (!nodeModulesFolder.exists()) {
            return
        }
        if (ciBuild) {
            deleteNodeModules(nodeModulesFolder)
        } else {
            val modulesYaml: File = File(nodeModulesFolder,  ".modules.yaml")
            val hasModulesYaml = (modulesYaml.exists() && modulesYaml.isFile)
            if (!enablePnpm && hasModulesYaml) {
                deleteNodeModules(nodeModulesFolder)

                buildDir.resolve("pnpmfile.js").also { pnpmScriptFile ->
                    if (pnpmScriptFile.exists()) {
                        println("\tCleaning up after change in pnpm installation")
                        pnpmScriptFile.delete()

                        buildDir.resolve("package.json").also { if (it.exists()) it.delete() } // reset this file
                        buildDir.resolve("versions.json").also { if (it.exists()) it.delete() }
                        buildDir.resolve("pnpm-lock.yaml").also { if (it.exists()) it.delete() }
                    }
                }
            } else if (enablePnpm && !hasModulesYaml) {
                // presence of .staging dir with a "pnpm-*" folder means that pnpm
                // download is in progress, don't remove anything in this case
                val staging: File = File(nodeModulesFolder, ".staging")
                if (!staging.isDirectory || staging.listFiles { dir: File?, name: String -> name.startsWith("pnpm-") }?.isEmpty() == true) {
                    deleteNodeModules(nodeModulesFolder)
                }
            }
        }
    }

    @Throws(ExecutionFailedException::class)
    private fun deleteNodeModules(nodeModulesFolder: File) {
        try {
            FrontendUtils.deleteNodeModules(nodeModulesFolder)
        } catch (exception: IOException) {
            val log: Logger = packageUpdater.log()
            log.debug("Exception removing node_modules", exception)
            log.error("Failed to remove '" + packageUpdater.nodeModulesFolder.absolutePath + "'. Please remove it manually.")
            throw ExecutionFailedException(
                "Exception removing node_modules. Please remove it manually."
            )
        }
    }

    /*
     * The pnpmfile.js file is recreated from scratch every time when `pnpm
     * install` is executed. It doesn't take much time to recreate it and it's
     * not supposed that it can be modified by the user. This is done in the
     * same way as for webpack.generated.js.
     */
    @Throws(IOException::class)
    private fun createPnpmFile() {
        val versionsPath = runNpm.generateVersionsJson() ?: return

        val pnpmFile = nodeInfo.pnpmFile

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

    /**
     * Installs frontend resources (using either pnpm or npm) after
     * `package.json` has been updated.
     */
    @Throws(ExecutionFailedException::class)
    private fun runNpmInstall() {
        // Do possible cleaning before generating any new files.
        cleanUp()

        if (enablePnpm) {
            try {
                createPnpmFile()
            } catch (exception: IOException) {
                throw ExecutionFailedException(
                    "Failed to read frontend version data from vaadin-core " + "and make it available to pnpm for locking transitive dependencies.\n" + "Please report an issue, as a workaround try running project " + "with npm by setting system variable -Dvaadin.pnpm.enable=false",
                    exception
                )
            }
        }




        // now we have to install the dependencies from package.json! We do this MANUALLY, instead of using the builder
        println("\tInstalling package dependencies")

        val debug = VaadinConfig[nodeInfo.project].debugNodeJs
        val process = nodeInfo.nodeExe {
            this.workingDirectory(buildDir)

            this.environment["ADBLOCK"] = "1"
            this.environment["NO_UPDATE_NOTIFIER"] = "1"

            val scriptFile = if (enablePnpm) nodeInfo.pnpmScript.absolutePath else nodeInfo.npmScript
            addArg(scriptFile)

            if (ciBuild) {
                if (enablePnpm) {
                    addArg("install")
                    addArg("--frozen-lockfile")
                } else {
                    addArg("ci")
                }
            } else {
                addArg("install")
            }


            addArg("--scripts-prepend-node-path")

            if (debug) {
                this.enableRead()
                this.defaultLogger()
                Util.execDebug(this)
            }
        }

        if (debug) {
            println("\t\tOutput:")
            process.output.linesAsUtf8().forEach {
                println("\t\t\t$it")
            }
        }

        if (process.exitValue == 0) {
            updateLocalHash(generatedJson)
        } else {
            println("Process failed with ${process.exitValue}!")
            throw ExecutionFailedException("${SharedUtil.capitalize(toolName)} install has exited with non zero status. Some dependencies are not installed. Check $toolName command output")
        }
    }
}
