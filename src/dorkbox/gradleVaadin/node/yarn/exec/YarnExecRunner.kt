package dorkbox.gradleVaadin.node.yarn.exec

import dorkbox.gradleVaadin.NodeExtension
import dorkbox.gradleVaadin.node.exec.ExecConfiguration
import dorkbox.gradleVaadin.node.exec.ExecRunner
import dorkbox.gradleVaadin.node.exec.NodeExecConfiguration
import dorkbox.gradleVaadin.node.npm.proxy.NpmProxy
import dorkbox.gradleVaadin.node.util.ProjectApiHelper
import dorkbox.gradleVaadin.node.util.zip
import dorkbox.gradleVaadin.node.variant.VariantComputer
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

internal abstract class YarnExecRunner {
    @get:Inject
    abstract val providers: ProviderFactory

    private val variantComputer = VariantComputer()

    fun executeYarnCommand(project: ProjectApiHelper, nodeExtension: NodeExtension, nodeExecConfiguration: NodeExecConfiguration) {
        val nodeDirProvider = variantComputer.computeNodeDir(nodeExtension)
        val yarnDirProvider = variantComputer.computeYarnDir(nodeExtension)
        val yarnBinDirProvider = variantComputer.computeYarnBinDir(yarnDirProvider)
        val yarnExecProvider = variantComputer.computeYarnExec(nodeExtension, yarnBinDirProvider)
        val additionalBinPathProvider =
                computeAdditionalBinPath(nodeExtension, nodeDirProvider, yarnBinDirProvider)
        val execConfiguration = ExecConfiguration(yarnExecProvider.get(),
                nodeExecConfiguration.command, additionalBinPathProvider.get(),
                addNpmProxyEnvironment(nodeExtension, nodeExecConfiguration), nodeExecConfiguration.workingDir,
                nodeExecConfiguration.ignoreExitValue, nodeExecConfiguration.execOverrides)
        val execRunner = ExecRunner()
        execRunner.execute(project, nodeExtension, execConfiguration)
    }

    private fun addNpmProxyEnvironment(nodeExtension: NodeExtension,
                                       nodeExecConfiguration: NodeExecConfiguration
    ): Map<String, String> {
        if (NpmProxy.shouldConfigureProxy(System.getenv(), nodeExtension.nodeProxySettings.get())) {
            val npmProxyEnvironmentVariables = NpmProxy.computeNpmProxyEnvironmentVariables()
            if (npmProxyEnvironmentVariables.isNotEmpty()) {
                return nodeExecConfiguration.environment.plus(npmProxyEnvironmentVariables)
            }
        }
        return nodeExecConfiguration.environment
    }

    private fun computeAdditionalBinPath(nodeExtension: NodeExtension,
                                         nodeDirProvider: Provider<Directory>,
                                         yarnBinDirProvider: Provider<Directory>): Provider<List<String>> {
        return nodeExtension.download.flatMap { download ->
            if (!download) {
                providers.provider { listOf<String>() }
            }
            val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
            val npmDirProvider = variantComputer.computeNpmDir(nodeExtension, nodeDirProvider)
            val npmBinDirProvider = variantComputer.computeNpmBinDir(npmDirProvider)
            zip(nodeBinDirProvider, npmBinDirProvider, yarnBinDirProvider)
                    .map { (nodeBinDir, npmBinDir, yarnBinDir) ->
                        listOf(yarnBinDir, npmBinDir, nodeBinDir).map { file -> file.asFile.absolutePath }
                    }
        }
    }
}
