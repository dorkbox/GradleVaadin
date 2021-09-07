package com.vaadin.flow.server.frontend

import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.frontend.scanner.ClassFinder
import com.vaadin.flow.server.frontend.scanner.FrontendDependenciesScanner
import dorkbox.executor.Executor
import dorkbox.gradleVaadin.JsonPackageTools
import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.NodeInfo
import elemental.json.JsonObject
import org.slf4j.Logger
import java.io.File


/**
 *                  flow-server-2.4.6
 *
 *  Allows access to NodeUpdater.java and NodeTasks in the vaadin project
 *
 *  For the compile steps, we have to match what is happening (for the most part) in the NodeTasks file
 */
class NodeUpdaterAccess(
    finder: ClassFinder?,
    frontendDependencies: FrontendDependenciesScanner?,
    npmFolder: File?,
    generatedPath: File?,
    val logger: Logger
) : NodeUpdater(finder, frontendDependencies, npmFolder, generatedPath) {
    companion object {
        fun generateWebPack(nodeInfo: NodeInfo, webPackExecutableFile: File, webPackProdFile: File) {
            val start = System.nanoTime()

            // For information about webpack, SEE https://webpack.js.org/guides/getting-started/
            println("\tGenerating WebPack")

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
                Util.execDebug(ex)
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
