package dorkbox.gradleVaadin.node.npm.task

import dorkbox.gradleVaadin.Vaadin
import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.exec.NodeExecConfiguration
import dorkbox.gradleVaadin.node.npm.exec.NpmExecRunner
import dorkbox.gradleVaadin.node.util.ProjectApiHelper
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecSpec
import javax.inject.Inject

abstract class NpmTask : DefaultTask() {
    @get:Inject
    abstract val objects: ObjectFactory

    @get:Inject
    abstract val providers: ProviderFactory

    @get:Optional
    @get:Input
    val npmCommand = objects.listProperty(String::class.java)

    @get:Optional
    @get:Input
    val args = objects.listProperty(String::class.java)

    @get:Input
    val ignoreExitValue = objects.property(Boolean::class.java).convention(false)

    @get:Internal
    val workingDir = objects.fileProperty()

    @get:Input
    val environment = objects.mapProperty(String::class.java, String::class.java)

    @Suppress("UNCHECKED_CAST")
    @get:Internal
    val execOverrides = objects.property(Action::class.java) as Property<Action<ExecSpec>>

    @get:Internal
    val projectHelper = ProjectApiHelper.newInstance(project)

    @get:Internal
    val vaadinConfig = VaadinConfig[project]

    init {
        group = Vaadin.NPM_GROUP
        dependsOn(NpmSetupTask.NAME)
    }

    // For DSL
    @Suppress("unused")
    fun execOverrides(execOverrides: Action<ExecSpec>) {
        this.execOverrides.set(execOverrides)
    }

    @TaskAction
    fun exec() {
        val command = npmCommand.get().plus(args.get())
        val nodeExecConfiguration = NodeExecConfiguration(command, environment.get(), workingDir.asFile.orNull, ignoreExitValue.get(), execOverrides.orNull)
        val npmExecRunner = objects.newInstance(NpmExecRunner::class.java)
        npmExecRunner.executeNpmCommand(project, projectHelper, vaadinConfig, nodeExecConfiguration)
    }
}
