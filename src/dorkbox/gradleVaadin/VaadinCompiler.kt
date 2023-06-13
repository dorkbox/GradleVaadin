package dorkbox.gradleVaadin

import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.InitParameters
import com.vaadin.flow.server.frontend.FrontendWebComponentGenerator
import com.vaadin.flow.server.frontend.TaskCopyLocalFrontendFiles_
import com.vaadin.flow.server.frontend.TaskCreatePackageJson_
import com.vaadin.flow.server.frontend.TaskGenerateTsFiles_
import com.vaadin.flow.server.frontend.TaskInstallWebpackPlugins_
import com.vaadin.flow.server.frontend.TaskRunNpmInstall_
import com.vaadin.flow.server.frontend.TaskUpdateImports_
import com.vaadin.flow.server.frontend.TaskUpdatePackages_
import com.vaadin.flow.server.frontend.TaskUpdateThemeImport_
import com.vaadin.flow.server.frontend.TaskUpdateWebpack_
import com.vaadin.flow.server.frontend.Util
import com.vaadin.flow.server.frontend.scanner.FrontendDependencies
import dorkbox.gradleVaadin.node.NodeInfo
import elemental.json.Json
import elemental.json.JsonObject
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
class VaadinCompiler(val project: Project) {
    companion object {
        /**
         * Gets the version number.
         */
        const val version = "14.10.1"
    }

    private val config = VaadinConfig[project]

    val polymerVersion = Util.POLYMER_VERSION

    val nodeInfo by lazy { NodeInfo(project) }

    // this cannot be resolved until INSIDE a doLast {} callback, as moshiX will break otherwise!
    // dependencies cannot be modified after this resolves them
    val projectDependencies by lazy {
        Vaadin.resolveRuntimeDependencies(project).dependencies
            .flatMap { dep ->
                dep.artifacts.map { artifact -> artifact.file }
            }
    }

    @Volatile
    internal var classFinderInitialized = false
    val customClassFinder by lazy {
        classFinderInitialized = true
        // we want to search java + kotlin classes!
        val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer

        // this includes kotlin files
        val main = sourceSets.getByName("main")

        val classPath = mutableListOf<File>()
        classPath.addAll(main.output.classesDirs.map { it.absoluteFile })
        classPath.addAll(projectDependencies)

        CustomClassFinder(classPath)
    }

    val frontendDependencies by lazy {
        FrontendDependencies(customClassFinder, true)
    }
    // SEE: com.vaadin.flow.server.startup.DevModeInitializer


    fun log() {
        println("\tInitializing the vaadin compiler")

        val explicitRun = config.explicitRun.get()
        val defaultRun = config.defaultRun.get()

        if (config.debug) {
            println("\t\tFor the compile steps, we match (for the most part) NodeTasks from Vaadin")
            println("\t\tDebug mode: true")
        }

        println("\t\tProduction Mode: ${config.productionMode.get()}")
        if (explicitRun) println("\t\tForcing recompile: true")
        if (defaultRun) println("\t\tForcing default compile: true")
        println("\t\tCompiler version: $version")
        println("\t\tVaadin version: ${VaadinConfig.VAADIN_VERSION}")

        if (config.debug) {
            println("\t\tPolymer version: $polymerVersion")

            println("\t\tBase Dir: ${config.projectDir}")
            println("\t\tBuild Dir: ${config.buildDir}")
            println("\t\tNode Dir: ${config.nodeJsDir__}")

            println("\t\tGenerated Dir: ${nodeInfo.frontendGeneratedDir}")
            println("\t\tWebPack Executable: ${nodeInfo.webPackExecutableFile}")

            println("\t\tJsonPackage File: ${nodeInfo.jsonPackageFile}")
            println("\t\tJsonPackage generated file: ${nodeInfo.buildDirJsonPackageFile}")
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
    fun prepareJsonFiles() {
        // also copy over lock file!
        if (nodeInfo.jsonPackageLockFile.canRead()) {
            println("\tCopying json-lock into generated json-lock.")

            // we CANNOT merge the lock-file, because, there is a required json key that is "", and this breaks when writing the output!!!
            nodeInfo.jsonPackageLockFile.copyTo(nodeInfo.buildDirJsonPackageLockFile, true)
        }

        // createMissingPackageJson
        TaskCreatePackageJson_.execute(nodeInfo)

        // now we have to update the package.json file with whatever version of into we have specified on the classpath
        val packageUpdater = TaskUpdatePackages_.execute(customClassFinder, frontendDependencies, nodeInfo)

        // we want to also MERGE in our saved (non-generated) json file contents to the generated file
        println("\tMerging original json into generated json")

        val origJson = Util.getJsonFileContent(nodeInfo.jsonPackageFile)
        val genJson = Util.getJsonFileContent(nodeInfo.buildDirJsonPackageFile)

        JsonPackageTools.mergeJson(origJson, genJson)
        Util.disableVaadinStatistics(genJson)

        println("\tAdding required dependencies")
        // we enhance the default webpack tools
        val devDeps = genJson.get<JsonObject>("devDependencies")
        val vaadinDevDeps = genJson.get<JsonObject>("vaadin").get<JsonObject>("devDependencies")

        JsonPackageTools.addDependency(devDeps, "core-js", "3.19.3")
        JsonPackageTools.addDependency(devDeps, "terser-webpack-plugin", "4.2.3")
        JsonPackageTools.addDependency(devDeps, "webpack-bundle-analyzer", "4.5.0")

        val logEnabled = Util.logger.enable

        Util.logger.enable = false
        JsonPackageTools.addDependency(vaadinDevDeps, "core-js", "3.19.3")
        JsonPackageTools.addDependency(vaadinDevDeps, "terser-webpack-plugin", "4.2.3")
        JsonPackageTools.addDependency(vaadinDevDeps, "webpack-bundle-analyzer", "4.5.0")
        Util.logger.enable = logEnabled

        JsonPackageTools.writeJson(nodeInfo.buildDirJsonPackageFile, genJson)


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

    fun validTokenFile() : Boolean {
        if (!nodeInfo.tokenFile.canRead()) {
            return false
        }

        val tokenFile = Util.getJsonFileContent(nodeInfo.tokenFile)
        if (!tokenFile.hasKey(InitParameters.SERVLET_PARAMETER_COMPATIBILITY_MODE)) return false
        if (!tokenFile.hasKey(InitParameters.SERVLET_PARAMETER_PRODUCTION_MODE)) return false
        if (!tokenFile.hasKey("polymer.version")) return false

        if (!tokenFile.hasKey(InitParameters.SERVLET_PARAMETER_ENABLE_PNPM)) return false
        if (!tokenFile.hasKey(InitParameters.SERVLET_PARAMETER_ENABLE_DEV_SERVER)) return false

        if (!tokenFile.hasKey(dorkbox.vaadin.util.VaadinConfig.DEBUG)) return false

        val productionMode = tokenFile.getBoolean(InitParameters.SERVLET_PARAMETER_PRODUCTION_MODE)

        if (!productionMode) {
            if (!tokenFile.hasKey(Constants.NPM_TOKEN)) return false
            if (!tokenFile.hasKey(Constants.GENERATED_TOKEN)) return false
            if (!tokenFile.hasKey(Constants.FRONTEND_TOKEN)) return false
        } else {
            // only applicable when in production mode
            if (!tokenFile.hasKey(dorkbox.vaadin.util.VaadinConfig.EXTRACT_JAR)) return false
        }

        return true
    }

    fun tokenFileIsProdMode() : Boolean {
        if (!nodeInfo.tokenFile.canRead()) {
            return false
        }

        val tokenFile = Util.getJsonFileContent(nodeInfo.tokenFile)
        return tokenFile.getBoolean(InitParameters.SERVLET_PARAMETER_PRODUCTION_MODE)
    }

    fun tokenFileIsDevMode() : Boolean {
        if (!nodeInfo.tokenFile.canRead()) {
            return false
        }

        val tokenFile = Util.getJsonFileContent(nodeInfo.tokenFile)
        return !tokenFile.getBoolean(InitParameters.SERVLET_PARAMETER_PRODUCTION_MODE)
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
        // we don't always have the classfinder initialized, so only close it if we've started it!
        if (classFinderInitialized) {
            println("\tShutting down the Vaadin Compiler")
            customClassFinder.finish()
        }
    }
}
