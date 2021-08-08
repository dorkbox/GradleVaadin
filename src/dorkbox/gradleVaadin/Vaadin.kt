/*
 * Copyright 2020 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.gradleVaadin

import dorkbox.gradleVaadin.node.npm.proxy.ProxySettings
import dorkbox.gradleVaadin.node.npm.task.NpmInstallTask
import dorkbox.gradleVaadin.node.npm.task.NpmSetupTask
import dorkbox.gradleVaadin.node.npm.task.NpmTask
import dorkbox.gradleVaadin.node.npm.task.NpxTask
import dorkbox.gradleVaadin.node.task.NodeSetupTask
import dorkbox.gradleVaadin.node.task.NodeTask
import dorkbox.gradleVaadin.node.variant.VariantComputer
import dorkbox.gradleVaadin.node.yarn.task.YarnInstallTask
import dorkbox.gradleVaadin.node.yarn.task.YarnSetupTask
import dorkbox.gradleVaadin.node.yarn.task.YarnTask
import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.frontend.FrontendUtils
import com.vaadin.flow.server.frontend.scanner.FrontendDependenciesScanner
import dorkbox.gradleVaadin.node.deps.DependencyScanner
import elemental.json.Json
import elemental.json.impl.JsonUtil
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.impldep.org.bouncycastle.asn1.x500.style.RFC4519Style
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import org.jetbrains.kotlin.gradle.targets.js.npm.packageJson
import java.io.File

/**
 * For managing Vaadin gradle tasks
 */
@Suppress("UnstableApiUsage", "unused")
class Vaadin : Plugin<Project> {
    private lateinit var project: Project
    private lateinit var config: VaadinConfig

    override fun apply(project: Project) {
        this.project = project

        // https://discuss.gradle.org/t/can-a-plugin-itself-add-buildscript-dependencies-and-then-apply-a-plugin/25039/4
        apply("java")
//        apply("kotlin-dsl")
//        apply("com.vaadin:flow-server")
//        apply("com.github.node-gradle.node")
//        project.plugins.apply(NodePlugin::class.java)

        // Create the Plugin extension object (for users to configure publishing).
        config = project.extensions.create("vaadin", VaadinConfig::class.java, project)

        // have to create the task
        project.tasks.create("prepare_jar_libraries", PrepJarsTask::class.java)

        project.repositories.apply {
            maven { setUrl("https://maven.vaadin.com/vaadin-addons") } // Vaadin Addons
            maven { setUrl("https://maven.vaadin.com/vaadin-prereleases") } // Pre-releases
            maven { setUrl("https://oss.sonatype.org/content/repositories/vaadin-snapshots") } // Vaadin Snapshots
        }

        project.dependencies.apply {
            add("implementation", "com.vaadin:vaadin:${config.vaadinVersion}")
            add("implementation", "com.dorkbox:VaadinUndertow:0.1")
        }

        // NOTE: NPM will ALWAYS install packages to the "node_modules" directory that is a sibiling to the packages.json directory!

        val nodeExtension = NodeExtension.create(project)
        addGlobalTypes()
        addTasks()
        addNpmRule()
        addYarnRule()

        project.afterEvaluate {
            if (nodeExtension.download.get()) {
                nodeExtension.distBaseUrl.orNull?.let { addRepository(it) }
                configureNodeSetupTask(nodeExtension)
            }
        }


//        val npmInstallTask = project.tasks.named(NpmInstallTask.NAME).get().apply {
//            this as NpmInstallTask
//            packageJson()
//
//        }



        val nodeSetup = project.tasks.named(NodeSetupTask.NAME).apply {

        }

        project.tasks.create("compileResources-DEV").apply {
            dependsOn(nodeSetup, project.tasks.named("classes"))
            mustRunAfter(nodeSetup)

            group = "vaadin"
            description = "Compile Vaadin resources for Development"

            // enable caching for the compile task
            outputs.cacheIf { true }

            inputs.files(
                "${project.projectDir}/package.json",
                "${project.projectDir}/package-lock.json",
                "${project.projectDir}/webpack.config.js",
                "${project.projectDir}/webpack.production.js"
            )

            outputs.dir("${project.buildDir}/config")
            outputs.dir("${project.buildDir}/resources")

            outputs.dir("${project.buildDir}/nodejs")
            outputs.dir("${project.buildDir}/node_modules")

            doLast {
                compileVaadinResources(project, false)
            }
        }

        project.tasks.create("compileResources-PROD").apply {
            dependsOn(nodeSetup, project.tasks.named("classes"))
            mustRunAfter(nodeSetup)

            group = "vaadin"
            description = "Compile Vaadin resources for Production"

            // enable caching for the compile task
            outputs.cacheIf { true }

            inputs.files(
                "${project.projectDir}/package.json",
                "${project.projectDir}/package-lock.json",
                "${project.projectDir}/webpack.config.js",
                "${project.projectDir}/webpack.production.js"
            )

            outputs.dir("${project.buildDir}/resources/META-INF/resources/VAADIN")
            outputs.dir("${project.buildDir}/node_modules")

            doLast {
                println("work? $didWork")
                compileVaadinResources(project, true)
            }
        }



        // have to get the configuration extension data
        // required to make sure the tasks run in the correct order
        project.afterEvaluate {
//            config.nodeFixer = FixNodeInstall(project, config)


//            // fix the maven source jar
//            val sourceJarTask = project.tasks.findByName("sourceJar") as Jar
//            sourceJarTask.apply {
//                val sourceSets = project.extensions.getByName("sourceSets") as org.gradle.api.tasks.SourceSetContainer
//                val mainSourceSet: SourceSet = sourceSets.getByName("main")
//
//                // want to included java + kotlin for the sources
//
//                // kotlin stuff. Sometimes kotlin depends on java files, so the kotlin sourcesets have BOTH java + kotlin.
//                // we want to make sure to NOT have both, as it will screw up creating the jar!
//                try {
//                    val kotlin = (mainSourceSet as org.gradle.api.internal.HasConvention)
//                        .convention
//                        .getPlugin(KotlinSourceSet::class.java)
//                        .kotlin
//
//                    val srcDirs = kotlin.srcDirs
//                    val kotlinFiles = kotlin.asFileTree.matching { it: PatternFilterable ->
//                        // find out if this file (usually, just a java file) is ALSO in the java sourceset.
//                        // this is to prevent DUPLICATES in the jar, because sometimes kotlin must be .kt + .java in order to compile!
//                        val javaFiles = mainSourceSet.java.files.map { file ->
//                            // by definition, it MUST be one of these
//                            val base = srcDirs.first {
//                                // find out WHICH src dir base path it is
//                                val path = project.buildDir.relativeTo(it)
//                                path.path.isNotEmpty()
//                            }
//                            file.relativeTo(base).path
//                        }
//
//                        it.setExcludes(javaFiles)
//                    }
//
//                    from(kotlinFiles)
//                } catch (ignored: Exception) {
//                    // maybe we don't have kotlin for the project
//                }
//
//                // java stuff (it is compiled AFTER kotlin), and it is ALREADY included!
//                // kotlin is always compiled first
//                // from(mainSourceSet.java)
//            }


//            // output how much the time-outs are
//            val durationString = config.httpTimeout.toString().substring(2)
//                    .replace("(\\d[HMS])(?!$)", "$1 ").toLowerCase()
//
//
//            val fullReleaseTimeout = Duration.ofMillis(config.retryDelay.toMillis() * config.retryLimit)
//            val fullReleaseString = fullReleaseTimeout.toString().substring(2)
//                    .replace("(\\d[HMS])(?!$)", "$1 ").toLowerCase()
//
//            project.tasks.findByName("publishToSonatype")?.doFirst {
//                println("\tPublishing to Sonatype: ${config.groupId}:${config.artifactId}:${config.version}")
//                println("\t\tSonatype HTTP timeout: $durationString")
//                println("\t\tSonatype API timeout: $fullReleaseString")
//            }
        }

        project.childProjects.values.forEach {
            it.pluginManager.apply(Vaadin::class.java)
        }
    }

    // required to make sure the plugins are correctly applied. ONLY applying it to the project WILL NOT work.
    // The plugin must also be applied to the root project
    private fun apply(id: String) {
        if (project.rootProject.pluginManager.findPlugin(id) == null) {
            project.rootProject.pluginManager.apply(id)
        }

        if (project.pluginManager.findPlugin(id) == null) {
            project.pluginManager.apply(id)
        }
    }


    private fun addGlobalTypes() {
        addGlobalType<NodeTask>()
        addGlobalType<NpmTask>()
        addGlobalType<NpxTask>()
        addGlobalType<YarnTask>()
        addGlobalType<ProxySettings>()
    }

    private inline fun <reified T> addGlobalType() {
        project.extensions.extraProperties[T::class.java.simpleName] = T::class.java
    }

    private fun addTasks() {
        project.tasks.register<NpmInstallTask>(NpmInstallTask.NAME)
        project.tasks.register<YarnInstallTask>(YarnInstallTask.NAME)
        project.tasks.register<NodeSetupTask>(NodeSetupTask.NAME)
        project.tasks.register<NpmSetupTask>(NpmSetupTask.NAME)
        project.tasks.register<YarnSetupTask>(YarnSetupTask.NAME)
    }

    private fun addNpmRule() { // note this rule also makes it possible to specify e.g. "dependsOn npm_install"
        project.tasks.addRule("Pattern: \"npm_<command>\": Executes an NPM command.") {
            val taskName = this
            if (taskName.startsWith("npm_")) {
                project.tasks.create(taskName, NpmTask::class.java) {
                    val tokens = taskName.split("_").drop(1) // all except first
                    npmCommand.set(tokens)
                    if (tokens.first().equals("run", ignoreCase = true)) {
                        dependsOn(NpmInstallTask.NAME)
                    }
                }
            }
        }
    }

    private fun addYarnRule() { // note this rule also makes it possible to specify e.g. "dependsOn yarn_install"
        project.tasks.addRule("Pattern: \"yarn_<command>\": Executes an Yarn command.") {
            val taskName = this
            if (taskName.startsWith("yarn_")) {
                project.tasks.create(taskName, YarnTask::class.java) {
                    val tokens = taskName.split("_").drop(1) // all except first
                    yarnCommand.set(tokens)
                    if (tokens.first().equals("run", ignoreCase = true)) {
                        dependsOn(YarnInstallTask.NAME)
                    }
                }
            }
        }
    }

    private fun addRepository(distUrl: String) {
        project.repositories.ivy {
            name = "Node.js"
            setUrl(distUrl)
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }
    }

    private fun configureNodeSetupTask(nodeExtension: NodeExtension) {
        val variantComputer = VariantComputer()
        val nodeArchiveDependencyProvider = variantComputer.computeNodeArchiveDependency(nodeExtension)
        val archiveFileProvider = nodeArchiveDependencyProvider.map { nodeArchiveDependency ->
            resolveNodeArchiveFile(nodeArchiveDependency)
        }

        project.tasks.named<NodeSetupTask>(NodeSetupTask.NAME) {
            nodeArchiveFile.set(project.layout.file(archiveFileProvider))
        }
    }

    private fun resolveNodeArchiveFile(name: String): File {
        val dependency = project.dependencies.create(name)
        val configuration = project.configurations.detachedConfiguration(dependency)
        configuration.isTransitive = false
        return configuration.resolve().single()
    }

    companion object {
        const val NODE_GROUP = "Node"
        const val NPM_GROUP = "npm"
        const val YARN_GROUP = "Yarn"

        // For more info, see:
        //   https://github.com/vaadin/flow/tree/master/flow-maven-plugin
        //   https://vaadin.com/docs/v14/flow/production/tutorial-production-mode-advanced.html
        fun compileVaadinResources(project: Project, productionMode: Boolean) {
            val variantComputer = VariantComputer()

            val config = project.extensions.getByName("vaadin") as VaadinConfig
            val nodeExtension = NodeExtension[project]

            val nodeDirProvider = variantComputer.computeNodeDir(nodeExtension)
//            val npmDirProvider = variantComputer.computeNpmDir(nodeExtension, nodeDirProvider)
            val nodeDir = nodeDirProvider.get().asFile
            val nodeModulesDir = variantComputer.computeNodeModulesDir(nodeExtension).get().asFile


            // SEE: com.vaadin.flow.server.startup.DevModeInitializer

            val baseDir = project.rootDir

            val buildDir = baseDir.resolve("build")
            val frontendDir = baseDir.resolve(FrontendUtils.FRONTEND)

            val webAppDir = baseDir.resolve("resources")
            val metaInfDir = webAppDir.resolve("META-INF")

            val vaadinDir = metaInfDir.resolve("resources").resolve("VAADIN")
            val generatedDir = buildDir.resolve(FrontendUtils.FRONTEND)

            // This file also points to the generated package file in the generated frontend dir
            val origWebPackFile = baseDir.resolve(FrontendUtils.WEBPACK_CONFIG)
            val origWebPackProdFile = baseDir.resolve("webpack.production.js")

            val jsonPackageFile = buildDir.resolve(Constants.PACKAGE_JSON)
            val jsonPackageLockFile = buildDir.resolve("package-lock.json")
            val webPackFile = buildDir.resolve(FrontendUtils.WEBPACK_CONFIG)
            val webPackProdFile = buildDir.resolve("webpack.production.js")
            val webPackExecutableFile = nodeModulesDir.resolve("webpack").resolve("bin").resolve("webpack.js").absoluteFile

            val generatedJsonPackageFile = generatedDir.resolve(Constants.PACKAGE_JSON)


            val tokenFile: File
            val generatedFilesDir: File

            // setup the token file
            run {
                // This matches the AppLauncher!
                val prodTokenFile = vaadinDir.resolve(FrontendUtils.TOKEN_FILE)
                val devTokenFile = buildDir.resolve(FrontendUtils.TOKEN_FILE)

                // always delete BOTH token files! We read these files in the launcher to determine if we are in DEV or PROD mode!
                prodTokenFile.delete()
                devTokenFile.delete()

                val prodGeneratedFilesDir = vaadinDir.resolve(FrontendUtils.FRONTEND).absoluteFile
                val devGeneratedFilesDir = buildDir.resolve(FrontendUtils.FRONTEND).absoluteFile

                if (productionMode) {
                    tokenFile = prodTokenFile
                    generatedFilesDir = prodGeneratedFilesDir
                    devGeneratedFilesDir.deleteRecursively() // always delete the OTHER generated directory
                } else {
                    tokenFile = devTokenFile
                    generatedFilesDir = devGeneratedFilesDir
                    prodGeneratedFilesDir.deleteRecursively() // always delete the OTHER generated directory
                }
            }

            // REGARDING the current version of polymer.
            // see: com.vaadin.flow.server.frontend.NodeUpdater.updateMainDefaultDependencies
            val polymerVersion = "3.2.0"


            println("\tCompiling Vaadin resources")
            println("\tProduction mode: $productionMode")

            println("\t\tPolymer version: $polymerVersion")
            println("\t\tBase Dir: $baseDir")
            println("\t\tNode Dir: ${nodeDir}")
            println("\t\tGenerated Dir: $generatedDir")
            println("\t\tWebPack Executable: $webPackExecutableFile")


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
            val projectDependencies: List<File> =
                resolveRuntimeDependencies(project).dependencies.flatMap { dep -> dep.artifacts.map { artifact -> artifact.file } }
            //    val projectDependencies = resolve(project.configurations["default"]).map { it.file }

            val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer

            val classPath = sourceSets.getByName("main").output.classesDirs.map { it.absoluteFile } as MutableList<File>
            classPath.addAll(projectDependencies)

            val customClassFinder = CustomClassFinder(classPath, projectDependencies)


            try {
                ////////////////////////////////
                // PREPARE FRONT END
                ////////////////////////////////
                run {
                    println("\tPreparing frontend...")

                    // always delete the VAADIN directory!
                    vaadinDir.deleteRecursively()

                    // always delete the generated directory
                    generatedFilesDir.deleteRecursively()


                    // copy our package.json + package-lock.json + webpack.config.js files to the build dir (only if they are different!)
                    Updater.compareAndCopy(origWebPackFile, webPackFile)
                    Updater.compareAndCopy(origWebPackProdFile, webPackProdFile)


                    if (!generatedDir.exists() && !generatedDir.mkdirs()) {
                        throw GradleException("Unable to continue. Target generation dir $generatedDir cannot be created")
                    }

                    val wasModified = Updater.createMissingPackageJson(jsonPackageFile, generatedJsonPackageFile, polymerVersion)
                    if (wasModified) {
                        println("\t\tPackage file was modified!")
                    }

                    val flowImportFile = generatedFilesDir.resolve(FrontendUtils.IMPORTS_NAME)

                    // we have to make additional customizations to the webpack.generated.js file
                    val relativeStaticResources = Updater.relativize(baseDir, metaInfDir).replace("./", "")
                    Updater.generateWebPackGeneratedConfig(
                        webPackFile,
                        frontendDir,
                        vaadinDir,
                        flowImportFile,
                        relativeStaticResources,
                        customClassFinder
                    )
                }

                ////////////////////////////////
                // BUILD FRONT END
                ////////////////////////////////

                run {
                    println("\t\tCreating configuration token file: $tokenFile")

                    // propagateBuildInfo & updateBuildInfo (combined from maven goals, because the token file is ONLY used for enableImportsUpdate)
                    val buildInfo = Json.createObject()

                    buildInfo.put(Constants.SERVLET_PARAMETER_COMPATIBILITY_MODE, false)
                    buildInfo.put(Constants.SERVLET_PARAMETER_PRODUCTION_MODE, productionMode)
                    buildInfo.put("polymer.version", polymerVersion)

                    // used for defining folder paths for dev server
                    if (!productionMode) {
                        buildInfo.put(Constants.NPM_TOKEN, buildDir.absolutePath)
                        buildInfo.put(Constants.GENERATED_TOKEN, generatedDir.absolutePath)
                        buildInfo.put(Constants.FRONTEND_TOKEN, frontendDir.absolutePath)
                    }

                    buildInfo.put(Constants.SERVLET_PARAMETER_ENABLE_DEV_SERVER, !productionMode)

                    tokenFile.ensureParentDirsCreated()
                    Updater.writeJson(tokenFile, buildInfo)
                    tokenFile.writeText(JsonUtil.stringify(buildInfo, 2) + "\n", Charsets.UTF_8)

                    // println("\t\tToken content:\n ${tokenFile.readText(Charsets.UTF_8)}")
                }

                // now run NodeUpdater
                println("\tBuilding frontend...")

                // enablePackagesUpdate OR enableImportsUpdate
                println("\t\tGenerating web-components into $generatedDir")
                val gen = com.vaadin.flow.server.frontend.FrontendWebComponentGenerator(customClassFinder)
                gen.generateWebComponents(generatedDir)

                run {
                    // TaskUpdatePackages
                    println("\tUpdating package dependency and hash information")

                    // this cannot be refactored out! (as tempting as that might be...)
                    val frontendDependencies = FrontendDependenciesScanner.FrontendDependenciesScannerFactory()
                        .createScanner(false, customClassFinder, true)

                    val packageJson = Updater.getOrCreateJson(generatedJsonPackageFile)

                    val updateResult = Updater.updateGeneratedPackageJsonDependencies(
                        packageJson,
                        frontendDependencies.packages,
                        jsonPackageFile, generatedJsonPackageFile, nodeModulesDir, jsonPackageLockFile
                    )

                    val isModified = updateResult.first
                    if (isModified) {
                        Updater.writeJson(generatedJsonPackageFile, packageJson)
                    }

                    val hashIsModified = Updater.updatePackageHash(jsonPackageFile, packageJson)
                    if (hashIsModified) {
                        println("\t\tPackage dependencies were modified!")
                    }

                    val doCleanup = updateResult.second
                    if (doCleanup) {
                        println("\t\t##########################################################")
                        println("\t\tNode.js information is different. Cleaning up directories!")
                        println("\t\t##########################################################")

                        // Removes package-lock.json file and node_modules folders in case the versions are different.
                        if (jsonPackageLockFile.exists()) {
                            if (!jsonPackageLockFile.delete()) {
                                throw GradleException(
                                    "Could not remove ${jsonPackageLockFile.path} file. This file has been generated with " +
                                            "a different platform version. Try to remove it manually."
                                )
                            }
                        }

                        if (nodeModulesDir.exists()) {
                            nodeModulesDir.deleteRecursively()
                        }

                        val generatedNodeModules = buildDir.resolve(FrontendUtils.NODE_MODULES)
                        if (generatedNodeModules.exists()) {
                            generatedNodeModules.deleteRecursively()
                        }
                    }

                    // also have to make sure that webpack is properly installed!
                    val webPackNotInstalled = !webPackExecutableFile.canRead()

                    if (doCleanup || isModified || hashIsModified || webPackNotInstalled) {
                        println("\t\tSomething changed, installing dependencies")
                        // must run AFTER package.json file is created **AND** packages are updated!
                        installPackageDependencies()

                        // for node_modules\@vaadin\vaadin-usage-statistics
                        //or you can disable vaadin-usage-statistics for the project by adding
                        //```
                        //   "vaadin": { "disableUsageStatistics": true }
                        //```
                        //to your project `package.json` and running `npm install` again (remove `node_modules` if needed).
                        //
                        //You can verify this by checking that `vaadin-usage-statistics.js` contains an empty function.
                    }
                }

                if (!productionMode) {
                    // the dev mode initializer from the App Launcher will build everything following, but in a special way
                    return
                }

                // copyResources
                run {
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


                    val targetDirectory = nodeModulesDir.resolve(FrontendUtils.FLOW_NPM_PACKAGE_NAME)
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
                    val jarContentsManager = com.vaadin.flow.server.frontend.JarContentsManager()
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
                        frontendDir.absoluteFile
                            .copyRecursively(targetDirectory, true)

                        ms = (System.nanoTime() - start) / 1000000
                        println("\t\tCopied frontend directory $frontendDir")
                        println("\t\tCopied frontend directory in $ms ms")
                    } else {
                        println("\t\tFound no local frontend resources for the project")
                    }
                }

                // enableImportsUpdate
                run {
                    val start = System.nanoTime()

                    println("\tGenerating vaadin flow files...")
                    val genFile = generatedFilesDir.resolve("generated-flow-imports.js")
                    val genFallbackFile = generatedFilesDir.resolve("generated-flow-imports-fallback.js")

                    println("\t\tGenerating  $genFile")
                    println("\t\tGenerating  $genFallbackFile")

                    val frontendDependencies = FrontendDependenciesScanner.FrontendDependenciesScannerFactory()
                        .createScanner(false, customClassFinder, true)

                    val provider =
                        com.vaadin.flow.function.SerializableFunction<com.vaadin.flow.server.frontend.scanner.ClassFinder, com.vaadin.flow.server.frontend.scanner.FrontendDependenciesScanner> { t ->
                            FrontendDependenciesScanner.FrontendDependenciesScannerFactory().createScanner(true, t, true)
                        }

                    val taskUpdateClass = com.vaadin.flow.server.frontend.TaskUpdateImports::class.java
                    val constructor = taskUpdateClass.declaredConstructors.first { constructor -> constructor.parameterCount == 8 }
                    constructor.isAccessible = true

                    val updateImports = constructor.newInstance(
                        customClassFinder, frontendDependencies, provider,
                        jsonPackageFile.parentFile, // folder with the `package.json` file
                        generatedFilesDir, // folder where flow generated files will be placed.
                        frontendDir,  // a directory with project's frontend files
                        tokenFile, Updater.getJson(tokenFile)
                    ) as com.vaadin.flow.server.frontend.TaskUpdateImports

                    updateImports.execute()

                    val ms = (System.nanoTime() - start) / 1000000
                    println("\t\tFinished in $ms ms")
                }

                run {
                    val start = System.nanoTime()
                    println("\tConfiguring WebPack")

                    val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
                    val nodeExec = variantComputer.computeNodeExec(nodeExtension, nodeBinDirProvider).get()

                    val npmDir = variantComputer.computeNpmDir(nodeExtension, nodeDirProvider)
                    val npmBinDir = variantComputer.computeNpmBinDir(npmDir).get().asFile.absolutePath
                    val nodeBinDir = nodeBinDirProvider.get().asFile.absolutePath
                    val nodePath = npmBinDir + File.pathSeparator + nodeBinDir


                    // For information about webpack, SEE https://webpack.js.org/guides/getting-started/

                    val exec = Exec(project)
                    exec.executable = nodeExec
                    exec.path = nodePath
                    exec.workingDir = variantComputer.computeNodeModulesDir(nodeExtension).get().asFile


                    exec.workingDir = buildDir
                    exec.arguments = listOf(webPackExecutableFile.path, "--config", webPackProdFile.absolutePath, "--silent")
                    exec.suppressOutput = false
                    exec.debug = false
                    exec.execute()

                    val ms = (System.nanoTime() - start) / 1000000
                    println("\t\tFinished in $ms ms")
                }
            } catch (e: Exception) {
                throw GradleException(e.message ?: "", e)
            } finally {
                customClassFinder.finish()
            }
        }

        fun installPackageDependencies() {
            // now we have to install the dependencies from package.json! We do this MANUALLY, instead of using the builder
            println("\tInstalling package dependencies --- FIXEME")

//            val packageInstallExec = getExec()
//            packageInstallExec.environment["ADBLOCK"] = "1"
//            packageInstallExec.environment["NO_UPDATE_NOTIFIER"] = "1"
//            packageInstallExec.arguments = listOf(npmScriptFile, "install")
//            packageInstallExec.execute()
        }

        /**
         * Recursively resolves all child dependencies of the project
         *
         * THIS MUST BE IN "afterEvaluate" or run from a specific task.
         */
        fun resolveAllDependencies(project: Project): List<DependencyScanner.DependencyData> {
            // NOTE: we cannot createTree("compile") and createTree("runtime") using the same exitingNames and expect correct results.
            // This is because a dependency might exist for compile and runtime, but have different children, therefore, the list
            // will be incomplete

            // there will be DUPLICATES! (we don't care about children or hierarchy, so we remove the dupes)
            return (DependencyScanner.scan(project, "compileClasspath") +
                    DependencyScanner.scan(project, "runtimeClasspath")
                    ).toSet().toList()
        }

        /**
         * Recursively resolves all child compile dependencies of the project
         *
         * THIS MUST BE IN "afterEvaluate" or run from a specific task.
         */
        fun resolveRuntimeDependencies(project: Project): DependencyScanner.ProjectDependencies {
            val projectDependencies = mutableListOf<DependencyScanner.Dependency>()
            val existingNames = mutableMapOf<String, DependencyScanner.Dependency>()

            DependencyScanner.createTree(project, "runtimeClasspath", projectDependencies, existingNames)

            return DependencyScanner.ProjectDependencies(projectDependencies, existingNames.map { it.value })
        }
    }
}
