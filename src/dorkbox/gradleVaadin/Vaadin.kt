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

import com.vaadin.flow.server.frontend.TaskCopyFrontendFiles_
import com.vaadin.flow.server.frontend.TaskGenerateTsFiles_
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
import java.io.File

/**
 * For managing Vaadin gradle tasks
 *
 * NOTE: Vaadin css resources are compiled into the stats.json file, they are NOT loaded "statically" from the webserver
 */
@Suppress("unused", "SameParameterValue", "ObjectLiteralToLambda")
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

        private fun String.startColon(): String {
            return if (this.startsWith(":")) {
                this
            } else {
                ":$this"
            }
        }

        private fun Project.newTask(dependencyTask: Task, taskName: String, description: String): Task {
            return newTask(dependencyTask.name, taskName, description)
        }

        private fun Project.newTask(dependencyName: String, taskName: String, description: String): Task {
            return newTask(listOf(dependencyName), taskName, description)
        }

        private fun Project.newTask(dependencyNames: List<String>, taskName: String, description: String): Task {
            return this.tasks.create(taskName).apply {
                dependsOn(dependencyNames.toTypedArray())
                finalizedBy(SHUTDOWN_TASK)

                group = "vaadin"
                this.description = description
            }
        }

        private fun buildName(project: Project?): String {
            return when (project) {
                null -> ":"
                project.rootProject -> ""
                else -> buildName(project.parent) + ":${project.name}"
            }
        }

        fun buildName(task: Task): String {
            return buildName(task.project) + ":${task.name}"
        }


        private fun Project.isStartupTask(vararg taskNames: String): Boolean {
            // get the list of startup tasks
            this.gradle.startParameter.taskNames.forEach { startTaskName ->
                if (taskNames.contains(startTaskName)) {
                    return true
                }
            }

            return false
        }

        private fun Project.isStartupTask(vararg tasks: Task): Boolean {
            val taskNames = tasks.map { it.name }

            // get the list of startup tasks
            this.gradle.startParameter.taskNames.forEach { startTaskName ->
                if (taskNames.contains(startTaskName)) {
                    return true
                }
            }

            return false
        }

        fun repositories(project: Project) {
            project.repositories.apply {
                maven { it.setUrl("https://maven.vaadin.com/vaadin-addons") } // Vaadin Addons
                maven { it.setUrl("https://maven.vaadin.com/vaadin-prereleases") } // Pre-releases
                maven { it.setUrl("https://oss.sonatype.org/content/repositories/vaadin-snapshots") } // Vaadin Snapshots
            }
        }
    }

    private lateinit var config: VaadinConfig

    /** Useful, so we can announce the version of vaadin we are using */
    val version = VaadinApplication.vaadinVersion

    @Volatile
    private var allTasks: Map<String, Task>? = null
    private fun allTasks(project: Project): Map<String, Task> {
        var tasks = this.allTasks

        if (tasks == null) {
            val allTasks = project.rootProject.getAllTasks(true).flatMap { it.value }
            val allTasksNames = mutableMapOf<String, Task>()

            allTasks.forEach {
//                println("\t${buildName(it)}")
                allTasksNames[buildName(it)] = it
            }

            tasks = allTasksNames
            this.allTasks = tasks
        }

        return tasks
    }

    override fun apply(project: Project) {
        // https://discuss.gradle.org/t/can-a-plugin-itself-add-buildscript-dependencies-and-then-apply-a-plugin/25039/4
        project.applyId("java")

        // Create the Plugin extension object (for users to configure publishing).
        config = VaadinConfig.create(project)

        repositories(project)

        project.dependencies.apply {
            // API is so the dependency project can use undertow/etc without having to explicitly define it (since we already include it)
            add("api", "com.vaadin:vaadin:${VaadinConfig.VAADIN_VERSION}")
            add("api", "com.dorkbox:VaadinUndertow:${VaadinConfig.MAVEN_VAADIN_GRADLE_VERSION}")

            add("api", "io.undertow:undertow-core:${VaadinConfig.UNDERTOW_VERSION}")
            add("api", "io.undertow:undertow-servlet:${VaadinConfig.UNDERTOW_VERSION}")
            add("api", "io.undertow:undertow-websockets-jsr:${VaadinConfig.UNDERTOW_VERSION}")

            // Vaadin 14.9 changed how license checking works, and doesn't include this.
            add("api", "com.github.oshi:oshi-core-java11:${VaadinConfig.OSHI_VERSION}")

            // license checker requires JNA
            add("api", "net.java.dev.jna:jna-jpms:${VaadinConfig.JNA_VERSION}")
            add("api", "net.java.dev.jna:jna-platform-jpms:${VaadinConfig.JNA_VERSION}")
        }

        // NOTE: NPM will ALWAYS install packages to the "node_modules" directory that is a sibling to the packages.json directory!

        addGlobalTypes(project)
        project.tasks.register(NpmInstallTask.NAME, NpmInstallTask::class.java)
        project.tasks.register(NodeSetupTask.NAME, NodeSetupTask::class.java)
        project.tasks.register(NpmSetupTask.NAME, NpmSetupTask::class.java)
        addNpmRule(project)

        val shutdownTask = project.tasks.create(SHUTDOWN_TASK).apply {
            doLast(object: Action<Task> {
                override fun execute(t: Task) {
                    val vaadinCompiler = VaadinConfig[project].vaadinCompiler
                    // every task MUST call shutdown in order to close the class-scanner (otherwise it will hold open files)
                    vaadinCompiler.finish()
                }
            })
        }

        val generateWebComponents = project.newTask(listOf(NodeSetupTask.NAME, "classes"), "generateWebComponents", "Generate Vaadin components")

        val prepareJsonFiles = project.newTask(generateWebComponents, "prepareJsonFiles", "Prepare Vaadin frontend")
        val copyJarResources = project.newTask(prepareJsonFiles, "copyJarResources", "Compile Vaadin resources for Production")
        val copyLocalResources = project.newTask(copyJarResources, "copyLocalResources", "Compile Vaadin resources for Production")
        val createTokenFile = project.newTask(copyLocalResources, "createTokenFile", "Create Vaadin token file")

        val devMode = project.newTask(createTokenFile, compileDevName, "Compile Vaadin resources for Development")

        val updateWebPack = project.newTask(createTokenFile, "updateWebPack", "Compile Vaadin resources for Production")


        // last one from NodeTasks.java
        val enableImportsUpdate = project.newTask(updateWebPack, "enableImportsUpdate", "Compile Vaadin resources for Production")
        val generateWebPack = project.newTask(enableImportsUpdate, "generateWebPack", "Compile Vaadin resources for Production")

        val prodMode = project.newTask(generateWebPack, compileProdName, "Compile Vaadin resources for Production")



        project.tasks.getByName("jar").apply {
            // NOTE: kotlin-jvm plugin will ALWAYS build jars during a compile!
            // we ALWAYS want to make sure that this task runs when jar files are created (since we consider a jar to be the final production package for this project.
            // Additionally, there are problems when we try to include resources that are implicitly used by another task. Gradle hates it.
            dependsOn(generateWebPack)
        }


        project.gradle.taskGraph.whenReady { taskGraph ->
            // NOTE! our class-scanner scans COMPILED CLASSES, so it is required to depend (at some point) on class compilation!
            val compiler = VaadinConfig[project].vaadinCompiler
            val nodeInfo = compiler.nodeInfo

            val prodStartup = project.isVaadinTheStartupTask(compileProdName)
            val devStartup = project.isVaadinTheStartupTask(compileDevName)

            val isExplicitRun = prodStartup || devStartup
            config.explicitRun.set(isExplicitRun)


            val prodName = buildName(project) + compileProdName.startColon()
            val devName = buildName(project) + compileDevName.startColon()

            val prodDep = taskGraph.hasTask(prodName)
            val devDep = taskGraph.hasTask(devName)

            val canRun = isExplicitRun || prodDep || devDep
            if (canRun) {
                config.productionMode.set(prodStartup || prodDep)
                compiler.log()
            } else {
                println("\t\tVaadin compile task not found in start parameters or task graph!")
            }

            generateWebComponents.apply {
                this.enabled = canRun
                if (isExplicitRun) {
                    outputs.upToDateWhen { false }
                }

                outputs.files(File(nodeInfo.frontendGeneratedDir, TaskGenerateTsFiles_.TSCONFIG_JSON),
                              nodeInfo.buildDirJsonPackageFile)

                doLast {
                    compiler.generateWebComponents()
                }
            }

            prepareJsonFiles.apply {
                this.enabled = canRun
                if (isExplicitRun) {
                    outputs.upToDateWhen { false }
                }

                inputs.files(nodeInfo.jsonPackageFile)
                outputs.files(nodeInfo.buildDirJsonPackageFile,
                              nodeInfo.buildDirJsonPackageLockFile)

                doLast {
                    // createMissingPackageJson
                    compiler.createMissingPackageJson()

                    // prepareJsonFiles
                    compiler.prepareJsonFiles()
                }
            }

            copyJarResources.apply {
                this.enabled = canRun
                if (isExplicitRun) {
                    outputs.upToDateWhen { false }
                }
                val task = TaskCopyFrontendFiles_(project, compiler, nodeInfo)

                // NOTE: cannot scan dependency classpath with moshix BEFORE moshiX is applied
//                    inputs.files(task.frontendLocations)

                outputs.dirs(
                    task.flowNpmTargetDirectory,
                    task.themeJarTargetDirectory
                )

                doLast {
                    task.execute()
                }
            }

            copyLocalResources.apply {
                this.enabled = canRun
                if (isExplicitRun) {
                    outputs.upToDateWhen { false }
                }

                inputs.dir(nodeInfo.frontendDir)
                outputs.dir(nodeInfo.createFrontendDir())

                doLast {
                    compiler.copyFrontendResourcesDirectory()
                }
            }

            createTokenFile.apply {
                this.enabled = canRun
                if (isExplicitRun) {
                    outputs.upToDateWhen { false }
                }

                outputs.file(nodeInfo.tokenFile)

                doLast {
                    compiler.createTokenFile()
                }
            }

            updateWebPack.apply {
                this.enabled = canRun
                if (isExplicitRun) {
                    outputs.upToDateWhen { false }
                }

                inputs.files(nodeInfo.origWebPackFile, nodeInfo.origWebPackProdFile)
                outputs.files(nodeInfo.webPackFile, nodeInfo.webPackProdFile, nodeInfo.webPackGeneratedFile)

                doLast {
                    compiler.fixWebpackTemplate()
                }
            }

            enableImportsUpdate.apply {
                this.enabled = canRun
                if (isExplicitRun) {
                    outputs.upToDateWhen { false }
                }

                outputs.files(nodeInfo.flowImportFile,
                              nodeInfo.flowFallbackImportFile)
                doLast {
                    compiler.enableImportsUpdate()
                }
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
            generateWebPack.apply {
                this.enabled = canRun
                if (isExplicitRun) {
                    outputs.upToDateWhen { false }
                }

                inputs.files(
                    "${project.projectDir}/webpack.config.js",
                    "${project.projectDir}/webpack.production.js"
                )

                outputs.file(nodeInfo.vaadinStatsJsonFile)

                doLast {
                    compiler.generateWebPack()
                }
            }

            shutdownTask.apply {
                this.enabled = canRun
            }
        }
    }

    // WOW... this is annoying. It must be in project.afterEvaluate, and the task dependencies MUST be BEFORE evaluate!
    // Gradle processes things in order for each stage of processing it has.
    // Task definitions will immediately execute!
    // NOTE: we want to discover if the "task to look for" is a dependency of one of the startup tasks
    private fun Task.isMyTaskAStartDependency(): Boolean {
        val taskToLookFor: Task = this
        val debug = config.debug

        val allTasksNames = allTasks(this.project)

        val taskToLookForName = buildName(taskToLookFor)
        if (debug) println("\tLooking for: $taskToLookForName")

        val taskList = LinkedList<Any>()

        // get the starting tasks
        this.project.gradle.startParameter.taskNames.forEach { startTaskNameOrig ->
            val startTaskName = startTaskNameOrig.startColon()

            if (taskToLookForName == startTaskName) {
                if (debug) println("\t\tFound task: $taskToLookForName")
                return true
            }

            if (debug) println("\tStart Parameters: $startTaskName")
            var task = allTasksNames[startTaskName]
            if (task == null) {
                // MAYBE it's the project parent class? gradle + intellij is weird.
                task = allTasksNames[buildName(this) + startTaskName]
            }

            if (task == null) {
                if (debug) println("\tUnable to evaluate start task: $startTaskNameOrig")
                return false
            }

            taskList.add(task)
        }

        while (taskList.isNotEmpty()) {
            when (val task = taskList.removeFirst()) {
                is Array<*> -> {
                    if (debug) println("\t\t$task :: ${task.javaClass}")
                    task.forEach {
                        if (it != null) {
                            taskList.add(it)
                        }
                    }
                }
                is TaskCollection<*> -> {
                    if (debug) println("\t\t$task :: ${task.javaClass}")
                    task.forEach {
                        taskList.add(it)
                    }
                }
                is TaskProvider<*> -> {
                    if (debug) println("\t\t$task :: ${task.javaClass}")
                    taskList.add(task.get())
                }
                is ConfigurableFileCollection -> {
                    // do nothing. it's a file collection
                    if (debug) println("\t\t$task :: ${task.javaClass}")
                }
                is String -> {
                    if (debug) println("\t\t$task :: ${task.javaClass}")
                    if (!task.startsWith(":")) {
                        taskList.add(task.startColon())
                    } else {
                        if (debug) println("\t\t${task}")

                        if (task == taskToLookForName) {
                            if (debug) println("\t\tFound task: $taskToLookForName")
                            return true
                        }

                        val tsk = allTasksNames[task]
                        if (tsk != null) {
                            taskList.add(tsk)
                        } else {
                            if (debug) println("Unable to find task for: $task")
                        }
                    }
                }
                is Task -> {
                    if (debug) println("\t\t$task :: ${task.javaClass}")
                    // THIS DOES THE ACTUAL WORK TO SEE IF WE ARE THE TASK WE ARE LOOKING FOR
                    val taskName1 = buildName(task)
                    if (debug) println("1\t\t$taskName1")

                    if (taskName1 == taskToLookForName) {
                        if (debug) println("\t\tFound task: $taskToLookForName")
                        return true
                    }

                    task.dependsOn.forEach { dep ->
                        if (dep is Array<*>) {
                            dep.forEach { d1 ->
                                if (d1 is String) {
                                    // maybe the dependency is relative.
                                    var tsk = allTasksNames[d1]
                                    if (tsk == null) {
                                        tsk = allTasksNames["${buildName(task.project)}:$d1"]
                                    }

                                    if (tsk != null) {
                                        taskList.add(tsk)
                                    } else {
                                        // no idea what to do
                                        if (debug) println("Cannot find task: $d1")
                                    }
                                } else if (d1 != null) {
                                    if (debug) println("\t\t$d1 ${d1.javaClass}")
                                    taskList.add(d1)
                                }
                            }
                        } else {
                            if (debug) println("\t\t$dep ${dep.javaClass}")
                            taskList.add(dep)
                        }
                    }
                }
                else -> {
                    if (debug) println("??:\t\t$task :: ${task?.javaClass}")
                }
            }
        }

        if (debug) println("task $taskToLookForName not found")
        return false
    }


    // required to make sure the plugins are correctly applied. ONLY applying it to the project WILL NOT work.
    // The plugin must also be applied to the root project
    private fun Project.applyId(id: String) {
        if (project.rootProject.pluginManager.findPlugin(id) == null) {
            project.rootProject.pluginManager.apply(id)
        }

        if (project.pluginManager.findPlugin(id) == null) {
            project.pluginManager.apply(id)
        }
    }


    private fun addGlobalTypes(project: Project) {
        project.addGlobalType<NodeTask>()
        project.addGlobalType<NpmTask>()
        project.addGlobalType<NpxTask>()
        project.addGlobalType<ProxySettings>()
    }

    private inline fun <reified T> Project.addGlobalType() {
        project.extensions.extraProperties[T::class.java.simpleName] = T::class.java
    }

    private fun addNpmRule(project: Project) { // note this rule also makes it possible to specify e.g. "dependsOn npm_install"
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
