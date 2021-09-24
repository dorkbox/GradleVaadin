package dorkbox.gradleVaadin.node.variant

import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.util.PlatformHelper
import dorkbox.gradleVaadin.node.util.mapIf
import dorkbox.gradleVaadin.node.util.zip
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.File

object VariantComputer {
    private val platformHelper: PlatformHelper = PlatformHelper.INSTANCE

    fun computeExtractionName(vaadinConfig: VaadinConfig): String {
        val osName = platformHelper.osName
        val osArch = platformHelper.osArch
        return "node-${vaadinConfig.nodeVersion}-$osName-$osArch"
    }


    fun computeNodeBinDir(nodeDirProvider: Provider<Directory>) = computeProductBinDir(nodeDirProvider)
    fun computeNodeBinDir(nodeDir: File) = computeProductBinDir(nodeDir)

    fun computeNodeExec(vaadinConfig: VaadinConfig): File {
        val nodeDirProvider = vaadinConfig.nodeJsDir
        val nodeBinDirProvider = computeNodeBinDir(nodeDirProvider)

        val computeNodeExec = computeNodeExec(vaadinConfig, nodeBinDirProvider)
        return File(computeNodeExec.get()).absoluteFile
    }

    fun computeNodeExec(vaadinConfig: VaadinConfig, nodeBinDirProvider: Provider<Directory>): Provider<String> {
        return zip(vaadinConfig.download, nodeBinDirProvider).map {
            val (download, nodeBinDir) = it
            if (download) {
                val nodeCommand = if (platformHelper.isWindows) "node.exe" else "node"
                nodeBinDir.dir(nodeCommand).asFile.absolutePath
            } else "node"
        }
    }

    fun computeNodeExec(vaadinConfig: VaadinConfig, nodeBinDir: File): String {
        return if (vaadinConfig.download.get()) {
            val nodeCommand = if (platformHelper.isWindows) "node.exe" else "node"
            nodeBinDir.resolve(nodeCommand).absolutePath
        } else {
            "node"
        }
    }

    fun computeNpmDir(vaadinConfig: VaadinConfig, nodeDirProvider: Provider<Directory>): Provider<Directory> {
        return zip(vaadinConfig.npmVersion, vaadinConfig.npmDir, nodeDirProvider).map {
            val (npmVersion, npmWorkDir, nodeDir) = it
            if (npmVersion.isNotBlank()) {
                val directoryName = "npm-v${npmVersion}"
                npmWorkDir.dir(directoryName)
            } else nodeDir
        }
    }

    fun computeNpmBinDir(npmDirProvider: Provider<Directory>) = computeProductBinDir(npmDirProvider)

    fun computeNpmExec(vaadinConfig: VaadinConfig): File {
        val nodeDirProvider = vaadinConfig.nodeJsDir

        val npmDirProvider = computeNpmDir(vaadinConfig, nodeDirProvider)
        val npmBinDirProvider = computeNpmBinDir(npmDirProvider)

        val computeNpmExec = computeNpmExec(vaadinConfig, npmBinDirProvider)
        return File(computeNpmExec.get()).absoluteFile
    }

    fun computeNpmExec(vaadinConfig: VaadinConfig, npmBinDirProvider: Provider<Directory>): Provider<String> {
        return zip(vaadinConfig.download, vaadinConfig.npmCommand, npmBinDirProvider).map {
            val (download, npmCommand, npmBinDir) = it
            val command = if (platformHelper.isWindows) {
                npmCommand.mapIf({ it == "npm" }) { "npm.cmd" }
            } else npmCommand
            if (download) npmBinDir.dir(command).asFile.absolutePath else command
        }
    }

    fun computeNpmScriptFile(nodeDirProvider: Provider<Directory>, command: String): String {
        return computeNpmScriptProvider(nodeDirProvider, command).get()
    }

    fun computeNpmScriptProvider(nodeDirProvider: Provider<Directory>, command: String): Provider<String> {
        return nodeDirProvider.map { nodeDir ->
            if (platformHelper.isWindows) nodeDir.dir("node_modules/npm/bin/$command-cli.js").asFile.path
            else nodeDir.dir("lib/node_modules/npm/bin/$command-cli.js").asFile.path
        }
    }

    fun computePnpmScriptFile(vaadinConfig: VaadinConfig): File {
        val nodeDir = vaadinConfig.buildDir
        val paths = listOf(
            "node_modules/pnpm/bin/pnpm.js", // has priority

            // For version 4.3.3 check ".ignored" folders
            "node_modules/.ignored_pnpm/bin/pnpm.js",
            "node_modules/.ignored/pnpm/bin/pnpm.js")

        return if (platformHelper.isWindows) {
            paths.map { nodeDir.resolve(it) }.firstOrNull { it.canRead() } ?: nodeDir.resolve(paths.first())
        }
        else {
            paths.map { nodeDir.resolve("lib").resolve(it) }.firstOrNull { it.canRead() } ?: nodeDir.resolve("lib").resolve(paths.first())
        }
    }

    fun computeNpxExec(vaadinConfig: VaadinConfig, npmBinDirProvider: Provider<Directory>): Provider<String> {
        return zip(vaadinConfig.download, vaadinConfig.npxCommand, npmBinDirProvider).map {
            val (download, npxCommand, npmBinDir) = it
            val command = if (platformHelper.isWindows) {
                npxCommand.mapIf({ it == "npx" }) { "npx.cmd" }
            } else npxCommand
            if (download) npmBinDir.dir(command).asFile.absolutePath else command
        }
    }

    fun computeYarnDir(vaadinConfig: VaadinConfig): Provider<Directory> {
        return zip(vaadinConfig.yarnVersion, vaadinConfig.yarnDir).map {
            val (yarnVersion, yarnWorkDir) = it
            val dirnameSuffix = if (yarnVersion.isNotBlank()) {
                "-v${yarnVersion}"
            } else "-latest"
            val dirname = "yarn$dirnameSuffix"
            yarnWorkDir.dir(dirname)
        }
    }

    fun computeYarnBinDir(yarnDirProvider: Provider<Directory>) = computeProductBinDir(yarnDirProvider)

    fun computeYarnExec(vaadinConfig: VaadinConfig, yarnBinDirProvider: Provider<Directory>): Provider<String> {
        return zip(vaadinConfig.yarnCommand, vaadinConfig.download, yarnBinDirProvider).map {
            val (yarnCommand, download, yarnBinDir) = it
            val command = if (platformHelper.isWindows) {
                yarnCommand.mapIf({ it == "yarn" }) { "yarn.cmd" }
            } else yarnCommand
            if (download) yarnBinDir.dir(command).asFile.absolutePath else command
        }
    }

    private fun computeProductBinDir(productDirProvider: Provider<Directory>) =
            if (platformHelper.isWindows) productDirProvider else productDirProvider.map { it.dir("bin") }

    private fun computeProductBinDir(productDirProvider: File) =
            if (platformHelper.isWindows) productDirProvider else productDirProvider.resolve("bin")

    fun computeNodeArchiveDependency(vaadinConfig: VaadinConfig): String {
        val osName = platformHelper.osName
        val osArch = platformHelper.osArch
        val type = if (platformHelper.isWindows) "zip" else "tar.gz"

        return "org.nodejs:node:${vaadinConfig.nodeVersion}:$osName-$osArch@$type"
    }
}
