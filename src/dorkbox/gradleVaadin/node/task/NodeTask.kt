package dorkbox.gradleVaadin.node.task

import dorkbox.gradleVaadin.NodeExtension
import dorkbox.gradleVaadin.Vaadin
import dorkbox.gradleVaadin.node.exec.NodeExecConfiguration
import dorkbox.gradleVaadin.node.exec.NodeExecRunner
import dorkbox.gradleVaadin.node.util.ProjectApiHelper
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.*
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property
import org.gradle.process.ExecSpec
import javax.inject.Inject

/**
 * Gradle task for running a Node.js script
 */
abstract class NodeTask : DefaultTask() {
    @get:Inject
    abstract val objects: ObjectFactory

    @get:Inject
    abstract val providers: ProviderFactory

    /**
     * Node.js script to run.
     */
    @get:InputFile
    @get:PathSensitive(RELATIVE)
    val script = objects.fileProperty()

    /**
     * Arguments to be passed to Node.js
     */
    @get:Input
    val options = objects.listProperty<String>()

    /**
     * Additional arguments for the [script] being run.
     */
    @get:Input
    val args = objects.listProperty<String>()

    /**
     * If enabled prevents the task from failing if the exit code is not 0. Defaults to false.
     */
    @get:Input
    val ignoreExitValue = objects.property<Boolean>().convention(false)

    /**
     * Sets the working directory.
     */
    @get:Internal
    val workingDir = objects.directoryProperty()

    /**
     * Add additional environment variables or override environment variables inherited from the system.
     */
    @get:Input
    val environment = objects.mapProperty<String, String>()

    @get:Internal
    val execOverrides = objects.property<Action<ExecSpec>>()

    @get:Internal
    val projectHelper = ProjectApiHelper.newInstance(project)

    /**
     * Overrides for [ExecSpec]
     */
    @get:Internal
    val extension = NodeExtension[project]

    init {
        group = Vaadin.NODE_GROUP
        dependsOn(NodeSetupTask.NAME)
    }

    // For DSL
    @Suppress("unused")
    fun execOverrides(execOverrides: Action<ExecSpec>) {
        this.execOverrides.set(execOverrides)
    }

    @TaskAction
    fun exec() {
        val currentScript = script.get().asFile
        val command = options.get().plus(currentScript.absolutePath).plus(args.get())
        val nodeExecConfiguration =
                NodeExecConfiguration(command, environment.get(), workingDir.asFile.orNull,
                        ignoreExitValue.get(), execOverrides.orNull)
        val nodeExecRunner = NodeExecRunner()
        nodeExecRunner.execute(projectHelper, extension, nodeExecConfiguration)
    }
}
