package dorkbox.gradleVaadin.node

import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.frontend.FrontendUtils
import dorkbox.executor.DeferredProcessResult
import dorkbox.executor.Executor
import dorkbox.executor.processResults.SyncProcessResult
import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.variant.VariantComputer
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File

/**
 *
 */
class NodeInfo(val project: Project) {
    val config = VaadinConfig[project]

    val sourceDir = config.projectDir.absoluteFile.normalize()
    val buildDir = config.buildDir.absoluteFile.normalize()


    val enablePnpm = config.enablePnpm



    val debug = config.debug
    val debugNodeJS = config.debugNodeJs


    private val nodeOptions = config.nodeOptions


    val metaInfDir = sourceDir.resolve("resources").resolve("META-INF")

    val outputMetaInfDir = buildDir.resolve("resources").resolve("main").resolve("META-INF")
    val vaadinDir = outputMetaInfDir.resolve("resources").resolve("VAADIN")

    val vaadinStatsJsonFile = vaadinDir.resolve(Constants.STATISTICS_JSON_DEFAULT)
    val vaadinStaticDir = vaadinDir.resolve("static")
    val tokenFile = vaadinDir.resolve(FrontendUtils.TOKEN_FILE)


    val frontendGeneratedDir = buildDir.resolve(FrontendUtils.FRONTEND)

    val pnpmFile = buildDir.resolve("pnpmfile.js")


    // This file also points to the generated package file in the generated frontend dir
    val origWebPackFile = sourceDir.resolve(FrontendUtils.WEBPACK_CONFIG)
    val origWebPackProdFile = sourceDir.resolve("webpack.production.js")

    val jsonPackageFile = sourceDir.resolve(Constants.PACKAGE_JSON)
    val buildDirJsonPackageFile = buildDir.resolve(Constants.PACKAGE_JSON)
    val buildDirJsonPackageLockFile = buildDir.resolve("package-lock.json")

    // The webpack files MUST be executed from the build dir...
    val webPackFile = buildDir.resolve(FrontendUtils.WEBPACK_CONFIG)
    val webPackProdFile = buildDir.resolve("webpack.production.js")
    val webPackGeneratedFile = buildDir.resolve(FrontendUtils.WEBPACK_GENERATED)




    val frontendDir = sourceDir.resolve(FrontendUtils.FRONTEND)

    // There are SUBTLE differences between windows and not-windows with webpack!
    // NOTE: RELATIVE TO THE BUILD DIR!
    // NOTE: this does NOT make sense!
    //  windows: '..\frontend' (windows + linux -> undertow example only works here, windows -> netref works here)
    //    linux: 'frontend'  (linux -> netref only works here)
    // frontend generated directory. Resolved to absolute path
    val frontendDestDir_WebPack = buildDir.resolve(config.frontendGeneratedDir).normalize()


    // this is the absolute location of the SOURCE frontend files.
    val frontendSourceDir_WebPack = sourceDir.resolve(config.frontendSourceDir).normalize()


    val flowJsonPackageFile = buildDir.resolve(config.flowDirectory).resolve(Constants.PACKAGE_JSON)
    val generatedNodeModules = buildDir.resolve(FrontendUtils.NODE_MODULES)
    val webPackExecutableFile = generatedNodeModules.resolve("webpack").resolve("bin").resolve("webpack.js")

    val generatedFilesDir = buildDir.resolve(FrontendUtils.FRONTEND)

    // absolute path
    val flowImportFile = generatedFilesDir.resolve(FrontendUtils.IMPORTS_NAME)
    val flowFallbackImportFile = generatedFilesDir.resolve(FrontendUtils.FALLBACK_IMPORTS_NAME)


    val nodeDirProvider = config.nodeJsDir
    val nodeBinDir by lazy { VariantComputer.computeNodeBinDir(config.nodeJsDir__) }

    val nodeBinExec by lazy { VariantComputer.computeNodeExecFullPath(config,nodeBinDir) } // dont' want to compute things too early
    val nodeExec= VariantComputer.computeNodeExecFile()


    val nodeJsDir by lazy { config.nodeJsDir__ }

    val nodeModulesDir = nodeJsDir.parentFile.resolve("node_modules")

    val npmDirProvider = VariantComputer.computeNpmDir(config, nodeDirProvider)

    val npmScript by lazy { VariantComputer.computeNpmScriptFile(nodeDirProvider, "npm") }

    val pnpmScript = VariantComputer.computePnpmScriptFile(config)

    fun createFrontendDir(): File {
        val targetDirectory = generatedNodeModules.resolve(FrontendUtils.FLOW_NPM_PACKAGE_NAME)
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            throw GradleException("Unable to create target directory: $targetDirectory")
        }
        return targetDirectory
    }


    fun nodeExe(config: Executor.() -> Unit): SyncProcessResult {
        val exe = Executor()
            .executable(nodeExec)
            .environment("ADBLOCK", "1")
            .destroyOnExit()
            .useSystemEnvironment()


        if (nodeOptions.isNotEmpty()) {
            exe.environment["NODE_OPTIONS"] = nodeOptions
        }

        if (debugNodeJS) {
            exe.defaultLogger()
        }

        exe.addPath(nodeBinDir.path)

        config(exe)

        return exe.startBlocking()
    }

    fun nodeExeAsync(config: Executor.() -> Unit): DeferredProcessResult {
        val exe = Executor()
            .executable(nodeExec)
            .environment("ADBLOCK", "1")
            .destroyOnExit()
            .useSystemEnvironment()

        if (nodeOptions.isNotEmpty()) {
            exe.environment["NODE_OPTIONS"] = nodeOptions
        }

        if (debugNodeJS) {
            exe.defaultLogger()
        }

        exe.addPath(nodeBinDir.path)

        config(exe)

        return exe.startAsync()
    }

    fun nodeExeOutput(config: Executor.() -> Unit): String {
        val exe = Executor()
            .executable(nodeExec)
            .environment("ADBLOCK", "1")
            .destroyOnExit()
            .useSystemEnvironment()

        if (nodeOptions.isNotEmpty()) {
            exe.environment["NODE_OPTIONS"] = nodeOptions
        }

        if (debugNodeJS) {
            exe.defaultLogger()
        }

        exe.addPath(nodeBinDir.path)

        config(exe)

        val result = exe.startBlocking()
        return if (result.hasOutput) {
            result.output.utf8()
        } else {
            ""
        }
    }

    fun npmExe(config: Executor.() -> Unit): SyncProcessResult {
        val exe = Executor()
            .executable(nodeExec)
            //            .workingDirectory(File(nodeBinExec).parent)
            .environment("ADBLOCK", "1")
            .addArg(npmScript)
            .destroyOnExit()
            .useSystemEnvironment()


        if (nodeOptions.isNotEmpty()) {
            exe.environment["NODE_OPTIONS"] = nodeOptions
        }

        if (debugNodeJS) {
            exe.defaultLogger()
        }

        exe.addPath(nodeBinDir.path)

        config(exe)

        return exe.startBlocking()
    }

    fun npmExeOutput(config: Executor.() -> Unit): String {
        val result = npmExe(config)
        return if (result.hasOutput) {
            result.output.utf8()
        } else {
            ""
        }
    }
}
