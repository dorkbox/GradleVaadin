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

import dorkbox.gradleVaadin.node.deps.DependencyScanner
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
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.tasks.TaskInputs
import java.io.File

/**
 * For managing Vaadin gradle tasks
 */
@Suppress("UnstableApiUsage", "unused", "SameParameterValue")
class Vaadin : Plugin<Project> {
    companion object {
        const val NODE_GROUP = "Node"
        const val NPM_GROUP = "npm"
        const val YARN_GROUP = "Yarn"

        const val SHUTDOW_TASK = "shutdownCompiler"
        const val compileDevName = "compileResources-DEV"
        const val compileProdName = "compileResources-PROD"

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

    private lateinit var project: Project
    private lateinit var config: VaadinConfig


    @Suppress("ObjectLiteralToLambda")
    private fun newTask(dependencyTask: Task,
                        taskName: String,
                        description: String,
                        inputs: TaskInputs.()->Unit = {},
                        action: Task.(vaadinCompiler: VaadinCompiler) -> Unit): Task {
        return project.tasks.create(taskName).apply {
            dependsOn(dependencyTask)
            finalizedBy(SHUTDOW_TASK)

            group = "vaadin"
            this.description = description

            inputs(this.inputs)

            doLast(object: Action<Task> {
                override fun execute(task: Task) {
                    action(task, VaadinConfig[project].vaadinCompiler)
                }
            })
        }
    }

    override fun apply(project: Project) {
        this.project = project

        // https://discuss.gradle.org/t/can-a-plugin-itself-add-buildscript-dependencies-and-then-apply-a-plugin/25039/4
        apply("java")

        // Create the Plugin extension object (for users to configure publishing).
        config = VaadinConfig.create(project)

        project.repositories.apply {
            maven { it.setUrl("https://maven.vaadin.com/vaadin-addons") } // Vaadin Addons
            maven { it.setUrl("https://maven.vaadin.com/vaadin-prereleases") } // Pre-releases
            maven { it.setUrl("https://oss.sonatype.org/content/repositories/vaadin-snapshots") } // Vaadin Snapshots
        }

        project.dependencies.apply {
            add("implementation", "com.vaadin:vaadin:${VaadinConfig.VAADIN_VERSION}")
            add("implementation", "com.dorkbox:VaadinUndertow:${VaadinConfig.MAVEN_VAADIN_GRADLE_VERSION}")
        }

        // NOTE: NPM will ALWAYS install packages to the "node_modules" directory that is a sibling to the packages.json directory!

        addGlobalTypes()
        addTasks()
        addNpmRule()
        addYarnRule()


        project.gradle.taskGraph.whenReady(object: Action<TaskExecutionGraph> {
            override fun execute(graph: TaskExecutionGraph) {
                // every other task will do nothing (run as dev mode).
                val allTasks = graph.allTasks
                var hasVaadinTask = false
                if (allTasks.firstOrNull { it.name == compileProdName } != null) {
                    config.productionMode.set(true)
                    hasVaadinTask = true
                }
                if (allTasks.firstOrNull { it.name == compileDevName } != null) {
                    hasVaadinTask = true
                }

                if (hasVaadinTask) {
                    val jarTasks = allTasks.filter { it.name.endsWith("jar")}
                    if (jarTasks.isNotEmpty()) {
                        println("\tDisabling the gradle cache for:")
                    }
                    jarTasks.forEach {
                        println("\t\t${it.project.name}:${it.name}")
                        // we ALWAYS want to make sure that this task runs. If *something* is cached, then there jar file output will be incomplete.
                        it.outputs.upToDateWhen { false }
                    }
                }
            }
        })

        project.afterEvaluate { proj ->
            try {
                if (config.download.get()) {
                    config.distBaseUrl.orNull?.let { addNodeRepository(it) }

                    val nodeArchiveDependency = VariantComputer.computeNodeArchiveDependency(config)
                    val archiveFileProvider = resolveNodeArchiveFile(nodeArchiveDependency)

                    proj.tasks.named(NodeSetupTask.NAME, NodeSetupTask::class.java) { nodeTask ->
                        val provider = proj.objects.fileProperty().apply {
                            set(archiveFileProvider)
                        }.asFile

                        nodeTask.nodeArchiveFile.set(proj.layout.file(provider))
                    }
                }
            } catch (e: Exception) {
                println("Unable to configure NodeJS repository: ${config.nodeVersion}")
                e.printStackTrace()
            }
        }

        project.tasks.create(SHUTDOW_TASK).apply {
            doLast(object: Action<Task> {
                override fun execute(t: Task) {
                    val vaadinCompiler = VaadinConfig[project].vaadinCompiler
                    // every task MUST call shutdown in order to close the class-scanner (otherwise it will hold open files)
                    vaadinCompiler.finish()
                }
            })
        }


        val nodeSetup = project.tasks.named(NodeSetupTask.NAME).get().apply {
            // our class-scanner scans COMPILED CLASSES, so this is required.
            dependsOn(project.tasks.named("classes"))
        }

        val generateWebComponents = newTask(nodeSetup, "generateWebComponents", "Generate Vaadin web components")
        { vaadinCompiler ->
            VaadinConfig[project].vaadinCompiler.log()
            vaadinCompiler.generateWebComponents()
        }

        val createMissingPackageJson = newTask(generateWebComponents, "createMissingPackageJson", "Prepare Vaadin frontend")
        { vaadinCompiler ->
//            VaadinCompile.print()
            vaadinCompiler.createMissingPackageJson()
        }

        val prepareJsonFiles = newTask(createMissingPackageJson, "prepareJsonFiles", "Prepare Vaadin frontend",
            {
//            files(vaadinCompiler.jsonPackageFile)
            })
        { vaadinCompiler ->
            vaadinCompiler.prepareJsonFiles()
        }
//
        /**
         * Get if the stats.json file should be retrieved from an external service
         * or through the classpath.
         *
         * @return true if stats.json is served from an external location
         */
//        default boolean isStatsExternal() {
//            return getBooleanProperty(Constants.EXTERNAL_STATS_FILE, false);
//        }
// /**
        //     * Get the url from where stats.json should be retrieved from. If not given
        //     * this will default to '/vaadin-static/VAADIN/config/stats.json'
        //     *
        //     * @return external stats.json location
        //     */
        //    default String getExternalStatsUrl() {
        //        return getStringProperty(Constants.EXTERNAL_STATS_URL,
        //                Constants.DEFAULT_EXTERNAL_STATS_URL);
        //    }


        // private static InputStream getStatsFromClassPath(VaadinService service) {
        //        String stats = service.getDeploymentConfiguration()
        //                .getStringProperty(SERVLET_PARAMETER_STATISTICS_JSON,
        //                        VAADIN_SERVLET_RESOURCES + STATISTICS_JSON_DEFAULT)
        //                // Remove absolute
        //                .replaceFirst("^/", "");
        //        InputStream stream = service.getClassLoader()
        //                .getResourceAsStream(stats);
        //        if (stream == null) {
        //            getLogger().error(
        //                    "Cannot get the 'stats.json' from the classpath '{}'",
        //                    stats);
        //        }
        //        return stream;
        //    }

        val copyJarResources = newTask(prepareJsonFiles, "copyJarResources", "Compile Vaadin resources for Production")
        { vaadinCompiler ->
            vaadinCompiler.copyJarResources()
        }

        val copyLocalResources = newTask(copyJarResources, "copyLocalResources", "Compile Vaadin resources for Production")
        { vaadinCompiler ->
            vaadinCompiler.copyFrontendResourcesDirectory()
        }

        val createTokenFile = newTask(copyLocalResources, "createTokenFile", "Create Vaadin token file")
        { vaadinCompiler ->
            vaadinCompiler.createTokenFile()
        }

        val updateWebPack = newTask(createTokenFile, "updateWebPack", "Compile Vaadin resources for Production")
        { vaadinCompiler ->
            vaadinCompiler.fixWebpackTemplate()
        }

        // last one from NodeTasks.java
        val enableImportsUpdate = newTask(updateWebPack, "enableImportsUpdate", "Compile Vaadin resources for Production")
        { vaadinCompiler ->
            vaadinCompiler.enableImportsUpdate()
        }

        // FOR DEV MODE, THIS IS AS FAR AS WE GO
        // last DevModeHandler start




        // the plugin on start needs to rewrite DevModeInitializer.initDevModeHandler so that all these tasks aren't run every time for dev mode
        // because that is really slow to start up??
        // ALTERNATIVELY, devmodeinit runs the same thing as the gradle plugin, but the difference is we only run the full gradle version
        // for a production build (webpack is compiled instead of running dev-mode, is the only difference...).
        //   the only problem, is when running as a JPMS module...
        //      to solve this, we could modify the byte-code -- then RECREATE the jar (which we would then startup)
        //         OR.. we modify the bytecode ON COMPILE, so that a "run" would always include the modified jar
        // or we just include our own version




        val generateWebPack = newTask(enableImportsUpdate, "generateWebPack", "Compile Vaadin resources for Production")
        { vaadinCompiler ->
            vaadinCompiler.generateWebPack()
        }

        project.tasks.create(compileDevName).apply {
            dependsOn(createTokenFile, project.tasks.named("classes"))
            finalizedBy(SHUTDOW_TASK)

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
        }


        project.tasks.create(compileProdName).apply {
            dependsOn(generateWebPack, project.tasks.named("classes"))
            finalizedBy(SHUTDOW_TASK)

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

            outputs.dir("${project.buildDir}/resources/main/META-INF/resources/VAADIN")
            outputs.dir("${project.buildDir}/node_modules")
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
        project.tasks.register(NpmInstallTask.NAME, NpmInstallTask::class.java)
        project.tasks.register(YarnInstallTask.NAME, YarnInstallTask::class.java)
        project.tasks.register(NodeSetupTask.NAME, NodeSetupTask::class.java)
        project.tasks.register(NpmSetupTask.NAME, NpmSetupTask::class.java)
        project.tasks.register(YarnSetupTask.NAME, YarnSetupTask::class.java)
    }

    private fun addNpmRule() { // note this rule also makes it possible to specify e.g. "dependsOn npm_install"
        project.tasks.addRule("Pattern: \"npm_<command>\": Executes an NPM command.") { taskName ->
            if (taskName.startsWith("npm_")) {
                project.tasks.create(taskName, NpmTask::class.java) {
                    val tokens = taskName.split("_").drop(1) // all except first
                    it.npmCommand.set(tokens)
                    if (tokens.first().equals("run", ignoreCase = true)) {
                        it.dependsOn(NpmInstallTask.NAME)
                    }
                }
            }
        }
    }

    private fun addYarnRule() { // note this rule also makes it possible to specify e.g. "dependsOn yarn_install"
        project.tasks.addRule("Pattern: \"yarn_<command>\": Executes an Yarn command.") { taskName ->
            if (taskName.startsWith("yarn_")) {
                project.tasks.create(taskName, YarnTask::class.java) {
                    val tokens = taskName.split("_").drop(1) // all except first
                    it.yarnCommand.set(tokens)
                    if (tokens.first().equals("run", ignoreCase = true)) {
                        it.dependsOn(YarnInstallTask.NAME)
                    }
                }
            }
        }
    }

    private fun addNodeRepository(distUrl: String) {
        project.repositories.ivy {
            it.name = "Node.js"
            it.setUrl(distUrl)
            it.patternLayout { t ->
                t.artifact("[revision]/[artifact](-[revision]-[classifier]).[ext]")
            }
            it.metadataSources { t ->
                t.artifact()
            }
            it.content { t ->
                t.includeModule("org.nodejs", "node")
            }
        }
    }

    private fun resolveNodeArchiveFile(name: String): File {
        val dependency = project.dependencies.create(name)
        val configuration = project.configurations.detachedConfiguration(dependency)
        configuration.isTransitive = false
        return configuration.resolve().single()
    }
}
