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
import dorkbox.vaadin.VaadinApplication
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.tasks.TaskInputs
import org.gradle.jvm.tasks.Jar

/**
 * For managing Vaadin gradle tasks
 *
 * NOTE: Vaadin css resources are compiled into the stats.json file, they are NOT loaded "statically" from the webserver
 */
@Suppress("UnstableApiUsage", "unused", "SameParameterValue", "ObjectLiteralToLambda")
class Vaadin : Plugin<Project> {
    companion object {
        internal const val NODE_GROUP = "Node"
        internal const val NPM_GROUP = "npm"

        internal const val SHUTDOWN_TASK = "shutdownCompiler"
        internal const val compileDevName = "vaadinDevelopment"
        internal const val compileProdName = "vaadinProduction"

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

    /** Useful, so we can announce the version of vaadin we are using */
    val version = VaadinApplication.vaadinVersion

    private fun newTask(dependencyTask: Task,
                        taskName: String,
                        description: String,
                        inputs: TaskInputs.()->Unit = {},
                        action: Task.(vaadinCompiler: VaadinCompiler) -> Unit): Task {
        return newTask(dependencyTask.name, taskName, description, inputs, action)
    }

    @Suppress("ObjectLiteralToLambda")
    private fun newTask(dependencyName: String,
                        taskName: String,
                        description: String,
                        inputs: TaskInputs.()->Unit = {},
                        action: Task.(vaadinCompiler: VaadinCompiler) -> Unit): Task {
        return newTask(listOf(dependencyName), taskName, description, inputs, action)
    }

    private fun newTask(dependencyName: List<String>,
                        taskName: String,
                        description: String,
                        inputs: TaskInputs.()->Unit = {},
                        action: Task.(vaadinCompiler: VaadinCompiler) -> Unit): Task {

        return project.tasks.create(taskName).apply {
            dependsOn(dependencyName.toTypedArray())
            finalizedBy(SHUTDOWN_TASK)

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

            add("implementation", "io.undertow:undertow-core:${VaadinConfig.UNDERTOW_VERSION}")
            add("implementation", "io.undertow:undertow-servlet:${VaadinConfig.UNDERTOW_VERSION}")
            add("implementation", "io.undertow:undertow-websockets-jsr:${VaadinConfig.UNDERTOW_VERSION}")
        }

        // NOTE: NPM will ALWAYS install packages to the "node_modules" directory that is a sibling to the packages.json directory!

        addGlobalTypes()
        addTasks()
        addNpmRule()

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
                        project.tasks.withType(Jar::class.java) {
                            // we ALWAYS want to make sure that this task runs. If *something* is cached, then there jar file output will be incomplete.
                            it.outputs.cacheIf { false }
                            it.outputs.upToDateWhen { false }
                        }
                    }
                }
            }
        })

        project.tasks.create(SHUTDOWN_TASK).apply {
            doLast(object: Action<Task> {
                override fun execute(t: Task) {
                    val vaadinCompiler = VaadinConfig[project].vaadinCompiler
                    // every task MUST call shutdown in order to close the class-scanner (otherwise it will hold open files)
                    vaadinCompiler.finish()
                }
            })
        }

        // NOTE! our class-scanner scans COMPILED CLASSES, so it is required to depend (at some point) on class compilation!
        val generateWebComponents = newTask(listOf(NodeSetupTask.NAME, "classes"), "generateWebComponents", "Generate Vaadin web components")
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
            dependsOn(createTokenFile)
            finalizedBy(SHUTDOWN_TASK)

            group = "vaadin"
            description = "Compile Vaadin resources for Development"

            outputs.cacheIf { false }
            outputs.upToDateWhen { false }

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
            dependsOn(generateWebPack)
            finalizedBy(SHUTDOWN_TASK)

            group = "vaadin"
            description = "Compile Vaadin resources for Production"

            outputs.cacheIf { false }
            outputs.upToDateWhen { false }

//            inputs.files(
//                "${project.projectDir}/package.json",
//                "${project.projectDir}/package-lock.json",
//                "${project.projectDir}/webpack.config.js",
//                "${project.projectDir}/webpack.production.js"
//            )
//
//            outputs.dir("${project.buildDir}/resources/main/META-INF/resources/VAADIN")
//            outputs.dir("${project.buildDir}/node_modules")
        }

        project.tasks.withType(Jar::class.java) {
            // we ALWAYS want to make sure that this task runs when jar files are created (since we consider a jar to be the final production package for this project
            it.dependsOn(compileProdName)
        }


//        config.addSubprojects()

//        project.childProjects.values.forEach {
//            it.pluginManager.apply(Vaadin::class.java)
//        }
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
        addGlobalType<ProxySettings>()
    }

    private inline fun <reified T> addGlobalType() {
        project.extensions.extraProperties[T::class.java.simpleName] = T::class.java
    }

    private fun addTasks() {
        project.tasks.register(NpmInstallTask.NAME, NpmInstallTask::class.java)
        project.tasks.register(NodeSetupTask.NAME, NodeSetupTask::class.java)
        project.tasks.register(NpmSetupTask.NAME, NpmSetupTask::class.java)
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
}
