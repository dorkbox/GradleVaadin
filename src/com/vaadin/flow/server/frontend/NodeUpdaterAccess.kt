package com.vaadin.flow.server.frontend

import com.vaadin.flow.function.SerializableFunction
import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.ExecutionFailedException
import com.vaadin.flow.server.frontend.scanner.ClassFinder
import com.vaadin.flow.server.frontend.scanner.FrontendDependenciesScanner
import dorkbox.executor.Executor
import dorkbox.gradleVaadin.ConsoleLog
import dorkbox.gradleVaadin.CustomClassFinder
import dorkbox.gradleVaadin.JsonPackageTools
import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.NodeInfo
import elemental.json.Json
import elemental.json.JsonObject
import org.apache.commons.io.FileUtils
import org.slf4j.Logger
import java.io.File
import java.io.IOException
import java.net.URI


/**
 *                  flow-server-2.4.6
 */
class NodeUpdaterAccess(
    finder: ClassFinder?,
    frontendDependencies: FrontendDependenciesScanner?,
    npmFolder: File?,
    generatedPath: File?,
    val logger: Logger
) : NodeUpdater(finder, frontendDependencies, npmFolder, generatedPath) {
    companion object {
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

        /**
         * Updates <code>package.json</code> by visiting {@link NpmPackage} annotations
         * found in the classpath. It also visits classes annotated with
         * {@link NpmPackage}
         *
         * @since 2.0
         */
        fun enablePackagesUpdate(
            classFinder: ClassFinder, frontendDependenciesScanner: FrontendDependenciesScanner,
            npmFolder: File, enablePnpm: Boolean = false, nodeInfo: NodeInfo
        ) {
            println("\tUpdating package dependencies")

            val packageUpdater = object: TaskUpdatePackages(classFinder, frontendDependenciesScanner,
                npmFolder,
                npmFolder,
                false, enablePnpm
            ) {
                override fun log(): Logger {
                    return logger
                }
            }

            packageUpdater.execute()

            // WE DO NOT RUN THE BUNDLED TaskRunNpmInstall that is done by the Vaadin app. It does a terrible job....
            // so we run it ourselves.
            val uri = URI("file:///foo/bar")
            val runNpm = TaskRunNpmInstall(classFinder, packageUpdater, enablePnpm, false, "", uri)
            // runNpm.execute()




            val pnpmFile = npmFolder.resolve("pnpmfile.js")


            // Do possible cleaning before generating any new files. (nly if something has changed)
            val nodeModulesDir = nodeInfo.nodeModulesDir
            if (nodeModulesDir.exists()) {
                val modulesYaml = nodeModulesDir.resolve(MODULES_YAML)
                val hasModulesYaml = modulesYaml.isFile
                if (enablePnpm && !hasModulesYaml) {
                    println("\tCleaning up for pnpm installation")
                    nodeModulesDir.listFiles()?.filterNot { it.name == "pnpm" || it.name == ".bin" }?.forEach {
                        it.deleteRecursively()
                    }
                } else if (!enablePnpm) {
                    npmFolder.resolve("pnpmfile.js").also { pnpmFile ->
                        if (pnpmFile.exists()) {
                            println("\tCleaning up after change in pnpm installation")
                            pnpmFile.delete()

                            npmFolder.resolve("package.json").also { if (it.exists()) it.delete() } // reset this file
                            npmFolder.resolve("versions.json").also { if (it.exists()) it.delete() }
                            npmFolder.resolve("pnpm-lock.yaml").also { if (it.exists()) it.delete() }
                        }
                    }
                }
            }

            if (enablePnpm) {
                logger.enable = false
                val versionsPath = runNpm.generateVersionsJson()
                logger.enable = true

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
                                logger.info("Generated pnpmfile hook file: '{}'", pnpmFile)

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

            val ex = Executor()

            ex.executable( nodeInfo.nodeBinExec)
            ex.workingDirectory(npmFolder)

            ex.environment["ADBLOCK"] = "1"
            ex.environment["NO_UPDATE_NOTIFIER"] = "1"

            val scriptFile = if (enablePnpm) nodeInfo.pnpmScript.absolutePath else nodeInfo.npmScript
            ex.addArg(scriptFile, "install")

            val debug = VaadinConfig[nodeInfo.project].debug

            if (debug) {
                ex.enableRead()
                execDebug(ex)
            }

            val process = ex.startBlocking()

            if (debug) {
                println("\t\tOutput:")
                process.output.linesAsUtf8().forEach {
                    println("\t\t\t$it")
                }
            }

            if (process.exitValue != 0) {
                println("Process failed with ${process.exitValue}!")
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
            val vaadinKey = JsonPackageTools.getOrCreateKey(packageJson, VAADIN_DEP_KEY)
            JsonPackageTools.addDependency(vaadinKey, "disableUsageStatistics", true)
        }

        fun generateFlow(
            customClassFinder: CustomClassFinder,
            frontendDependencies: FrontendDependenciesScanner,
            provider: SerializableFunction<ClassFinder, FrontendDependenciesScanner>,
            buildDir: File,
            generatedFilesDir: File,
            frontendDir: File,
            tokenFile: File,
            tokenJson: JsonObject,
            pNpmEnabled: Boolean
        ) {
            val task = TaskUpdateImports(
                customClassFinder,     // a reusable class finder
                frontendDependencies,  // a reusable frontend dependencies scanner
                provider,              // fallback scanner provider, not {@code null}
                buildDir,          // folder with the `package.json` file
                generatedFilesDir, // folder where flow generated files will be placed.
                frontendDir,  // a directory with project's frontend files
                tokenFile,    // the token (flow-build-info.json) path, may be {@code null}
                tokenJson,    // object to fill with token file data, may be {@code null}
                !pNpmEnabled,
                emptyList()
            )

            task.execute()
        }

        fun generateWebPack(nodeInfo: NodeInfo, webPackExecutableFile: File, webPackProdFile: File) {
            val start = System.nanoTime()

            // For information about webpack, SEE https://webpack.js.org/guides/getting-started/
            println("\tConfiguring WebPack")


            val ex = Executor()

            ex.executable(nodeInfo.nodeBinExec)
            ex.workingDirectory(nodeInfo.buildDir)

            ex.environment["ADBLOCK"] = "1"
            ex.environment["NO_UPDATE_NOTIFIER"] = "1"

            // --scripts-prepend-node-path is added to fix path issues
            ex.addArg(webPackExecutableFile.path, "--config", webPackProdFile.absolutePath, "--silent", "--scripts-prepend-node-path")

            val debug = VaadinConfig[nodeInfo.project].debug

            if (debug) {
                ex.enableRead()
                execDebug(ex)
            }

            val process = ex.startBlocking()

            if (debug) {
                println("\t\tOutput:")
                process.output.linesAsUtf8().forEach {
                    println("\t\t\t$it")
                }
            }

            if (process.exitValue != 0) {
                println("Process failed with ${process.exitValue}!")
            }


            //        val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
            //        val nodeExec = variantComputer.computeNodeExec(nodeExtension, nodeBinDirProvider).get()
            //
            //        val npmDir = variantComputer.computeNpmDir(nodeExtension, nodeDirProvider)
            //        val npmBinDir = variantComputer.computeNpmBinDir(npmDir).get().asFile.absolutePath
            //        val nodeBinDir = nodeBinDirProvider.get().asFile.absolutePath
            //        val nodePath = npmBinDir + File.pathSeparator + project.buildDir
            //
            //        // For information about webpack, SEE https://webpack.js.org/guides/getting-started/
            //
            //        val exec = Exec(project)
            //        exec.executable = nodeExec
            //        exec.path = nodePath
            //        exec.workingDir = buildDir
            //
            //        exec.debug = debug
            //        exec.suppressOutput = !debug
            //
            //        exec.arguments = listOf(webPackExecutableFile.path, "--config", webPackProdFile.absolutePath, "--silent")
            //        exec.execute()
            //
            val ms = (System.nanoTime() - start) / 1000000
            println("\t\tFinished in $ms ms")
        }
    }


    override fun execute() {
        TODO("Not yet implemented")
    }

    public fun getPackageJsonFile(): File {
        return File(npmFolder, Constants.PACKAGE_JSON)
    }

    public override fun getPackageJson(): JsonObject {
        return super.getPackageJson()
    }

    /**
     * Updates default dependencies and development dependencies to
     * package.json.
     *
     * @param packageJson
     *            package.json json object to update with dependencies
     * @return true if items were added or removed from the {@code packageJson}
     */
    public override fun updateDefaultDependencies(packageJson: JsonObject): Boolean {
        var added = super.updateDefaultDependencies(packageJson)


        // for node_modules\@vaadin\vaadin-usage-statistics
        //or you can disable vaadin-usage-statistics for the project by adding
        //```
        //   "vaadin": { "disableUsageStatistics": true }
        //```
        //to your project `package.json` and running `npm install` again (remove `node_modules` if needed).
        //
        //You can verify this by checking that `vaadin-usage-statistics.js` contains an empty function.
        val vaadinKey = JsonPackageTools.getOrCreateKey(packageJson, VAADIN_DEP_KEY)
        added = added || JsonPackageTools.addDependency(vaadinKey, "disableUsageStatistics", true) > 0

        return added
    }

    public override fun writePackageFile(packageJson: JsonObject?): String {
        return super.writePackageFile(packageJson)
    }

    public override fun log(): Logger {
        return logger
    }
}
