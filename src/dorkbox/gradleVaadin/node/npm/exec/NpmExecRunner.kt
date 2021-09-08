package dorkbox.gradleVaadin.node.npm.exec

import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.exec.ExecConfiguration
import dorkbox.gradleVaadin.node.exec.ExecRunner
import dorkbox.gradleVaadin.node.exec.NodeExecConfiguration
import dorkbox.gradleVaadin.node.npm.proxy.NpmProxy
import dorkbox.gradleVaadin.node.npm.proxy.NpmProxy.Companion.computeNpmProxyEnvironmentVariables
import dorkbox.gradleVaadin.node.util.ProjectApiHelper
import dorkbox.gradleVaadin.node.util.zip
import dorkbox.gradleVaadin.node.variant.VariantComputer
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.io.File
import javax.inject.Inject

internal abstract class NpmExecRunner {
    @get:Inject
    abstract val providers: ProviderFactory

    fun executeNpmCommand(
        project1: Project,
        project: ProjectApiHelper,
        extension: VaadinConfig,
        nodeExecConfiguration: NodeExecConfiguration
    ) {
        val npmExecConfiguration = NpmExecConfiguration("npm") { variantComputer, nodeExtension, npmBinDir ->
            variantComputer.computeNpmExec(nodeExtension, npmBinDir)
        }

        executeCommand(project1, project, extension, addProxyEnvironmentVariables(extension, nodeExecConfiguration), npmExecConfiguration)
    }

    private fun addProxyEnvironmentVariables(vaadinConfig: VaadinConfig,
                                             nodeExecConfiguration: NodeExecConfiguration
    ): NodeExecConfiguration {
        if (NpmProxy.shouldConfigureProxy(System.getenv(), vaadinConfig.nodeProxySettings.get())) {
            val npmProxyEnvironmentVariables = computeNpmProxyEnvironmentVariables()
            if (npmProxyEnvironmentVariables.isNotEmpty()) {
                val environmentVariables = nodeExecConfiguration.environment.plus(npmProxyEnvironmentVariables)
                return nodeExecConfiguration.copy(environment = environmentVariables)
            }
        }
        return nodeExecConfiguration
    }

    fun executeNpxCommand(project1: Project, project: ProjectApiHelper, extension: VaadinConfig, nodeExecConfiguration: NodeExecConfiguration) {
        val npxExecConfiguration = NpmExecConfiguration("npx") { variantComputer, nodeExtension, npmBinDir ->
            variantComputer.computeNpxExec(nodeExtension, npmBinDir)
        }
        executeCommand(project1, project, extension, nodeExecConfiguration, npxExecConfiguration)
    }

    private fun executeCommand(project1: Project, project: ProjectApiHelper, extension: VaadinConfig,
                               nodeExecConfiguration: NodeExecConfiguration,
                               npmExecConfiguration: NpmExecConfiguration
    ) {
        val execConfiguration = computeExecConfiguration(project1, extension, npmExecConfiguration, nodeExecConfiguration).get()
        val execRunner = ExecRunner()
        execRunner.execute(project, extension, execConfiguration)
    }

    private fun computeExecConfiguration(
        project: Project, extension: VaadinConfig,
        npmExecConfiguration: NpmExecConfiguration,
        nodeExecConfiguration: NodeExecConfiguration
    ): Provider<ExecConfiguration> {
        val additionalBinPathProvider = computeAdditionalBinPath(extension)
        val executableAndScriptProvider = computeExecutable(project, extension, npmExecConfiguration)
        return zip(additionalBinPathProvider, executableAndScriptProvider)
                .map { (additionalBinPath, executableAndScript) ->
                    val argsPrefix =
                            if (executableAndScript.script != null) listOf(executableAndScript.script) else listOf()
                    val args = argsPrefix.plus(nodeExecConfiguration.command)
                    ExecConfiguration(executableAndScript.executable, args, additionalBinPath,
                            nodeExecConfiguration.environment, nodeExecConfiguration.workingDir,
                            nodeExecConfiguration.ignoreExitValue, nodeExecConfiguration.execOverrides)
                }
    }

    private fun getNodeModulesDirectory(project: Project, vaadinConfig: VaadinConfig): Provider<Directory> {
        return providers.provider {
            project.objects.directoryProperty().apply { set(vaadinConfig.nodeModulesDir) }.get()
        }
    }

    private fun computeExecutable(project: Project,vaadinConfig: VaadinConfig, npmExecConfiguration: NpmExecConfiguration):
            Provider<ExecutableAndScript> {
        val nodeDirProvider = vaadinConfig.nodeJsDir
        val npmDirProvider = VariantComputer.computeNpmDir(vaadinConfig, nodeDirProvider)
        val nodeBinDirProvider = VariantComputer.computeNodeBinDir(nodeDirProvider)
        val npmBinDirProvider = VariantComputer.computeNpmBinDir(npmDirProvider)
        val nodeExecProvider = VariantComputer.computeNodeExec(vaadinConfig, nodeBinDirProvider)
        val executableProvider = npmExecConfiguration.commandExecComputer(VariantComputer, vaadinConfig, npmBinDirProvider)
        val npmScriptFileProvider = VariantComputer.computeNpmScriptProvider(nodeDirProvider, npmExecConfiguration.command)


        return zip(vaadinConfig.download, getNodeModulesDirectory(project, vaadinConfig), executableProvider, nodeExecProvider, npmScriptFileProvider).map {
            val (download, nodeProjectDir, executable, nodeExec, npmScriptFile) = it

            if (download) {
                val localCommandScript = nodeProjectDir.dir("node_modules/npm/bin")
                        .file("${npmExecConfiguration.command}-cli.js").asFile

                if (localCommandScript.exists()) {
                    return@map ExecutableAndScript(nodeExec, localCommandScript.absolutePath)
                } else if (!File(executable).exists()) {
                    return@map ExecutableAndScript(nodeExec, npmScriptFile)
                }
            }
            return@map ExecutableAndScript(executable)
        }
    }

    private data class ExecutableAndScript(val executable: String, val script: String? = null)

    private fun computeAdditionalBinPath(vaadinConfig: VaadinConfig): Provider<List<String>> {
        return vaadinConfig.download.flatMap { download ->
            if (!download) {
                providers.provider { listOf<String>() }
            }
            val nodeDirProvider = vaadinConfig.nodeJsDir
            val nodeBinDirProvider = VariantComputer.computeNodeBinDir(nodeDirProvider)
            val npmDirProvider = VariantComputer.computeNpmDir(vaadinConfig, nodeDirProvider)
            val npmBinDirProvider = VariantComputer.computeNpmBinDir(npmDirProvider)
            zip(npmBinDirProvider, nodeBinDirProvider).map { (npmBinDir, nodeBinDir) ->
                listOf(npmBinDir, nodeBinDir).map { file -> file.asFile.absolutePath }
            }
        }
    }
}
