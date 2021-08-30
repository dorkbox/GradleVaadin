package dorkbox.gradleVaadin.node.task

import com.dorkbox.version.Version
import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.frontend.NodeUpdaterAccess
import dorkbox.executor.Executor
import dorkbox.gradleVaadin.JsonPackageTools
import dorkbox.gradleVaadin.NodeExtension
import dorkbox.gradleVaadin.Vaadin
import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.util.PlatformHelper
import dorkbox.gradleVaadin.node.util.PlatformHelper.Companion.validateToolVersion
import dorkbox.gradleVaadin.node.util.ProjectApiHelper
import dorkbox.gradleVaadin.node.variant.VariantComputer
import elemental.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Inject

abstract class NodeSetupTask : DefaultTask() {
    companion object {
        const val NAME = "nodeSetup"
    }

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
    val nodeDir = nodeExtension.workDir

    @get:Internal
    val projectHelper = ProjectApiHelper.newInstance(project)

    private val debug = VaadinConfig[project].debug

    private val nodeExec = variantComputer.computeNodeExec(nodeExtension)
    private val npmExec = variantComputer.computeNpmExec(nodeExtension)
    private val pNpmScript = variantComputer.computePnpmScriptFile(nodeExtension)
    private val enablePnpm = VaadinConfig[project].enablePnpm

    private var detectedNodeVersion = ""
    private var detectedNpmVersion = ""
    private var detectedPNpmVersion = ""

    // if there is a package.json file ALREADY here, we have to rename it so we can install pnpm.
    private val projectDir = NodeExtension[project].nodeProjectDir.get().asFile
    private val pnpmVersion = VaadinConfig[project].pnpmVersion

    init {
        group = Vaadin.NODE_GROUP
        description = "Download and install a local node/npm version."

        onlyIf {
            nodeExtension.download.get()
        }

        outputs.upToDateWhen {
            false
        }
    }

    @TaskAction
    fun exec() {
        // vaadin DEV MODE looks for node in the following location: basedir + "node/node_modules/npm/bin/npm-cli.js"
        var installNodeOK = validateNodeInstall(true)
        var installPnpmOK = enablePnpm && validatePnpmInstall(true)


        // if version info is OK, no need to download + reinstall nodejs
        if (installNodeOK && installPnpmOK) {
            printLog()

            if (debug) {
                println("\tCurrent install is valid, skipping reinstall.")
            }
            return
        }


        if (!installNodeOK) {
            deleteExistingNode()
            unpackNodeArchive()
            renameDirectory()
            setExecutableFlag()
            installNodeOK = validateNodeInstall()

            if (!installNodeOK) {
                throw GradleException("Node installation is corrupted. Aborting.")
            }
        }


        if (enablePnpm && !installPnpmOK) {
            // have to make sure that pmpn is also installed
            installPnpm(debug)
            installPnpmOK = validatePnpmInstall(!debug)

            if (!installPnpmOK) {
                throw GradleException("pNPM installation is corrupted. Aborting.")
            }
        }

        printLog()
    }

    private fun printLog() {
        println("\tNode info: $detectedNodeVersion [$nodeExec]")
        println("\t NPM info: $detectedNpmVersion [$npmExec]")
        if (enablePnpm) {
            println("\tpNPM info: $detectedPNpmVersion [$pNpmScript]")
        }
    }

    private fun deleteExistingNode() {
        println("\t Deleting: ${nodeDir.get()}")

        projectHelper.delete {
            // only delete the nodejs dir, NOT the parent dir!
            delete(nodeDir)
        }
    }

    private fun unpackNodeArchive() {
        val archiveFile = nodeArchiveFile.get().asFile
        val nodeDirProvider = nodeExtension.workDir
        val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)

        println("\t   Unpack: ${archiveFile}")

        if (archiveFile.name.endsWith("zip")) {
            projectHelper.copy {
                from(projectHelper.zipTree(archiveFile))
                into(nodeExtension.buildDir)
            }
        } else {
            projectHelper.copy {
                from(projectHelper.tarTree(archiveFile))
                into(nodeExtension.buildDir)
            }

            // Fix broken symlink
            val nodeBinDirPath = nodeBinDirProvider.get().asFile.toPath()
            val npm = nodeBinDirPath.resolve("npm")
            val npmScriptFile = variantComputer.computeNpmScriptFile(nodeDirProvider, "npm")
            if (Files.deleteIfExists(npm)) {
                Files.createSymbolicLink(npm, nodeBinDirPath.relativize(Paths.get(npmScriptFile)))
            }

            val npx = nodeBinDirPath.resolve("npx")
            val npxScriptFile = variantComputer.computeNpmScriptFile(nodeDirProvider, "npx")
            if (Files.deleteIfExists(npx)) {
                Files.createSymbolicLink(npx, nodeBinDirPath.relativize(Paths.get(npxScriptFile)))
            }
        }
    }

    private fun renameDirectory() {
        val nodeDirProvider = nodeExtension.workDir
        val extractionName = variantComputer.computeExtractionName(nodeExtension)

        val nodeDir = nodeDirProvider.get().asFile
        val baseFile = nodeDir.parentFile

        val extractedNode = baseFile.resolve(extractionName)
        val targetNode = nodeDir

        println("\t Renaming: $extractedNode")
        println("\t       to: $targetNode")

        extractedNode.renameTo(targetNode)
    }


    private fun setExecutableFlag() {
        if (!PlatformHelper.INSTANCE.isWindows) {
            val nodeDirProvider = nodeExtension.workDir
            val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
            val nodeExecProvider = variantComputer.computeNodeExec(nodeExtension, nodeBinDirProvider)
            File(nodeExecProvider.get()).setExecutable(true)
        }
    }

    private fun validateNodeInstall(silent: Boolean = false): Boolean {
        if (!nodeDir.get().asFile.exists() || !nodeExec.exists()) {
            return false
        }

        try {
            // gets the version of Node and NPM and compares them against the supported versions
            var detectedVersion = Executor.run(nodeExec.absolutePath, "--version").let {
                PlatformHelper.parseVersionString(it)
            }
            var parsedVersion = Version.from(detectedVersion)
            detectedNodeVersion = detectedVersion

            val SUPPORTED_NODE_VERSION = Version.from(Constants.SUPPORTED_NODE_MAJOR_VERSION, Constants.SUPPORTED_NODE_MINOR_VERSION)
            val nodeIsOK = validateToolVersion("node", parsedVersion, SUPPORTED_NODE_VERSION, silent)

            if (!nodeIsOK) {
                return false
            }

            detectedVersion = Executor.run(npmExec.absolutePath, "--version").let {
                PlatformHelper.parseVersionString(it)
            }
            parsedVersion = Version.from(detectedVersion)
            detectedNpmVersion = detectedVersion


            val SUPPORTED_NPM_VERSION = Version.from(Constants.SUPPORTED_NPM_MAJOR_VERSION, Constants.SUPPORTED_NPM_MINOR_VERSION)
            val npmIsOK = validateToolVersion("npm", parsedVersion, SUPPORTED_NPM_VERSION, silent)

            if (!npmIsOK) {
                return false
            }
        } catch (e: Exception) {
            // who cares what the error is, redo it
            return false
        }

        return true
    }

    private fun validatePnpmInstall(silent: Boolean = false): Boolean {
        // if we have pNPM configured, then we have to make sure that it is ALSO installed
        if (!pNpmScript.canRead()) {
            return false
        }

        if (pnpmVersion.length < 4) {
            throw GradleException("pNPM version [$pnpmVersion] is invalid!")
        }

        val exe = Executor()
            .executable(npmExec)
            .environment("ADBLOCK", "1")
            .workingDirectory(projectDir)
            .enableRead()
            .addArg(listOf("list", "pnpm", "--depth=0"))

        if (!silent) {
            NodeUpdaterAccess.execDebug(exe)
        }

        val result = exe.startBlocking()
        val output = result.output.utf8()
        val index = output.indexOf("pnpm@")
        if (index < 2) {
            return false
        }

//        println("OUTPUT: [$output]")

        val detectedVersion = output.substring(index + 5, output.length).trim().let {
            val firstWhiteSpace = it.indexOf(' ')
            if (firstWhiteSpace > 0) {
                it.substring(0, firstWhiteSpace)
            } else {
                it
            }
        }

        if (detectedVersion != pnpmVersion) {
            println("pNPM FAILED VERSION CHECK! [$detectedVersion] :: $pnpmVersion")
            return false
        }

        detectedPNpmVersion = detectedVersion
        return true
    }


    private fun installPnpm(debug: Boolean) {
        if (debug) {
            println("\tpNPM installation requested, installing")
        }

        val modulesDir = projectDir.resolve("node_modules")
        if (!modulesDir.exists() && !modulesDir.mkdir()) {
            throw GradleException("Unable to create $modulesDir")
        }

        val jsonFile = projectDir.resolve("package.json")
        val jsonFileTemp = projectDir.resolve("package.json.temp")
        val jsonLockFile = projectDir.resolve("package-lock.json")
        val jsonLockFileTemp = projectDir.resolve("package-lock.json.temp")

        try {
            if (jsonFile.canRead()) {
                jsonFileTemp.delete()
                jsonFile.renameTo(jsonFileTemp)
            }
            if (jsonLockFile.canRead()) {
                jsonLockFileTemp.delete()
                jsonLockFile.renameTo(jsonLockFileTemp)
            }

            val pkgJson = Json.createObject()
            pkgJson.put("name", "temp")
            pkgJson.put("license", "UNLICENSED")
            pkgJson.put("repository", "npm/npm")
            pkgJson.put("description", "Temporary package for pnpm installation")
            JsonPackageTools.writeJson(jsonFile, pkgJson, false)

            val lockJson = Json.createObject()
            lockJson.put("lockfileVersion", 1.0)
            JsonPackageTools.writeJson(jsonLockFile, lockJson, false)


            // install pnpm locally using npm
            val exe = Executor()
                .executable(npmExec)
                .environment("ADBLOCK", "1")
                .workingDirectory(projectDir)
                .addArg(listOf(
                    "--shamefully-hoist=true",
                    "install", "pnpm@$pnpmVersion"))

            if (debug) {
                exe.enableRead()
                NodeUpdaterAccess.execDebug(exe)
            }

            val result = exe.startBlocking()
            if (debug) {
                result.output.linesAsUtf8().forEach {
                    println("\t$it")
                }
            }

            if (result.exitValue == 1) {
                println("\tCouldn't install 'pnpm', disabling it's usage...")
                VaadinConfig[project].enablePnpm = false
            } else {
                if (debug) {
                    println("\tpNPM successfully installed")
                }
            }
        } finally {
            jsonFile.delete()
            jsonLockFile.delete()

            if (jsonFileTemp.canRead()) {
                jsonFileTemp.renameTo(jsonFile)
            }
            if (jsonLockFileTemp.canRead()) {
                jsonLockFileTemp.renameTo(jsonLockFile)
            }
        }
    }
}
