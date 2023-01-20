package dorkbox.gradleVaadin

import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.InitParameters
import com.vaadin.flow.server.frontend.*
import com.vaadin.flow.server.frontend.scanner.FrontendDependencies

import dorkbox.gradleVaadin.node.NodeInfo
import elemental.json.Json
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import java.io.File


/**
 * For more info, see:
 *   https://github.com/vaadin/flow/tree/master/flow-maven-plugin\
 *   https://vaadin.com/docs/v14/flow/production/tutorial-production-mode-advanced.html
 */
@Suppress("MemberVisibilityCanBePrivate")
internal class VaadinCompiler(val project: Project) {
    private val config = VaadinConfig[project]

    val polymerVersion = Util.POLYMER_VERSION

    val nodeInfo by lazy { NodeInfo(project) }

    val projectDependencies by lazy {
        Vaadin.resolveRuntimeDependencies(project).dependencies
            .flatMap { dep ->
                dep.artifacts.map { artifact -> artifact.file }
            }
    }

    val customClassFinder by lazy {
        val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer

        val classPath = mutableListOf<File>()
        classPath.addAll(sourceSets.getByName("main").output.classesDirs.map { it.absoluteFile })
        classPath.addAll(projectDependencies)

        CustomClassFinder(classPath, projectDependencies)
    }

    val frontendDependencies by lazy {
        FrontendDependencies(customClassFinder, true)
    }
    // SEE: com.vaadin.flow.server.startup.DevModeInitializer

    init {
        println("\tInitializing the vaadin compiler")
    }

    fun log() {
        if (config.debug) {
            println("\t\t#######")
            println("\t\tFor the compile steps, we match (for the most part) NodeTasks from Vaadin")
            println("\t\tProduction Mode: ${config.productionMode.get()}")
            println("\t\tVaadin version: ${VaadinConfig.VAADIN_VERSION}")
            println("\t\tPolymer version: $polymerVersion")

            println("\t\tBase Dir: ${config.projectDir}")
            println("\t\tBuild Dir: ${config.buildDir}")
            println("\t\tNode Dir: ${config.nodeJsDir__}")

            println("\t\tGenerated Dir: ${nodeInfo.frontendGeneratedDir}")
            println("\t\tWebPack Executable: ${nodeInfo.webPackExecutableFile}")

            println("\t\tJsonPackageFile: ${nodeInfo.jsonPackageFile}")
            println("\t\tJsonPackage generated file: ${nodeInfo.buildDirJsonPackageFile}")
        } else {
            println("\t\tProduction Mode: ${config.productionMode.get()}")
            println("\t\tVaadin version: ${VaadinConfig.VAADIN_VERSION}")
        }
    }

    // dev
    fun generateWebComponents() {
        // vaadin.frontend.frontend.folder

        Util.ensureDirectoryExists(nodeInfo.frontendGeneratedDir)

        // enablePackagesUpdate OR enableImportsUpdate
        println("\tGenerating web-components into ${nodeInfo.frontendGeneratedDir}")
        val gen = FrontendWebComponentGenerator(customClassFinder)
        gen.generateWebComponents(nodeInfo.frontendGeneratedDir, frontendDependencies.themeDefinition)

        TaskGenerateTsFiles_.execute(nodeInfo.buildDir)
    }

    // dev
    fun createMissingPackageJson() {
        TaskCreatePackageJson_.execute(nodeInfo)
    }

    // dev
    fun prepareJsonFiles() {
        // now we have to update the package.json file with whatever version of into we have specified on the classpath
        val packageUpdater = TaskUpdatePackages_.execute(customClassFinder, frontendDependencies, nodeInfo)
        TaskRunNpmInstall_.execute(customClassFinder, nodeInfo, packageUpdater)
        TaskInstallWebpackPlugins_.execute(nodeInfo.nodeModulesDir)
    }

    // dev
    fun copyFrontendResourcesDirectory() {
        TaskCopyLocalFrontendFiles_.execute(nodeInfo)
    }

    // dev
    fun createTokenFile() {
        val productionMode: Boolean = config.productionMode.get()

        println("\tCreating configuration token file: ${nodeInfo.tokenFile}")
        println("\tProduction mode: $productionMode")


        // propagateBuildInfo & updateBuildInfo (combined from maven goals, because the token file is ONLY used for enableImportsUpdate)
        val buildInfo = Json.createObject()

        buildInfo.put(InitParameters.SERVLET_PARAMETER_COMPATIBILITY_MODE, false)
        buildInfo.put(InitParameters.SERVLET_PARAMETER_PRODUCTION_MODE, productionMode)
        buildInfo.put("polymer.version", polymerVersion)

        // matches custom vaadin application launcher
        buildInfo.put(InitParameters.SERVLET_PARAMETER_ENABLE_PNPM, config.enablePnpm)
        buildInfo.put(InitParameters.SERVLET_PARAMETER_ENABLE_DEV_SERVER, !productionMode)

        buildInfo.put(dorkbox.vaadin.util.VaadinConfig.DEBUG, config.debug)

        if (!productionMode) {
            // used for defining folder paths for dev server

            buildInfo.put(Constants.NPM_TOKEN, config.buildDir.absolutePath)
            buildInfo.put(Constants.GENERATED_TOKEN, nodeInfo.frontendGeneratedDir.absolutePath)
            buildInfo.put(Constants.FRONTEND_TOKEN, nodeInfo.frontendDir.absolutePath)
        } else {
            // only applicable when in production mode
            buildInfo.put(dorkbox.vaadin.util.VaadinConfig.EXTRACT_JAR, config.extractJar) // matches vaadin application launcher
        }


        JsonPackageTools.writeJson(nodeInfo.tokenFile, buildInfo)

        if (config.debug) {
            println("\tToken content:\n ${nodeInfo.tokenFile.readText(Charsets.UTF_8)}")
        }
    }

    // dev
    fun fixWebpackTemplate() {
        TaskUpdateWebpack_.execute(nodeInfo, customClassFinder)
    }

    // dev
    fun enableImportsUpdate() {
        val productionMode: Boolean = config.productionMode.get()
        val additionalFrontendModules = emptyList<String>() // TODO: get this from the plugin configuration
        TaskUpdateImports_.execute(nodeInfo, customClassFinder, frontendDependencies, additionalFrontendModules, productionMode, !config.newLicenseMode)

        TaskUpdateThemeImport_.execute(nodeInfo, frontendDependencies.themeDefinition)
    }

    // production
    fun generateWebPack() {
        val webPackExecutableFile = nodeInfo.webPackExecutableFile
        val debug = nodeInfo.debugNodeJS

        val start = System.nanoTime()

        // For information about webpack, SEE https://webpack.js.org/guides/getting-started/
        println("\tGenerating WebPack")

        val process = nodeInfo.nodeExeAsync {
            this.workingDirectory(nodeInfo.buildDir)
                .addArg(webPackExecutableFile.path, "--config", nodeInfo.webPackProdFile.path)
                .addArg("--display-error-details")
            this.environment["NO_UPDATE_NOTIFIER"] = "1"

            if (!debug) {
                this.addArg("--silent")
            } else {
                this.addArg("--progress")
                this.defaultLogger()
                this.enableRead()
                Util.execDebug(this)
            }
        }


        val output = process.output
        val result = runBlocking {
            if (debug) {
                println("\tGathering webpack output...")
                launch {
                    while (output.isOpen) {
                        print(output.utf8())
                    }
                }
            }

            process.await()
        }

        if (result.exitValue != 0) {
            println("Process failed with ${result.exitValue}!")
        }

        val ms = (System.nanoTime() - start) / 1000000
        println("\t\tFinished in $ms ms")

        val statsJson = nodeInfo.vaadinStatsJsonFile
        println("\t${statsJson.path}\n\tSize: ${statsJson.length().toDouble() / (1_000 * 1_000)} MB")
    }

    fun finish() {
        println("\tShutting down the Vaadin Compiler")
        customClassFinder.finish()
    }
}
