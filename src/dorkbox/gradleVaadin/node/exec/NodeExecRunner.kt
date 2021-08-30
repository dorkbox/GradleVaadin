package dorkbox.gradleVaadin.node.exec

import dorkbox.gradleVaadin.NodeExtension
import dorkbox.gradleVaadin.node.util.ProjectApiHelper
import dorkbox.gradleVaadin.node.util.zip
import dorkbox.gradleVaadin.node.variant.VariantComputer
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

internal class NodeExecRunner {
    fun execute(project: ProjectApiHelper, extension: NodeExtension, nodeExecConfiguration: NodeExecConfiguration) {
        val execConfiguration = buildExecConfiguration(extension, nodeExecConfiguration).get()
        val execRunner = ExecRunner()
        execRunner.execute(project, extension, execConfiguration)
    }

    private fun buildExecConfiguration(nodeExtension: NodeExtension, nodeExecConfiguration: NodeExecConfiguration):
            Provider<ExecConfiguration> {
        val variantComputer = VariantComputer()
        val nodeDirProvider = nodeExtension.workDir
        val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
        val executableProvider = variantComputer.computeNodeExec(nodeExtension, nodeBinDirProvider)
        val additionalBinPathProvider = computeAdditionalBinPath(nodeExtension, nodeBinDirProvider)
        return zip(executableProvider, additionalBinPathProvider)
                .map { (executable, additionalBinPath) ->
                    ExecConfiguration(executable, nodeExecConfiguration.command, additionalBinPath,
                            nodeExecConfiguration.environment, nodeExecConfiguration.workingDir,
                            nodeExecConfiguration.ignoreExitValue, nodeExecConfiguration.execOverrides)
                }
    }

    private fun computeAdditionalBinPath(nodeExtension: NodeExtension, nodeBinDirProvider: Provider<Directory>):
            Provider<List<String>> {
        return zip(nodeExtension.download, nodeBinDirProvider)
                .map { (download, nodeBinDir) ->
                    if (download) listOf(nodeBinDir.asFile.absolutePath) else listOf()
                }
    }
}
