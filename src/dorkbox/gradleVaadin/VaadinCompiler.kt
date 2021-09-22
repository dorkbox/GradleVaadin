package dorkbox.gradleVaadin

import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.InitParameters
import com.vaadin.flow.server.frontend.*
import com.vaadin.flow.server.frontend.scanner.FrontendDependenciesScanner
import dorkbox.executor.Executor
import dorkbox.gradleVaadin.node.NodeInfo
import elemental.json.Json
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

    val baseDir = config.sourceRootDir
    val buildDir = config.buildDir


    // REGARDING the current version of polymer.
    // see: com.vaadin.flow.server.frontend.NodeUpdater.updateMainDefaultDependencies
    val polymerVersion = "3.2.0"

    val nodeInfo by lazy { NodeInfo(project) }

    //    ext.vaadin_charts_license = "00df96cb-e8da-4111-8e72-e3a1fc8b394b"       // registered to ajraman@net-ref.com
    //ext.vaadin_spreadsheets_license = "bc7e7ea0-3068-471d-ac3f-cbfe7be4d7ec" // registered to ajraman@net-ref.com
    //     // vaadin charts/spreadsheets licenses
    //                "-Dvaadin.charts.developer.license=$vaadin_charts_license",
    //                "-Dvaadin.spreadsheet.developer.license=$vaadin_spreadsheets_license",]
    //    -Dvaadin.proKey=[pro-key-string]


    // The default configuration extends from the runtime configuration, which means that it contains all the dependencies and artifacts of the runtime configuration, and potentially more.
    // THIS MUST BE IN "afterEvaluate".
    // Using the "runtime" classpath (weirdly) DOES NOT WORK. Only "default" works.

    //    val projectDependencies = resolve(project.configurations["default"]).map { it.file }
    val projectDependencies by lazy {
        Vaadin.resolveRuntimeDependencies(project).dependencies
            .flatMap { dep ->
                dep.artifacts.map { artifact -> artifact.file }
            }
    }

    //    val projectDependencies = resolve(project.configurations["default"]).map { it.file }

    val customClassFinder by lazy {
        val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer

        val classPath = mutableListOf<File>()
        classPath.addAll(sourceSets.getByName("main").output.classesDirs.map { it.absoluteFile })
        classPath.addAll(projectDependencies)

        CustomClassFinder(classPath, projectDependencies)
    }

    val frontendDependencies by lazy {
        // this cannot be refactored out! (as tempting as that might be...)
        FrontendDependenciesScanner.FrontendDependenciesScannerFactory().createScanner(true, customClassFinder, true)
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

            println("\t\tBase Dir: $baseDir")
            println("\t\tBuild Dir: $buildDir")
            println("\t\tNode Dir: ${VaadinConfig[project].nodeJsDir.get().asFile}")

            println("\t\tGenerated Dir: ${nodeInfo.frontendGeneratedDir}")
            println("\t\tWebPack Executable: ${nodeInfo.webPackExecutableFile}")

            println("\t\tJsonPackageFile: ${nodeInfo.jsonPackageFile}")
            println("\t\tJsonPackage generated file: ${nodeInfo.buildDirJsonPackageFile}")
        }
    }

    // dev
    fun generateWebComponents() {
        Util.ensureDirectoryExists(nodeInfo.frontendGeneratedDir)

        // enablePackagesUpdate OR enableImportsUpdate
        println("\tGenerating web-components into ${nodeInfo.frontendGeneratedDir}")
        val gen = FrontendWebComponentGenerator(customClassFinder)
        gen.generateWebComponents(nodeInfo.frontendGeneratedDir)
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
    }

    // dev
    fun copyJarResources() {
        TaskCopyFrontendFiles_.execute(projectDependencies, nodeInfo)
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

        if (!productionMode) {
            // used for defining folder paths for dev server

            buildInfo.put(Constants.NPM_TOKEN, buildDir.absolutePath)
            buildInfo.put(Constants.GENERATED_TOKEN, nodeInfo.frontendGeneratedDir.absolutePath)
            buildInfo.put(Constants.FRONTEND_TOKEN, nodeInfo.frontendDir.absolutePath)
        } else {
            // only applicable when in production mode

            buildInfo.put(dorkbox.vaadin.util.VaadinConfig.EXTRACT_JAR, config.extractJar) // matches vaadin application launcher
            buildInfo.put(dorkbox.vaadin.util.VaadinConfig.EXTRACT_JAR_OVERWRITE, config.extractJarOverwrite) // matches vaadin application launcher
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
        val additionalFrontendModules = emptyList<String>() // TODO: get this from the plugin configuration
        TaskUpdateImports_.execute(nodeInfo, customClassFinder, frontendDependencies, additionalFrontendModules)
    }










    // production
    fun generateWebPack() {
        val webPackExecutableFile = nodeInfo.webPackExecutableFile
        val debug = nodeInfo.debug

        val start = System.nanoTime()

        // For information about webpack, SEE https://webpack.js.org/guides/getting-started/
        println("\tGenerating WebPack")

        val ex = Executor()

        val nodeBinExec = File(nodeInfo.nodeBinExec)
        ex.executable(nodeBinExec)
        ex.workingDirectory(nodeInfo.buildDir)

        // there are path issues on linux. we must add the path!
        ex.environment["ADBLOCK"] = "1"
        ex.environment["NO_UPDATE_NOTIFIER"] = "1"
        Util.addPath(ex.environment, nodeBinExec.parent)

        // --scripts-prepend-node-path is added to fix path issues
        ex.addArg(webPackExecutableFile.path, "--config", nodeInfo.webPackProdFile.path,
//            "--scripts-prepend-node-path"
        )

        if (!debug) {
            ex.addArg("--silent")
        } else {
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

    fun finish() {
        println("\tShutting down the Vaadin Compiler")
        customClassFinder.finish()
    }
}
