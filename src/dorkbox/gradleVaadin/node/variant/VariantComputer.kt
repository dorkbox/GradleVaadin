package dorkbox.gradleVaadin.node.variant

import dorkbox.gradleVaadin.NodeExtension
import dorkbox.gradleVaadin.node.util.PlatformHelper
import dorkbox.gradleVaadin.node.util.mapIf
import dorkbox.gradleVaadin.node.util.zip
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.File

class VariantComputer @JvmOverloads constructor(private val platformHelper: PlatformHelper = PlatformHelper.INSTANCE) {
    fun computeExtractionName(nodeExtension: NodeExtension): String {
        val osName = platformHelper.osName
        val osArch = platformHelper.osArch
        return "node-v${nodeExtension.version.get()}-$osName-$osArch"
    }


    fun computeNodeBinDir(nodeDirProvider: Provider<Directory>) = computeProductBinDir(nodeDirProvider)

    fun computeNodeExec(nodeExtension: NodeExtension): File {
        val nodeDirProvider = nodeExtension.workDir
        val nodeBinDirProvider = computeNodeBinDir(nodeDirProvider)

        val computeNodeExec = computeNodeExec(nodeExtension, nodeBinDirProvider)
        return File(computeNodeExec.get()).absoluteFile
    }

    fun computeNodeExec(nodeExtension: NodeExtension, nodeBinDirProvider: Provider<Directory>): Provider<String> {
        return zip(nodeExtension.download, nodeBinDirProvider).map {
            val (download, nodeBinDir) = it
            if (download) {
                val nodeCommand = if (platformHelper.isWindows) "node.exe" else "node"
                nodeBinDir.dir(nodeCommand).asFile.absolutePath
            } else "node"
        }
    }

    fun computeNpmDir(nodeExtension: NodeExtension, nodeDirProvider: Provider<Directory>): Provider<Directory> {
        return zip(nodeExtension.npmVersion, nodeExtension.npmWorkDir, nodeDirProvider).map {
            val (npmVersion, npmWorkDir, nodeDir) = it
            if (npmVersion.isNotBlank()) {
                val directoryName = "npm-v${npmVersion}"
                npmWorkDir.dir(directoryName)
            } else nodeDir
        }
    }

    fun computeNpmBinDir(npmDirProvider: Provider<Directory>) = computeProductBinDir(npmDirProvider)

    fun computeNpmExec(nodeExtension: NodeExtension): File {
        val nodeDirProvider = nodeExtension.workDir

        val npmDirProvider = computeNpmDir(nodeExtension, nodeDirProvider)
        val npmBinDirProvider = computeNpmBinDir(npmDirProvider)

        val computeNpmExec = computeNpmExec(nodeExtension, npmBinDirProvider)
        return File(computeNpmExec.get()).absoluteFile
    }

    fun computeNpmExec(nodeExtension: NodeExtension, npmBinDirProvider: Provider<Directory>): Provider<String> {
        return zip(nodeExtension.download, nodeExtension.npmCommand, npmBinDirProvider).map {
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

    fun computePnpmScriptFile(nodeExtension: NodeExtension): File {
        val nodeDir = nodeExtension.buildDir.get().asFile
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


    fun computePNpmExec(nodeExtension: NodeExtension, npmBinDirProvider: Provider<Directory>): Provider<String> {
        //     private static final String PNMP_INSTALLED_BY_NPM_FOLDER = "node_modules/pnpm/";
        //
        //    private static final String PNMP_INSTALLED_BY_NPM = PNMP_INSTALLED_BY_NPM_FOLDER
        //            + "bin/pnpm.js";
        //     private Optional<File> getLocalPnpmScript(String dir) {
        //        File npmInstalled = new File(dir, PNMP_INSTALLED_BY_NPM);
        //        if (npmInstalled.canRead()) {
        //            return Optional.of(npmInstalled);
        //        }
        //
        //        // For version 4.3.3 check ".ignored" folders
        //        File movedPnpmScript = new File(dir,
        //                "node_modules/.ignored_pnpm/bin/pnpm.js");
        //        if (movedPnpmScript.canRead()) {
        //            return Optional.of(movedPnpmScript);
        //        }
        //
        //        movedPnpmScript = new File(dir,
        //                "node_modules/.ignored/pnpm/bin/pnpm.js");
        //        if (movedPnpmScript.canRead()) {
        //            return Optional.of(movedPnpmScript);
        //        }
        //        return Optional.empty();
        //    }


        return zip(nodeExtension.download, nodeExtension.npmCommand, npmBinDirProvider).map {
            val (download, npmCommand, npmBinDir) = it
            val command = if (platformHelper.isWindows) {
                npmCommand.mapIf({ it == "npm" }) { "npm.cmd" }
            } else npmCommand
            if (download) npmBinDir.dir(command).asFile.absolutePath else command
        }
    }

    fun computeNpxExec(nodeExtension: NodeExtension, npmBinDirProvider: Provider<Directory>): Provider<String> {
        return zip(nodeExtension.download, nodeExtension.npxCommand, npmBinDirProvider).map {
            val (download, npxCommand, npmBinDir) = it
            val command = if (platformHelper.isWindows) {
                npxCommand.mapIf({ it == "npx" }) { "npx.cmd" }
            } else npxCommand
            if (download) npmBinDir.dir(command).asFile.absolutePath else command
        }
    }

    fun computeYarnDir(nodeExtension: NodeExtension): Provider<Directory> {
        return zip(nodeExtension.yarnVersion, nodeExtension.yarnWorkDir).map {
            val (yarnVersion, yarnWorkDir) = it
            val dirnameSuffix = if (yarnVersion.isNotBlank()) {
                "-v${yarnVersion}"
            } else "-latest"
            val dirname = "yarn$dirnameSuffix"
            yarnWorkDir.dir(dirname)
        }
    }

    fun computeYarnBinDir(yarnDirProvider: Provider<Directory>) = computeProductBinDir(yarnDirProvider)

    fun computeYarnExec(nodeExtension: NodeExtension, yarnBinDirProvider: Provider<Directory>): Provider<String> {
        return zip(nodeExtension.yarnCommand, nodeExtension.download, yarnBinDirProvider).map {
            val (yarnCommand, download, yarnBinDir) = it
            val command = if (platformHelper.isWindows) {
                yarnCommand.mapIf({ it == "yarn" }) { "yarn.cmd" }
            } else yarnCommand
            if (download) yarnBinDir.dir(command).asFile.absolutePath else command
        }
    }

    private fun computeProductBinDir(productDirProvider: Provider<Directory>) =
            if (platformHelper.isWindows) productDirProvider else productDirProvider.map { it.dir("bin") }

    fun computeNodeArchiveDependency(nodeExtension: NodeExtension): Provider<String> {
        val osName = platformHelper.osName
        val osArch = platformHelper.osArch
        val type = if (platformHelper.isWindows) "zip" else "tar.gz"
        return nodeExtension.version.map { version -> "org.nodejs:node:$version:$osName-$osArch@$type" }
    }

}
