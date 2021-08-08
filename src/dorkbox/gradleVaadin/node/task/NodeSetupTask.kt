package dorkbox.gradleVaadin.node.task

import com.dorkbox.version.Version
import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.frontend.FrontendUtils
import com.vaadin.flow.server.frontend.FrontendVersion
import dorkbox.executor.Executor
import dorkbox.gradleVaadin.NodeExtension
import dorkbox.gradleVaadin.Vaadin
import dorkbox.gradleVaadin.node.util.PlatformHelper
import dorkbox.gradleVaadin.node.util.PlatformHelper.Companion.validateToolVersion
import dorkbox.gradleVaadin.node.util.ProjectApiHelper
import dorkbox.gradleVaadin.node.variant.VariantComputer
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.*
import org.gradle.internal.impldep.org.bouncycastle.asn1.cmc.CMCStatus.success
import java.io.File
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import javax.inject.Inject

abstract class NodeSetupTask : DefaultTask() {

    @get:Inject
    abstract val objects: ObjectFactory

    @get:Inject
    abstract val providers: ProviderFactory

    @get:Internal
    internal val variantComputer = VariantComputer()
    private val nodeExtension = NodeExtension[project]

    @get:Input
    val download = nodeExtension.download

    @get:InputFile
    val nodeArchiveFile = objects.fileProperty()

    @get:OutputDirectory
    val nodeDir by lazy {
        variantComputer.computeNodeDir(nodeExtension)
    }

    @get:Internal
    val projectHelper = ProjectApiHelper.newInstance(project)

    private val nodeExec: File
    private val npmExec: File

    init {
        group = Vaadin.NODE_GROUP
        description = "Download and install a local node/npm version."

        onlyIf {
            nodeExtension.download.get()
        }

        val nodeDirProvider = variantComputer.computeNodeDir(nodeExtension)

        val npmDirProvider = variantComputer.computeNpmDir(nodeExtension, nodeDirProvider)
        val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
        val npmBinDirProvider = variantComputer.computeNpmBinDir(npmDirProvider)

        nodeExec = File(variantComputer.computeNodeExec(nodeExtension, nodeBinDirProvider).get()).absoluteFile
        npmExec = File(variantComputer.computeNpmExec(nodeExtension, npmBinDirProvider).get()).absoluteFile

        // only run node setup IF we do not already have node installed.
        outputs.dir(nodeDirProvider)
    }

    @TaskAction
    fun exec() {
        // vaadin DEV MODE looks for node in the following location: basedir + "node/node_modules/npm/bin/npm-cli.js"
        deleteExistingNode()
        unpackNodeArchive()
        renameDirectory()
        setExecutableFlag()
        validateInstall()
    }

    private fun deleteExistingNode() {
        println("\tDeleting: ${nodeDir.get()}")

        projectHelper.delete {
            // only delete the nodejs dir, NOT the parent dir!
            delete(nodeDir)
        }
    }

    private fun unpackNodeArchive() {
        val archiveFile = nodeArchiveFile.get().asFile
        val nodeDirProvider = variantComputer.computeNodeDir(nodeExtension)
        val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)

        println("\t Unpacking: ${archiveFile}")

        if (archiveFile.name.endsWith("zip")) {
            projectHelper.copy {
                from(projectHelper.zipTree(archiveFile))
                into(nodeDirProvider.map { it.dir("../") })
            }
        } else {
            projectHelper.copy {
                from(projectHelper.tarTree(archiveFile))
                into(nodeDirProvider.map { it.dir("../") })
            }
            // Fix broken symlink
            val nodeBinDirPath = nodeBinDirProvider.get().asFile.toPath()
            val npm = nodeBinDirPath.resolve("npm")
            val npmScriptFile = variantComputer.computeNpmScriptFile(nodeDirProvider, "npm").get()
            if (Files.deleteIfExists(npm)) {
                Files.createSymbolicLink(npm, nodeBinDirPath.relativize(Paths.get(npmScriptFile)))
            }
            val npx = nodeBinDirPath.resolve("npx")
            val npxScriptFile = variantComputer.computeNpmScriptFile(nodeDirProvider, "npx").get()
            if (Files.deleteIfExists(npx)) {
                Files.createSymbolicLink(npx, nodeBinDirPath.relativize(Paths.get(npxScriptFile)))
            }
        }
    }

    private fun renameDirectory() {
        val nodeDirProvider = variantComputer.computeNodeDir(nodeExtension)
        val extractionName = variantComputer.computeExtractionName(nodeExtension)

        val nodeDir = nodeDirProvider.get().asFile
        val baseFile = nodeDir.parentFile

        val extractedNode = baseFile.resolve(extractionName)
        val targetNode = nodeDir

        println("\t  Renaming: $extractedNode")
        println("\t         to: $targetNode")

        extractedNode.renameTo(targetNode)
    }


    private fun setExecutableFlag() {
        if (!PlatformHelper.INSTANCE.isWindows) {
            val nodeDirProvider = variantComputer.computeNodeDir(nodeExtension)
            val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
            val nodeExecProvider = variantComputer.computeNodeExec(nodeExtension, nodeBinDirProvider)
            File(nodeExecProvider.get()).setExecutable(true)
        }
    }

    private fun validateInstall() {
        // gets the version of Node and NPM and compares them against the supported versions
        var detectedVersion = Executor.run(nodeExec.absolutePath, "--version").let {
            PlatformHelper.parseVersionString(it)
        }
        var parsedVersion = Version.from(detectedVersion)
        println("\tNode info: $detectedVersion @ $nodeExec")

        val SUPPORTED_NODE_VERSION = Version.from(Constants.SUPPORTED_NODE_MAJOR_VERSION, Constants.SUPPORTED_NODE_MINOR_VERSION
        )
        val SHOULD_WORK_NODE_VERSION = Version.from(Constants.SHOULD_WORK_NODE_MAJOR_VERSION, Constants.SHOULD_WORK_NODE_MINOR_VERSION
        )
        validateToolVersion("node", parsedVersion, SUPPORTED_NODE_VERSION, SHOULD_WORK_NODE_VERSION)


        detectedVersion = Executor.run(npmExec.absolutePath, "--version").let {
            PlatformHelper.parseVersionString(it)
        }
        parsedVersion = Version.from(detectedVersion)
        println("\t NPM info: $detectedVersion @ $npmExec")

        val SUPPORTED_NPM_VERSION = Version.from(Constants.SUPPORTED_NPM_MAJOR_VERSION, Constants.SUPPORTED_NPM_MINOR_VERSION
        )
        val SHOULD_WORK_NPM_VERSION = Version.from(Constants.SHOULD_WORK_NPM_MAJOR_VERSION, Constants.SHOULD_WORK_NPM_MINOR_VERSION
        )
        validateToolVersion("npm", parsedVersion, SUPPORTED_NPM_VERSION, SHOULD_WORK_NPM_VERSION)
    }

    companion object {
        const val NAME = "nodeSetup"
    }
}
