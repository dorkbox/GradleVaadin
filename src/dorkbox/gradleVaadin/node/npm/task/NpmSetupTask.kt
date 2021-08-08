package dorkbox.gradleVaadin.node.npm.task

import dorkbox.gradleVaadin.NodeExtension
import dorkbox.gradleVaadin.Vaadin
import dorkbox.gradleVaadin.node.exec.NodeExecConfiguration
import dorkbox.gradleVaadin.node.npm.exec.NpmExecRunner
import dorkbox.gradleVaadin.node.task.NodeSetupTask
import dorkbox.gradleVaadin.node.util.ProjectApiHelper
import dorkbox.gradleVaadin.node.variant.VariantComputer
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import java.io.File
import javax.inject.Inject

/**
 * npm install that only gets executed if gradle decides so.
 */
abstract class NpmSetupTask : DefaultTask() {

    @get:Inject
    abstract val objects: ObjectFactory

    @get:Inject
    abstract val providers: ProviderFactory

    @get:Internal
    protected val nodeExtension = NodeExtension[project]

    @get:Internal
    val projectHelper = ProjectApiHelper.newInstance(project)

    @get:Input
    val args = objects.listProperty<String>()

    @get:Input
    val download = nodeExtension.download

    @get:OutputDirectory
    val npmDir by lazy {
        val variantComputer = VariantComputer()
        val nodeDir = variantComputer.computeNodeDir(nodeExtension)
        variantComputer.computeNpmDir(nodeExtension, nodeDir)
    }

    init {
        group = Vaadin.NPM_GROUP
        description = "Setup a specific version of npm to be used by the build."
        dependsOn(NodeSetupTask.NAME)
        onlyIf {
            isTaskEnabled()
        }
    }

    @Input
    protected open fun getVersion(): Provider<String> {
        return nodeExtension.npmVersion
    }

    @Internal
    open fun isTaskEnabled(): Boolean {
        return nodeExtension.npmVersion.get().isNotBlank()
    }

    @TaskAction
    fun exec() {
        val command = computeCommand()
        val nodeExecConfiguration = NodeExecConfiguration(command)
        val npmExecRunner = objects.newInstance(NpmExecRunner::class.java)
        npmExecRunner.executeNpmCommand(projectHelper, nodeExtension, nodeExecConfiguration)
    }

    protected open fun computeCommand(): List<String> {
        val version = nodeExtension.npmVersion.get()
        val directory = npmDir.get().asFile
        // npm < 7 creates the directory if it's missing, >= 7 fails if it's missing
        File(directory, "lib").mkdirs()
        return listOf("install", "--global", "--no-save", "--prefix", directory.absolutePath,
                "npm@$version") + args.get()
    }

    companion object {
        const val NAME = "npmSetup"
    }
}
