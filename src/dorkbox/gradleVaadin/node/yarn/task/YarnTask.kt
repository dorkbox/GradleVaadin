package dorkbox.gradleVaadin.node.yarn.task

import dorkbox.gradleVaadin.Vaadin
import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.exec.NodeExecConfiguration
import dorkbox.gradleVaadin.node.util.ProjectApiHelper
import dorkbox.gradleVaadin.node.yarn.exec.YarnExecRunner
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

abstract class YarnTask : DefaultTask() {

    @get:Inject
    abstract val objects: ObjectFactory

    @get:Inject
    abstract val providers: ProviderFactory

    @get:Optional
    @get:Input
    val yarnCommand = objects.listProperty(String::class.java)

    @get:Optional
    @get:Input
    val args = objects.listProperty(String::class.java)

    @get:Input
    val ignoreExitValue = objects.property(Boolean::class.java).convention(false)

    @get:Internal
    val workingDir = objects.directoryProperty()

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
        group = Vaadin.NODE_GROUP
        dependsOn(YarnSetupTask.NAME)
    }

    // For DSL
    @Suppress("unused")
    fun execOverrides(execOverrides: Action<ExecSpec>) {
        this.execOverrides.set(execOverrides)
    }

    @TaskAction
    fun exec() {
        val command = yarnCommand.get().plus(args.get())
        val nodeExecConfiguration =
                NodeExecConfiguration(command, environment.get(), workingDir.asFile.orNull,
                        ignoreExitValue.get(), execOverrides.orNull)
        val yarnExecRunner = objects.newInstance(YarnExecRunner::class.java)
        yarnExecRunner.executeYarnCommand(projectHelper, vaadinConfig, nodeExecConfiguration)
    }
}
