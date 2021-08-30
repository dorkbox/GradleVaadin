package dorkbox.gradleVaadin.node.npm.exec

import dorkbox.gradleVaadin.NodeExtension
import dorkbox.gradleVaadin.node.exec.ExecConfiguration
import dorkbox.gradleVaadin.node.exec.ExecRunner
import dorkbox.gradleVaadin.node.exec.NodeExecConfiguration
import dorkbox.gradleVaadin.node.npm.proxy.NpmProxy
import dorkbox.gradleVaadin.node.npm.proxy.NpmProxy.Companion.computeNpmProxyEnvironmentVariables
import dorkbox.gradleVaadin.node.util.ProjectApiHelper
import dorkbox.gradleVaadin.node.util.zip
import dorkbox.gradleVaadin.node.variant.VariantComputer
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.io.File
import javax.inject.Inject

internal abstract class NpmExecRunner {
    @get:Inject
    abstract val providers: ProviderFactory

    private val variantComputer = VariantComputer()

    fun executeNpmCommand(project: ProjectApiHelper, extension: NodeExtension, nodeExecConfiguration: NodeExecConfiguration) {
        val npmExecConfiguration = NpmExecConfiguration("npm"
        ) { variantComputer, nodeExtension, npmBinDir -> variantComputer.computeNpmExec(nodeExtension, npmBinDir) }
        executeCommand(project, extension, addProxyEnvironmentVariables(extension, nodeExecConfiguration),
                npmExecConfiguration)
    }

    private fun addProxyEnvironmentVariables(nodeExtension: NodeExtension,
                                             nodeExecConfiguration: NodeExecConfiguration
    ): NodeExecConfiguration {
        if (NpmProxy.shouldConfigureProxy(System.getenv(), nodeExtension.nodeProxySettings.get())) {
            val npmProxyEnvironmentVariables = computeNpmProxyEnvironmentVariables()
            if (npmProxyEnvironmentVariables.isNotEmpty()) {
                val environmentVariables =
                        nodeExecConfiguration.environment.plus(npmProxyEnvironmentVariables)
                return nodeExecConfiguration.copy(environment = environmentVariables)
            }
        }
        return nodeExecConfiguration
    }

    fun executeNpxCommand(project: ProjectApiHelper, extension: NodeExtension, nodeExecConfiguration: NodeExecConfiguration) {
        val npxExecConfiguration = NpmExecConfiguration("npx") { variantComputer, nodeExtension, npmBinDir ->
            variantComputer.computeNpxExec(nodeExtension, npmBinDir)
        }
        executeCommand(project, extension, nodeExecConfiguration, npxExecConfiguration)
    }

    private fun executeCommand(project: ProjectApiHelper, extension: NodeExtension, nodeExecConfiguration: NodeExecConfiguration,
                               npmExecConfiguration: NpmExecConfiguration
    ) {
        val execConfiguration =
                computeExecConfiguration(extension, npmExecConfiguration, nodeExecConfiguration).get()
        val execRunner = ExecRunner()
        execRunner.execute(project, extension, execConfiguration)
    }

    private fun computeExecConfiguration(extension: NodeExtension, npmExecConfiguration: NpmExecConfiguration,
                                         nodeExecConfiguration: NodeExecConfiguration
    ): Provider<ExecConfiguration> {
        val additionalBinPathProvider = computeAdditionalBinPath(extension)
        val executableAndScriptProvider = computeExecutable(extension, npmExecConfiguration)
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

    private fun computeExecutable(nodeExtension: NodeExtension, npmExecConfiguration: NpmExecConfiguration):
            Provider<ExecutableAndScript> {
        val nodeDirProvider = nodeExtension.workDir
        val npmDirProvider = variantComputer.computeNpmDir(nodeExtension, nodeDirProvider)
        val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
        val npmBinDirProvider = variantComputer.computeNpmBinDir(npmDirProvider)
        val nodeExecProvider = variantComputer.computeNodeExec(nodeExtension, nodeBinDirProvider)
        val executableProvider =
                npmExecConfiguration.commandExecComputer(variantComputer, nodeExtension, npmBinDirProvider)
        val npmScriptFileProvider = variantComputer.computeNpmScriptProvider(nodeDirProvider, npmExecConfiguration.command)

        return zip(nodeExtension.download, nodeExtension.nodeProjectDir, executableProvider, nodeExecProvider, npmScriptFileProvider).map {
            val (download, nodeProjectDir, executable, nodeExec,
                    npmScriptFile) = it
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

    private data class ExecutableAndScript(
            val executable: String,
            val script: String? = null
    )

    private fun computeAdditionalBinPath(nodeExtension: NodeExtension): Provider<List<String>> {
        return nodeExtension.download.flatMap { download ->
            if (!download) {
                providers.provider { listOf<String>() }
            }
            val nodeDirProvider = nodeExtension.workDir
            val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
            val npmDirProvider = variantComputer.computeNpmDir(nodeExtension, nodeDirProvider)
            val npmBinDirProvider = variantComputer.computeNpmBinDir(npmDirProvider)
            zip(npmBinDirProvider, nodeBinDirProvider).map { (npmBinDir, nodeBinDir) ->
                listOf(npmBinDir, nodeBinDir).map { file -> file.asFile.absolutePath }
            }
        }
    }
}
