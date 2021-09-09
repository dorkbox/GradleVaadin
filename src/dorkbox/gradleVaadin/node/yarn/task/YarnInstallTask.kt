package dorkbox.gradleVaadin.node.yarn.task

import dorkbox.gradleVaadin.Vaadin
import dorkbox.gradleVaadin.node.util.zip
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import java.io.File

/**
 * yarn install that only gets executed if gradle decides so.
 */
abstract class YarnInstallTask : YarnTask() {

    @Suppress("UNCHECKED_CAST")
    @get:Internal
    val nodeModulesOutputFilter = objects.property(Action::class.java) as Property<Action<ConfigurableFileTree>>

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
        const val NAME = "yarn"
    }
}
