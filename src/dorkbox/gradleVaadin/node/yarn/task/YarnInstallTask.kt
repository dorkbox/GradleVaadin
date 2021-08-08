package dorkbox.gradleVaadin.node.yarn.task

import dorkbox.gradleVaadin.Vaadin
import dorkbox.gradleVaadin.node.util.zip
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.kotlin.dsl.property
import java.io.File

/**
 * yarn install that only gets executed if gradle decides so.
 */
abstract class YarnInstallTask : YarnTask() {

    @get:Internal
    val nodeModulesOutputFilter = objects.property<Action<ConfigurableFileTree>>()

    init {
        group = Vaadin.YARN_GROUP
        description = "Install node packages using Yarn."
        dependsOn(YarnSetupTask.NAME)
    }

    @PathSensitive(RELATIVE)
    @Optional
    @InputFile
    protected fun getPackageJsonFile(): Provider<File> {
        return projectFileIfExists("package.json")
    }

    @PathSensitive(RELATIVE)
    @Optional
    @InputFile
    protected fun getYarnLockFile(): Provider<File> {
        return projectFileIfExists("yarn.lock")
    }

    @Optional
    @OutputFile
    protected fun getYarnLockFileAsOutput(): Provider<File> {
        return projectFileIfExists("yarn.lock")
    }

    private fun projectFileIfExists(name: String): Provider<File> {
        return nodeExtension.nodeProjectDir.map { it.file(name).asFile }
                .flatMap { if (it.exists()) providers.provider { it } else providers.provider { null } }
    }

    @Optional
    @OutputDirectory
    @Suppress("unused")
    protected fun getNodeModulesDirectory(): Provider<Directory> {
        val filter = nodeModulesOutputFilter.orNull
        return if (filter == null) nodeExtension.nodeProjectDir.dir("node_modules")
        else providers.provider { null }
    }

    @Optional
    @OutputFiles
    @Suppress("unused")
    protected fun getNodeModulesFiles(): Provider<FileTree> {
        val nodeModulesDirectoryProvider = nodeExtension.nodeProjectDir.dir("node_modules")
        return zip(nodeModulesDirectoryProvider, nodeModulesOutputFilter)
                .flatMap { (nodeModulesDirectory, nodeModulesOutputFilter) ->
                    if (nodeModulesOutputFilter != null) {
                        val fileTree = projectHelper.fileTree(nodeModulesDirectory)
                        nodeModulesOutputFilter.execute(fileTree)
                        providers.provider { fileTree }
                    } else providers.provider { null }
                }
    }

    // For DSL
    @Suppress("unused")
    fun nodeModulesOutputFilter(nodeModulesOutputFilter: Action<ConfigurableFileTree>) {
        this.nodeModulesOutputFilter.set(nodeModulesOutputFilter)
    }

    companion object {
        const val NAME = "yarn"
    }
}
