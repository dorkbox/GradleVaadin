package dorkbox.gradleVaadin

import com.vaadin.flow.function.SerializableFunction
import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.InitParameters
import com.vaadin.flow.server.frontend.FrontendUtils
import com.vaadin.flow.server.frontend.JarContentsManager
import com.vaadin.flow.server.frontend.NodeUpdaterAccess
import com.vaadin.flow.server.frontend.TaskUpdateImports
import com.vaadin.flow.server.frontend.scanner.ClassFinder
import com.vaadin.flow.server.frontend.scanner.FrontendDependenciesScanner
import dorkbox.gradleVaadin.node.variant.VariantComputer
import elemental.json.Json
import elemental.json.impl.JsonUtil
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
    val baseDir = project.rootDir

    val debug = VaadinConfig[project].debug

    val buildDir = project.buildDir
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
    val flowJsonPackageFile = buildDir.resolve(VaadinConfig[project].flowDirectory).resolve(Constants.PACKAGE_JSON)
    val generatedNodeModules = buildDir.resolve(FrontendUtils.NODE_MODULES)
    val webPackExecutableFile = generatedNodeModules.resolve("webpack").resolve("bin").resolve("webpack.js").absoluteFile

    val tokenFile = buildDir.resolve(FrontendUtils.TOKEN_FILE)
    val generatedFilesDir = buildDir.resolve(FrontendUtils.FRONTEND).absoluteFile

    val flowImportFile = generatedFilesDir.resolve(FrontendUtils.IMPORTS_NAME)

    // REGARDING the current version of polymer.
    // see: com.vaadin.flow.server.frontend.NodeUpdater.updateMainDefaultDependencies
    val polymerVersion = "3.2.0"

    val nodeExtension = NodeExtension[project]

    val variantComputer = VariantComputer()
    val nodeDirProvider = variantComputer.computeNodeDir(nodeExtension)
    val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
    val nodeBinExecProvider = variantComputer.computeNodeExec(nodeExtension, nodeBinDirProvider)

    val npmScriptProvider = variantComputer.computeNpmScriptFile(nodeDirProvider, "npm")


    val nodeDir = nodeDirProvider.get().asFile

    val nodeModulesDir = variantComputer.computeNodeModulesDir(nodeExtension).get().asFile

    val npmDirProvider = variantComputer.computeNpmDir(nodeExtension, nodeDirProvider)
    val npmBinDirProvider = variantComputer.computeNpmBinDir(npmDirProvider)



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

        println("\t\tDebug: $debug")
        println("\t\tPolymer version: $polymerVersion")
        println("\t\tBase Dir: $baseDir")
        println("\t\tNode Dir: ${nodeDir}")
        println("\t\tGenerated Dir: $frontendGeneratedDir")
        println("\t\tWebPack Executable: $webPackExecutableFile")

        println("\t\tJsonPackageFile: $jsonPackageFile")
        println("\t\tJsonPackage generated file: ${buildDirJsonPackageFile}")
    }

    fun prepareFrontEnd() {
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


        // so we must have TWO json files.
        // -  the original json file (but existing in the build dir)
        // -  the generated one containing all of the vaadin-flow dependencies (the original one must point to this one)

//        val updater = NodeUpdaterAccess(null, null, buildDir, locationOfGeneratedJsonForFlowDependencies, ConsoleLog(messagePreface = "\t\t"))
////        val genJson = updater.packageJson
//        val jsonFile = updater.getPackageJsonFile()
//
////        JsonPackageTools.compareAndCopy(jsonPackageFile, jsonFile)
//
////        // we want to also MERGE in our saved (non-generated) json file contents to the generated file
////        println("Merging original json into generated json.")
////        val origJson = NodeUpdaterAccess.getJsonFileContent(jsonPackageFile)
////        JsonPackageTools.mergeJson(origJson, genJson)
//
//        println("\tUpdating generated json file with defaults")
//        val didUpdates = updater.updateDefaultDependencies(genJson)
//        if (didUpdates) {
//            println("\tAdded items to [$jsonFile]")
//
//            updater.writePackageFile(genJson)
////            JsonPackageTools.updatePackageHash(genJson)
////            JsonPackageTools.writeJson(jsonFile, genJson)
//        }

        // NOTE: this ABSOLUTELY MUST start with a "./", otherwise NPM freaks out
//        JsonPackageTools.fixOriginalJsonPackage(jsonPackageFile, polymerVersion, "./frontend")


//        val frontendGenJsonFile = buildDir.resolve(Constants.PACKAGE_JSON)
        // only if they are different

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

        // now we have to update the package.json file with whatever version of into we have specified on the classpath
        NodeUpdaterAccess.enablePackagesUpdate(customClassFinder, frontendDependencies, buildDir)
    }

    fun createTokenFile() {
        val productionMode = VaadinConfig[project].productionMode.get()

        println("\tCreating configuration token file: $tokenFile")
        println("\tProduction mode: $productionMode")

        // propagateBuildInfo & updateBuildInfo (combined from maven goals, because the token file is ONLY used for enableImportsUpdate)
        val buildInfo = Json.createObject()

        buildInfo.put(InitParameters.SERVLET_PARAMETER_COMPATIBILITY_MODE, false)
        buildInfo.put(InitParameters.SERVLET_PARAMETER_PRODUCTION_MODE, productionMode)
        buildInfo.put("polymer.version", polymerVersion)

        // used for defining folder paths for dev server
        if (!productionMode) {
            buildInfo.put(Constants.NPM_TOKEN, buildDir.absolutePath)
            buildInfo.put(Constants.GENERATED_TOKEN, frontendGeneratedDir.absolutePath)
            buildInfo.put(Constants.FRONTEND_TOKEN, frontendDir.absolutePath)
        }

        buildInfo.put(InitParameters.SERVLET_PARAMETER_ENABLE_DEV_SERVER, !productionMode)

        tokenFile.delete()
        tokenFile.ensureParentDirsCreated()
        JsonPackageTools.writeJson(tokenFile, buildInfo)
        tokenFile.writeText(JsonUtil.stringify(buildInfo, 2) + "\n", Charsets.UTF_8)

        if (debug) {
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


        val origPackageJson = JsonPackageTools.getOrCreateJson(jsonPackageFile)
        val generatedPackageJson = JsonPackageTools.getOrCreateJson(buildDirJsonPackageFile)

        // will also make sure that all of the contents from the original file are in the generated file.
        val didCleanup = JsonPackageTools.updateGeneratedPackageJsonDependencies(
            origPackageJson,
            generatedPackageJson,
            frontendDependencies.packages,
            jsonPackageFile, buildDirJsonPackageFile, generatedNodeModules, jsonPackageLockFile
        )

        val hashIsModified = JsonPackageTools.updatePackageHash(generatedPackageJson)
        if (hashIsModified) {
            println("\tPackage dependencies were modified!")
        }

        if (didCleanup) {
            println("\t##########################################################")
            println("\tNode.js information is different. Cleaning up directories!")
            println("\t##########################################################")

            // Removes package-lock.json file in case the versions are different.
            if (jsonPackageLockFile.exists()) {
                if (!jsonPackageLockFile.delete()) {
                    throw GradleException(
                        "Could not remove ${jsonPackageLockFile.path} file. This file has been generated with " +
                                "a different platform version. Try to remove it manually."
                    )
                }
            }
        }

        // also have to make sure that webpack is properly installed!
        val webPackNotInstalled = !webPackExecutableFile.canRead()

        if (didCleanup || hashIsModified || webPackNotInstalled) {
            println("\tSomething changed, installing dependencies")
            // must run AFTER package.json file is created **AND** packages are updated!
//            installPackageDependencies(project, generatedNodeModules)
        }
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


    private fun installPackageDependencies(
        project: Project,
        nodeModulesDir: File
    ) {
        // now we have to install the dependencies from package.json! We do this MANUALLY, instead of using the builder
        println("\tInstalling package dependencies")

        val npmScriptFile = npmScriptProvider.get()
        val nodeBinDir = nodeBinDirProvider.get().asFile.absolutePath
        val npmBinDir = npmBinDirProvider.get().asFile.absolutePath
        val nodePath = npmBinDir + File.pathSeparator + nodeBinDir


        val exec = Exec(project)
        exec.executable = nodeBinExecProvider.get()
        exec.path = nodePath
        exec.workingDir = nodeModulesDir.parentFile

        exec.debug = debug
        exec.suppressOutput = !debug

        exec.environment["ADBLOCK"] = "1"
        exec.environment["NO_UPDATE_NOTIFIER"] = "1"
        exec.arguments = listOf(npmScriptFile, "install")
        exec.execute()
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

        val taskUpdateClass = TaskUpdateImports::class.java
        val constructor = taskUpdateClass.declaredConstructors.first { constructor -> constructor.parameterCount == 8 }
        constructor.isAccessible = true

        val updateImports = constructor.newInstance(
            customClassFinder,  // a reusable class finder
            frontendDependencies,  // a reusable frontend dependencies scanner
            provider,              // fallback scanner provider, not {@code null}
            jsonPackageFile.parentFile, // folder with the `package.json` file
            generatedFilesDir, // folder where flow generated files will be placed.
            frontendDir,  // a directory with project's frontend files
            tokenFile,    // the token (flow-build-info.json) path, may be {@code null}
            JsonPackageTools.getJson(tokenFile) // object to fill with token file data, may be {@code null}
        ) as TaskUpdateImports

        updateImports.execute()

        val ms = (System.nanoTime() - start) / 1000000
        println("\t\tFinished in $ms ms")
    }

    fun generateWebPack() {
        val start = System.nanoTime()
        println("\tConfiguring WebPack")

        val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
        val nodeExec = variantComputer.computeNodeExec(nodeExtension, nodeBinDirProvider).get()

        val npmDir = variantComputer.computeNpmDir(nodeExtension, nodeDirProvider)
        val npmBinDir = variantComputer.computeNpmBinDir(npmDir).get().asFile.absolutePath
        val nodeBinDir = nodeBinDirProvider.get().asFile.absolutePath
        val nodePath = npmBinDir + File.pathSeparator + project.buildDir

        // For information about webpack, SEE https://webpack.js.org/guides/getting-started/

        val exec = Exec(project)
        exec.executable = nodeExec
        exec.path = nodePath
        exec.workingDir = buildDir

        exec.debug = debug
        exec.suppressOutput = !debug

        exec.arguments = listOf(webPackExecutableFile.path, "--config", webPackProdFile.absolutePath, "--silent")
        exec.execute()

        val ms = (System.nanoTime() - start) / 1000000
        println("\t\tFinished in $ms ms")
    }

    fun finish() {
        customClassFinder.finish()
    }
}
