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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.*

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


//                project.tasks.withType(Jar::class.java) {
//            // we ALWAYS want to make sure that this task runs when jar files are created (since we consider a jar to be the final production package for this project
//            it.dependsOn(compileProdName)
//        }

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

        private fun Project.isMyTaskTheStartupTask(allTasksNames: Map<String, Task>, taskToLookFor: Task): Boolean {
            val debug = false

            val taskToLookForName = buildName(taskToLookFor)
            if (debug) println("\tChecking for: $taskToLookForName")


            this.gradle.startParameter.taskNames.forEach { startTaskNameOrig ->
                val startTaskName = startTaskNameOrig.startColon()

                if (debug) println("\tStart Parameters: $startTaskName")
                var task = allTasksNames[startTaskName]
                if (task == null) {
                    // MAYBE it's the project parent class? gradle + intellij is weird.
                    task = allTasksNames[buildName(this) + startTaskName]
                }

                if (task == null) {
                    println("\tUnable to evaluate start task: $startTaskNameOrig")
                    return false
                }

                val checkName = task.name.startColon()
                if (debug) println("checking  $checkName :: $taskToLookForName")
                if (checkName == taskToLookForName) {
                    println("\t\tFound startup task: $taskToLookForName")
                    return true
                }

                if (debug) println("checking  $checkName  :: ${taskToLookFor.name.startColon()}")
                if (checkName == taskToLookFor.name.startColon()) {
                    return true
                }
            }

            return false
        }


        // WOW... this is annoying. It must be in project.afterEvaluate, and the task dependencies MUST be BEFORE evaluate!
        // Gradle processes things in order for each stage of processing it has.
        // Task definitions will immediately execute!
        private fun Project.isMyTaskAHardDependency(allTasksNames: Map<String, Task>, taskToLookFor: Task, rootClass: String = ":classes"): Boolean {
            val debug = false

            val taskToLookForName = buildName(taskToLookFor)
            if (debug) println("\tLooking for: $taskToLookForName")

            val taskList = LinkedList<Any>()


            this.gradle.startParameter.taskNames.forEach { startTaskNameOrig ->
                val startTaskName = startTaskNameOrig.startColon()

                if (debug) println("\tStart Parameters: $startTaskName")
                var task = allTasksNames[startTaskName]
                if (task == null) {
                    // MAYBE it's the project parent class? gradle + intellij is weird.
                    task = allTasksNames[buildName(this) + startTaskName]
                }

                if (task == null) {
                    println("\tUnable to evaluate start task: $startTaskNameOrig")
                    return false
                }

                taskList.add(task)
            }

            while (taskList.isNotEmpty()) {
                when (val task = taskList.removeFirst()) {
                    is Array<*> -> {
                        task.forEach {
                            if (it != null) {
                                taskList.add(it)
                            }
                        }
                    }
                    is TaskCollection<*> -> {
                        task.forEach {
                            taskList.add(it)
                        }
                    }
                    is TaskProvider<*> -> {
                        taskList.add(task.get())
                    }
                    is ConfigurableFileCollection -> {
                        // do nothing. it's a file collection
                    }
                    is Task -> {
                        val taskName1 = buildName(task)
                        if (debug) println("1\t\t$taskName1")

                        if (taskName1 == taskToLookForName) {
                            println("\t\tFound task: $taskToLookForName")
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
                                            println("Cannot find task: $d1")
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
                    is String -> {
                        if (!task.startsWith(":")) {
                            taskList.add(task.startColon())
                        } else if (task != rootClass) {
                            if (debug) println("\t\t${task}")

                            if (task == taskToLookForName) {
                                println("\t\tFound task: $taskToLookForName")
                                return true
                            }

                            val tsk = allTasksNames[task]
                            if (tsk != null) {
                                taskList.add(tsk)
                            } else {
                                println("Unable to find task for: $task")
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
    }

    private lateinit var config: VaadinConfig

    /** Useful, so we can announce the version of vaadin we are using */
    val version = VaadinApplication.vaadinVersion

    override fun apply(project: Project) {
        // https://discuss.gradle.org/t/can-a-plugin-itself-add-buildscript-dependencies-and-then-apply-a-plugin/25039/4
        project.applyId("java")

        // Create the Plugin extension object (for users to configure publishing).
        config = VaadinConfig.create(project)

        project.repositories.apply {
            maven { it.setUrl("https://maven.vaadin.com/vaadin-addons") } // Vaadin Addons
            maven { it.setUrl("https://maven.vaadin.com/vaadin-prereleases") } // Pre-releases
            maven { it.setUrl("https://oss.sonatype.org/content/repositories/vaadin-snapshots") } // Vaadin Snapshots
        }

        project.dependencies.apply {
            // API is so the dependency project can use undertow/etc without having to explicitly define it (since we already include it)
            add("implementation", "com.vaadin:vaadin:${VaadinConfig.VAADIN_VERSION}")
            add("implementation", "com.dorkbox:VaadinUndertow:${VaadinConfig.MAVEN_VAADIN_GRADLE_VERSION}")

            add("implementation", "io.undertow:undertow-core:${VaadinConfig.UNDERTOW_VERSION}")
            add("implementation", "io.undertow:undertow-servlet:${VaadinConfig.UNDERTOW_VERSION}")
            add("implementation", "io.undertow:undertow-websockets-jsr:${VaadinConfig.UNDERTOW_VERSION}")

            // Vaadin 14.9 changed how license checking works, and doesn't include this.
            add("implementation", "com.github.oshi:oshi-core-java11:6.4.0")

            // license checker requires JNA
            val jnaVersion = "5.12.1"
            add("implementation", "net.java.dev.jna:jna-jpms:${jnaVersion}")
            add("implementation", "net.java.dev.jna:jna-platform-jpms:${jnaVersion}")
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

        project.afterEvaluate { project ->
            // NOTE! our class-scanner scans COMPILED CLASSES, so it is required to depend (at some point) on class compilation!
            val compiler = VaadinConfig[project].vaadinCompiler
            val nodeInfo = compiler.nodeInfo

            val allTasks = project.rootProject.getAllTasks(true).flatMap { it.value }
            val allTasksNames = mutableMapOf<String, Task>()

            allTasks.forEach {
//                println("${ buildName(it)}")
                allTasksNames[buildName(it)] = it
            }

            val isExplicitRun = project.isMyTaskTheStartupTask(allTasksNames, prodMode) ||
                                          project.isMyTaskTheStartupTask(allTasksNames, devMode)
            config.explicitRun.set(isExplicitRun)

            val canRun = isExplicitRun ||
                                   project.isMyTaskAHardDependency(allTasksNames, prodMode) ||
                                   project.isMyTaskAHardDependency(allTasksNames, devMode)

            if (canRun) {
                config.productionMode.set(project.isMyTaskAHardDependency(allTasksNames, prodMode))
                compiler.log()
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

                val task = TaskCopyFrontendFiles_(compiler.projectDependencies, nodeInfo)

                if (project.isMyTaskAHardDependency(allTasksNames, copyJarResources)) {
                    inputs.files(task.frontendLocations)
                }

                outputs.dirs(
                    task.flowTargetDirectory,
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
