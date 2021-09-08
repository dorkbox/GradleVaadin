package dorkbox.gradleVaadin.node.npm.task

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
 * npm install that only gets executed if gradle decides so.
 */
abstract class NpmInstallTask : NpmTask() {
    @get:Internal
    val nodeModulesOutputFilter = objects.property<Action<ConfigurableFileTree>>()

    init {
        group = Vaadin.NPM_GROUP
        description = "Install node packages from package.json."
        dependsOn(NpmSetupTask.NAME)
        npmCommand.set(vaadinConfig.npmInstallCommand.map { listOf(it) })
    }

    @PathSensitive(RELATIVE)
    @InputFile
    protected fun getPackageJsonFile(): Provider<File> {
        return projectFileIfExists("package.json")
    }

    @PathSensitive(RELATIVE)
    @Optional
    @InputFile
    protected fun getNpmShrinkwrap(): Provider<File> {
        return projectFileIfExists("npm-shrinkwrap.json")
    }

    @PathSensitive(RELATIVE)
    @Optional
    @InputFile
    protected fun getPackageLockFileAsInput(): Provider<File> {
        return npmCommand.flatMap { command ->
            if (command[0] == "ci") projectFileIfExists("package-lock.json") else providers.provider { null }
        }
    }

    @PathSensitive(RELATIVE)
    @Optional
    @InputFile
    protected fun getYarnLockFile(): Provider<File> {
        return projectFileIfExists("yarn.lock")
    }

    @Optional
    @OutputFile
    protected fun getPackageLockFileAsOutput(): Provider<File> {
        return npmCommand.flatMap { command ->
            if (command[0] == "install") projectFileIfExists("package-lock.json") else providers.provider { null }
        }
    }

    private fun projectFileIfExists(name: String): Provider<File> {
        return vaadinConfig.buildDir.resolve(name).let {
            if (it.exists()) providers.provider { it }
            else providers.provider { null }
        }
    }

    @Optional
    @OutputDirectory
    @Suppress("unused")
    protected fun getNodeModulesDirectory(): Provider<Directory> {
        return providers.provider {
            project.objects.directoryProperty().apply { set(vaadinConfig.nodeModulesDir) }.get()
        }
    }

    @Optional
    @OutputFiles
    @Suppress("unused")
    protected fun getNodeModulesFiles(): Provider<FileTree> {
        return zip(getNodeModulesDirectory(), nodeModulesOutputFilter)
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
        const val NAME = "npmInstall"
    }
}
