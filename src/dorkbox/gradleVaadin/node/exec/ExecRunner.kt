package dorkbox.gradleVaadin.node.exec

import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.util.ProjectApiHelper
import java.io.File

internal class ExecRunner {
    fun execute(projectHelper: ProjectApiHelper, extension: VaadinConfig, execConfiguration: ExecConfiguration) {
        projectHelper.exec {
            it.executable = execConfiguration.executable
            it.args = execConfiguration.args
            it.environment = computeEnvironment(execConfiguration)
            it.isIgnoreExitValue = execConfiguration.ignoreExitValue
            it.workingDir = computeWorkingDir(extension.buildDir, execConfiguration)
            execConfiguration.execOverrides?.execute(it)
        }
    }

    private fun computeEnvironment(execConfiguration: ExecConfiguration): Map<String, String> {
        val execEnvironment = mutableMapOf<String, String>()
        execEnvironment += System.getenv()
        execEnvironment += execConfiguration.environment
        if (execConfiguration.additionalBinPaths.isNotEmpty()) {
            // Take care of Windows environments that may contain "Path" OR "PATH" - both existing
            // possibly (but not in parallel as of now)
            val pathEnvironmentVariableName = if (execEnvironment["Path"] != null) "Path" else "PATH"
            val actualPath = execEnvironment[pathEnvironmentVariableName]
            val additionalPathsSerialized = execConfiguration.additionalBinPaths.joinToString(File.pathSeparator)
            execEnvironment[pathEnvironmentVariableName] =
                    "${additionalPathsSerialized}${File.pathSeparator}${actualPath}"
        }
        return execEnvironment
    }

    private fun computeWorkingDir(nodeProjectDir: File, execConfiguration: ExecConfiguration): File? {
        val workingDir = execConfiguration.workingDir ?: nodeProjectDir
        workingDir.mkdirs()
        return workingDir
    }
}
