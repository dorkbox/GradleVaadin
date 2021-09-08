package dorkbox.gradleVaadin.node.exec

import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.util.ProjectApiHelper
import dorkbox.gradleVaadin.node.util.zip
import dorkbox.gradleVaadin.node.variant.VariantComputer
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

internal class NodeExecRunner {
    fun execute(project: ProjectApiHelper, extension: VaadinConfig, nodeExecConfiguration: NodeExecConfiguration) {
        val execConfiguration = buildExecConfiguration(extension, nodeExecConfiguration).get()
        val execRunner = ExecRunner()
        execRunner.execute(project, extension, execConfiguration)
    }

    private fun buildExecConfiguration(vaadinConfig: VaadinConfig, nodeExecConfiguration: NodeExecConfiguration):
            Provider<ExecConfiguration> {
        val nodeDirProvider = vaadinConfig.nodeJsDir
        val nodeBinDirProvider = VariantComputer.computeNodeBinDir(nodeDirProvider)
        val executableProvider = VariantComputer.computeNodeExec(vaadinConfig, nodeBinDirProvider)
        val additionalBinPathProvider = computeAdditionalBinPath(vaadinConfig, nodeBinDirProvider)
        return zip(executableProvider, additionalBinPathProvider)
                .map { (executable, additionalBinPath) ->
                    ExecConfiguration(executable, nodeExecConfiguration.command, additionalBinPath,
                            nodeExecConfiguration.environment, nodeExecConfiguration.workingDir,
                            nodeExecConfiguration.ignoreExitValue, nodeExecConfiguration.execOverrides)
                }
    }

    private fun computeAdditionalBinPath(vaadinConfig: VaadinConfig, nodeBinDirProvider: Provider<Directory>):
            Provider<List<String>> {
        return zip(vaadinConfig.download, nodeBinDirProvider)
                .map { (download, nodeBinDir) ->
                    if (download) listOf(nodeBinDir.asFile.absolutePath) else listOf()
                }
    }
}
