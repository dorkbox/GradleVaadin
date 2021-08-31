package dorkbox.gradleVaadin

import com.vaadin.flow.function.SerializableFunction
import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.InitParameters
import com.vaadin.flow.server.frontend.FrontendUtils
import com.vaadin.flow.server.frontend.JarContentsManager
import com.vaadin.flow.server.frontend.NodeUpdaterAccess
import com.vaadin.flow.server.frontend.scanner.ClassFinder
import com.vaadin.flow.server.frontend.scanner.FrontendDependenciesScanner
import dorkbox.gradleVaadin.node.NodeInfo
import elemental.json.Json
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
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


    val frontendDir = baseDir.resolve(FrontendUtils.FRONTEND)

    val webAppDir = baseDir.resolve("resources")
    val metaInfDir = webAppDir.resolve("META-INF")

    val vaadinDir = metaInfDir.resolve("resources").resolve("VAADIN")
    val frontendGeneratedDir = buildDir.resolve(FrontendUtils.FRONTEND)

    // This file also points to the generated package file in the generated frontend dir
    val origWebPackFile = baseDir.resolve(FrontendUtils.WEBPACK_CONFIG)
    val origWebPackProdFile = baseDir.resolve("webpack.production.js")

    val jsonPackageFile = baseDir.resolve(Constants.PACKAGE_JSON)
    val jsonPackageLockFile = buildDir.resolve("package-lock.json")
    val webPackFile = buildDir.resolve(FrontendUtils.WEBPACK_CONFIG)
    val webPackProdFile = buildDir.resolve("webpack.production.js")

    val buildDirJsonPackageFile = buildDir.resolve(Constants.PACKAGE_JSON)
    val flowJsonPackageFile = buildDir.resolve(config.flowDirectory).resolve(Constants.PACKAGE_JSON)
    val generatedNodeModules = buildDir.resolve(FrontendUtils.NODE_MODULES)
    val webPackExecutableFile = generatedNodeModules.resolve("webpack").resolve("bin").resolve("webpack.js")

    val tokenFile = buildDir.resolve(FrontendUtils.TOKEN_FILE)
    val generatedFilesDir = buildDir.resolve(FrontendUtils.FRONTEND)

    val flowImportFile = generatedFilesDir.resolve(FrontendUtils.IMPORTS_NAME)

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
        FrontendDependenciesScanner.FrontendDependenciesScannerFactory().createScanner(false, customClassFinder, true)
    }
    // SEE: com.vaadin.flow.server.startup.DevModeInitializer

    init {
        println("\tInitializing the vaadin compiler")
    }

    fun log() {
        if (config.debug) {
            println("\t\tVaadin version: ${VaadinConfig.VAADIN_VERSION}")
            println("\t\tPolymer version: $polymerVersion")
            println("\t\tBase Dir: $baseDir")
            println("\t\tBuild Dir: $buildDir")
            println("\t\tNode Dir: ${VaadinConfig[project].nodeJsDir.get().asFile}")

            println("\t\tGenerated Dir: $frontendGeneratedDir")
            println("\t\tWebPack Executable: $webPackExecutableFile")

            println("\t\tJsonPackageFile: $jsonPackageFile")
            println("\t\tJsonPackage generated file: ${buildDirJsonPackageFile}")
        }
    }

    fun prepareJsonFiles() {
        // always delete the VAADIN directory!
        vaadinDir.deleteRecursively()

        // always delete the generated directory
        generatedFilesDir.deleteRecursively()

        if (!frontendGeneratedDir.exists() && !frontendGeneratedDir.mkdirs()) {
            throw GradleException("Unable to continue. Target generation dir $frontendGeneratedDir cannot be created")
        }

        // make sure the flow parent dir exists
        flowJsonPackageFile.ensureParentDirsCreated()

        // we want to also MERGE in our saved (non-generated) json file contents to the generated file
        println("\tMerging original json into generated json.")

        val origJson = NodeUpdaterAccess.getJsonFileContent(jsonPackageFile)
        val genJson = NodeUpdaterAccess.getJsonFileContent(buildDirJsonPackageFile)
        JsonPackageTools.mergeJson(origJson, genJson)
        NodeUpdaterAccess.disableVaadinStatistics(genJson)

        JsonPackageTools.writeJson(buildDirJsonPackageFile, genJson)


        val locationOfGeneratedJsonForFlowDependencies = buildDir.resolve("frontend")
        NodeUpdaterAccess.createMissingPackageJson(buildDir, locationOfGeneratedJsonForFlowDependencies)

        // now we have to update the package.json file with whatever version of into we have specified on the classpath
        NodeUpdaterAccess.enablePackagesUpdate(customClassFinder, frontendDependencies, buildDir, config.enablePnpm, nodeInfo)
    }

    fun prepareWebpackFiles() {
        JsonPackageTools.compareAndCopy(origWebPackFile, webPackFile)
        JsonPackageTools.compareAndCopy(origWebPackProdFile, webPackProdFile)

        // we have to make additional customizations to the webpack.generated.js file
        val relativeStaticResources = JsonPackageTools.relativize(baseDir, metaInfDir).replace("./", "")

        JsonPackageTools.fixWebPackConfig(
            webPackFile,
            frontendDir,
            vaadinDir,
            flowImportFile,
            relativeStaticResources,
            customClassFinder
        )



        // then copy frontend (this copies from the jar files) - TaskCopyFrontendFiles
        // then copy frontend from local files - TaskCopyLocalFrontendFiles
        // then TaskUpdateImports


        // last DevModeHandler start


        // the plugin on start needs to rewrite DevModeInitializer.initDevModeHandler so that all these tasks aren't run every time for dev mode
        // because that is really slow to start up??
        // ALTERNATIVELY, devmodeinit runs the same thing as the gradle plugin, but the difference is we only run the full gradle version
        // for a production build (webpack is compiled instead of running dev-mode, is the only difference...).
        //   the only problem, is when running as a JPMS module...
        //      to solve this, we could modify the byte-code -- then RECREATE the jar (which we would then startup)
        //         OR.. we modify the bytecode ON COMPILE, so that a "run" would always include the modified jar
    }

    fun createTokenFile() {
        val productionMode = config.productionMode.get()

        println("\tCreating configuration token file: $tokenFile")
        println("\tProduction mode: $productionMode")


        // propagateBuildInfo & updateBuildInfo (combined from maven goals, because the token file is ONLY used for enableImportsUpdate)
        val buildInfo = Json.createObject()

        buildInfo.put(InitParameters.SERVLET_PARAMETER_COMPATIBILITY_MODE, false)
        buildInfo.put(InitParameters.SERVLET_PARAMETER_PRODUCTION_MODE, productionMode)
        buildInfo.put("polymer.version", polymerVersion)
        buildInfo.put("pnpm.enabled", config.enablePnpm) // matches vaadin application launcher

        // used for defining folder paths for dev server
        if (!productionMode) {
            buildInfo.put(Constants.NPM_TOKEN, buildDir.absolutePath)
            buildInfo.put(Constants.GENERATED_TOKEN, frontendGeneratedDir.absolutePath)
            buildInfo.put(Constants.FRONTEND_TOKEN, frontendDir.absolutePath)
        }

        buildInfo.put(InitParameters.SERVLET_PARAMETER_ENABLE_DEV_SERVER, !productionMode)

        JsonPackageTools.writeJson(tokenFile, buildInfo)

        if (config.debug) {
            println("\tToken content:\n ${tokenFile.readText(Charsets.UTF_8)}")
        }
    }

    fun generateWebComponents() {
        // now run NodeUpdater

        // enablePackagesUpdate OR enableImportsUpdate
        println("\tGenerating web-components into $frontendGeneratedDir")
        val gen = com.vaadin.flow.server.frontend.FrontendWebComponentGenerator(customClassFinder)
        gen.generateWebComponents(frontendGeneratedDir)
    }

    fun generateFlowJsonPackage() {
        // TaskUpdatePackages
        println("\tUpdating package dependency and hash information")


//        val origPackageJson = JsonPackageTools.getOrCreateJson(buildDirJsonPackageFile)
//        val generatedPackageJson = JsonPackageTools.getOrCreateJson(buildDirJsonPackageFile)

//        // will also make sure that all of the contents from the original file are in the generated file.
//        val didCleanup = JsonPackageTools.updateGeneratedPackageJsonDependencies(
//            origPackageJson,
//            generatedPackageJson,
//            frontendDependencies.packages,
//            jsonPackageFile, buildDirJsonPackageFile, generatedNodeModules, jsonPackageLockFile
//        )
//
//        val hashIsModified = JsonPackageTools.updatePackageHash(generatedPackageJson)
//        if (hashIsModified) {
//            println("\tPackage dependencies were modified!")
//        }

//        if (didCleanup) {
//            println("\t##########################################################")
//            println("\tNode.js information is different. Cleaning up directories!")
//            println("\t##########################################################")
//
//            // Removes package-lock.json file in case the versions are different.
//            if (jsonPackageLockFile.exists()) {
//                if (!jsonPackageLockFile.delete()) {
//                    throw GradleException(
//                        "Could not remove ${jsonPackageLockFile.path} file. This file has been generated with " +
//                                "a different platform version. Try to remove it manually."
//                    )
//                }
//            }
//        }

        // also have to make sure that webpack is properly installed!
//        val webPackNotInstalled = !webPackExecutableFile.canRead()

//        if (didCleanup || hashIsModified || webPackNotInstalled) {
//            println("\tSomething changed, installing dependencies")
//            // must run AFTER package.json file is created **AND** packages are updated!
////            installPackageDependencies(project, generatedNodeModules)
//        }
    }

    fun copyToken() {
        // fix token file location
        println("\tCopying token file...")
        tokenFile.copyTo(vaadinDir.resolve(FrontendUtils.TOKEN_FILE), overwrite = true)
    }


    fun copyResources() {
        // copyResources
        println("\tCopying resources...")

        var start = System.nanoTime()

        /////////////////////
        val classPathScanResult = io.github.classgraph.ClassGraph()
            .overrideClasspath(projectDependencies)
            .enableSystemJarsAndModules()
            .enableInterClassDependencies()
            .enableExternalClasses()
            .enableAllInfo()
            .scan()

        val frontendLocations = mutableSetOf<File>()

        // all jar files having files in the 'META-INF/frontend' or 'META-INF/resources/frontend' folder.
        //  We don't use URLClassLoader because will fail in Java 9+
        classPathScanResult.allResources.forEach {
            val interiorPath = it.path
            val jarContainer = it.classpathElementFile

            if (interiorPath.startsWith(Constants.RESOURCES_FRONTEND_DEFAULT) || interiorPath.startsWith(Constants.COMPATIBILITY_RESOURCES_FRONTEND_DEFAULT)) {
                frontendLocations.add(jarContainer)
            }
        }

        classPathScanResult.close()
        /////////////////////


        val targetDirectory = generatedNodeModules.resolve(FrontendUtils.FLOW_NPM_PACKAGE_NAME)
        targetDirectory.mkdirs()
        println("\tCopying frontend resources to '$targetDirectory'")

        // get all jar files having files in the 'META-INF/frontend' or 'META-INF/resources/frontend' folder.
        println("\t\tFound ${frontendLocations.size} resources")
        frontendLocations.forEach {
            println("\t\t\t${it.name}")
        }

        // copy jar resources
        @Suppress("LocalVariableName")
        val WILDCARD_INCLUSIONS = arrayOf("**/*.js", "**/*.css")
        val jarContentsManager = JarContentsManager()
        frontendLocations.forEach { location ->
            jarContentsManager.copyIncludedFilesFromJarTrimmingBasePath(
                location, Constants.RESOURCES_FRONTEND_DEFAULT,
                targetDirectory, *WILDCARD_INCLUSIONS
            )

            jarContentsManager.copyIncludedFilesFromJarTrimmingBasePath(
                location, Constants.COMPATIBILITY_RESOURCES_FRONTEND_DEFAULT,
                targetDirectory, *WILDCARD_INCLUSIONS
            )
        }

        var ms = (System.nanoTime() - start) / 1000000
        println("\t\tCopied ${frontendLocations.size} resources in $ms ms")


        // copy Local Resources
        if (frontendDir.isDirectory) {
            start = System.nanoTime()
            frontendDir.absoluteFile.copyRecursively(targetDirectory, true)

            ms = (System.nanoTime() - start) / 1000000
            println("\t\tCopied frontend directory $frontendDir")
            println("\t\t                     took $ms ms")
        } else {
            println("\t\tFound no local frontend resources for the project")
        }
    }


    fun generateFlow() {
        // enableImportsUpdate
        val start = System.nanoTime()

        println("\tGenerating vaadin flow files...")
        val genFile = generatedFilesDir.resolve("generated-flow-imports.js")
        val genFallbackFile = generatedFilesDir.resolve("generated-flow-imports-fallback.js")

        println("\t\tGenerating  $genFile")
        println("\t\tGenerating  $genFallbackFile")

        val provider =
            SerializableFunction<ClassFinder, FrontendDependenciesScanner> { t ->
                FrontendDependenciesScanner.FrontendDependenciesScannerFactory().createScanner(true, t, true)
            }


        // we know this is not null, because we explicitly created it earlier
        val tokenJson = JsonPackageTools.getJson(tokenFile)!!

        NodeUpdaterAccess.generateFlow(
            customClassFinder,     // a reusable class finder
            frontendDependencies,  // a reusable frontend dependencies scanner
            provider,              // fallback scanner provider, not {@code null}
            buildDir,          // folder with the `package.json` file
            generatedFilesDir, // folder where flow generated files will be placed.
            frontendDir,  // a directory with project's frontend files
            tokenFile,    // the token (flow-build-info.json) path, may be {@code null}
            tokenJson, // object to fill with token file data, may be {@code null}
            false
        )

        val ms = (System.nanoTime() - start) / 1000000
        println("\t\tFinished in $ms ms")
    }

    fun generateWebPack() {
        NodeUpdaterAccess.generateWebPack(nodeInfo, webPackExecutableFile, webPackProdFile)
    }

    fun finish() {
        customClassFinder.finish()
    }
}
