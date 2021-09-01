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
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * For managing Vaadin gradle tasks
 */
@Suppress("UnstableApiUsage", "unused")
class Vaadin : Plugin<Project> {
    companion object {
        const val NODE_GROUP = "Node"
        const val NPM_GROUP = "npm"
        const val YARN_GROUP = "Yarn"

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

    override fun apply(project: Project) {
        this.project = project

        // https://discuss.gradle.org/t/can-a-plugin-itself-add-buildscript-dependencies-and-then-apply-a-plugin/25039/4
        apply("java")

        // Create the Plugin extension object (for users to configure publishing).
        config = VaadinConfig.create(project)

        // have to create the task
        project.tasks.create("prepare_jar_libraries", PrepJarsTask::class.java)

        project.repositories.apply {
            maven { setUrl("https://maven.vaadin.com/vaadin-addons") } // Vaadin Addons
            maven { setUrl("https://maven.vaadin.com/vaadin-prereleases") } // Pre-releases
            maven { setUrl("https://oss.sonatype.org/content/repositories/vaadin-snapshots") } // Vaadin Snapshots
        }

        project.dependencies.apply {
            add("implementation", "com.vaadin:vaadin:${VaadinConfig.VAADIN_VERSION}")
            add("implementation", "com.dorkbox:VaadinUndertow:0.1")
        }

        // NOTE: NPM will ALWAYS install packages to the "node_modules" directory that is a sibling to the packages.json directory!

        addGlobalTypes()
        addTasks()
        addNpmRule()
        addYarnRule()



        project.afterEvaluate {
            val runTasks = gradle.startParameter.taskNames
            if (runTasks.any { it == compileProdName }) {
                // every other task will do nothing (run as dev mode).
                VaadinConfig[project].productionMode.set(true)
            }

            try {
                if (config.download.get()) {
                    config.distBaseUrl.orNull?.let { addNodeRepository(it) }
                    configureNodeSetupTask(config)
                }
            } catch (e: Exception) {
                println("Unable to configure NodeJS repository: ${config.nodeVersion}")
            }

            VaadinConfig[project].vaadinCompiler.log()
        }

        val nodeSetup = project.tasks.named(NodeSetupTask.NAME)

        val prepareJsonFiles = project.tasks.create("prepareJsonFiles").apply {
            dependsOn(nodeSetup)
            group = "vaadin"
            description = "Prepare Vaadin frontend"

//                inputs.files(vaadinCompiler.jsonPackageFile)

            doLast {
                val vaadinCompiler = VaadinConfig[project].vaadinCompiler
                vaadinCompiler.prepareJsonFiles()
            }
        }

        val prepareWebpackFiles = project.tasks.create("prepareWebpackFiles").apply {
            dependsOn(prepareJsonFiles)
            group = "vaadin"
            description = "Prepare Vaadin frontend"

//                inputs.files(vaadinCompiler.jsonPackageFile)

            doLast {
                val vaadinCompiler = VaadinConfig[project].vaadinCompiler
                vaadinCompiler.prepareWebpackFiles()
            }
        }

        val createTokenFile = project.tasks.create("createTokenFile").apply {
            dependsOn(prepareWebpackFiles)
            group = "vaadin"
            description = "Create Vaadin token file"

            doLast {
                val vaadinCompiler = VaadinConfig[project].vaadinCompiler
                vaadinCompiler.createTokenFile()
            }
        }

        val generateWebComponents = project.tasks.create("generateWebComponents").apply {
            dependsOn(createTokenFile)
            group = "vaadin"
            description = "Generate Vaadin web components"

            doLast {
                val vaadinCompiler = VaadinConfig[project].vaadinCompiler
                vaadinCompiler.generateWebComponents()
            }
        }

        val generateFlowJsonPackage = project.tasks.create("generateFlowJsonPackage").apply {
            dependsOn(generateWebComponents)
            group = "vaadin"
            description = "Compile Vaadin resources for Development"

            // enable caching for the compile task
            outputs.cacheIf { true }

            inputs.files(
                "${project.projectDir}/package.json",
                "${project.projectDir}/package-lock.json",
            )

//            outputs.files(
//                "${project.buildDir}/package.json",
//            )

            doLast {
                val vaadinCompiler = VaadinConfig[project].vaadinCompiler
                vaadinCompiler.generateFlowJsonPackage()
            }
        }


        project.tasks.create(compileDevName).apply {
            dependsOn(generateFlowJsonPackage, project.tasks.named("classes"))
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
            dependsOn(generateFlowJsonPackage, project.tasks.named("classes"))

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

            doLast {
                val vaadinCompiler = VaadinConfig[project].vaadinCompiler

                try {
                    vaadinCompiler.copyToken()
                    vaadinCompiler.copyResources()
                    vaadinCompiler.generateFlow()
                    vaadinCompiler.generateWebPack()
                } catch (e: Exception) {
                    throw GradleException(e.message ?: "", e)
                } finally {
                    vaadinCompiler.finish()
                }
            }
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

    private fun addNodeRepository(distUrl: String) {
        project.repositories.ivy {
            name = "Node.js"
            setUrl(distUrl)
            patternLayout {
                artifact("[revision]/[artifact](-[revision]-[classifier]).[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }
    }

    private fun configureNodeSetupTask(vaadinConfig: VaadinConfig) {
        val variantComputer = VariantComputer()
        val nodeArchiveDependency = variantComputer.computeNodeArchiveDependency(vaadinConfig)
        val archiveFileProvider = resolveNodeArchiveFile(nodeArchiveDependency)

        project.tasks.named<NodeSetupTask>(NodeSetupTask.NAME) {
            val provider = project.objects.fileProperty().apply {
                set(archiveFileProvider)
            }.asFile

            nodeArchiveFile.set(project.layout.file(provider))
        }
    }

    private fun resolveNodeArchiveFile(name: String): File {
        val dependency = project.dependencies.create(name)
        val configuration = project.configurations.detachedConfiguration(dependency)
        configuration.isTransitive = false
        return configuration.resolve().single()
    }
}
