package dorkbox.gradleVaadin.node.task

import com.dorkbox.version.Version
import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.frontend.Util
import dorkbox.executor.Executor
import dorkbox.gradleVaadin.JsonPackageTools
import dorkbox.gradleVaadin.Vaadin
import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.NodeInfo
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
import java.io.IOException
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

    @get:InputFile
    val nodeArchiveFile = objects.fileProperty()

    // This crashes on linux! SUPER ODD...
//    @get:OutputDirectory
//    val nodeDir = vaadinConfig.nodeJsDir

    private val projectHelper = ProjectApiHelper.newInstance(project)


    private val vaadinConfig by lazy { VaadinConfig[project] }
    private val debug by lazy { vaadinConfig.debug }

    private val nodeInfo by lazy { NodeInfo(project) }

    private val nodeExec by lazy { nodeInfo.nodeBinExec}
    private val npmExec by lazy { VariantComputer.computeNpmExec(vaadinConfig) }
    private val pNpmScript by lazy { VariantComputer.computePnpmScriptFile(vaadinConfig) }
    private val enablePnpm by lazy { vaadinConfig.enablePnpm }

    private var detectedNodeVersion = ""
    private var detectedNpmVersion = ""
    private var detectedPNpmVersion = ""

    // if there is a package.json file ALREADY here, we have to rename it so we can install pnpm.
    private val projectDir  by lazy { vaadinConfig.buildDir }
    private val pnpmVersion  by lazy { vaadinConfig.pnpmVersion }

    init {
        group = Vaadin.NODE_GROUP
        description = "Download and install a local node/npm version."

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
            fixSymbolicLinks()
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
        println("\t Deleting: ${vaadinConfig.nodeJsDir.get()}")

        projectHelper.delete {
            // only delete the nodejs dir, NOT the parent dir!
            it.delete(vaadinConfig.nodeJsDir)
        }
    }

    private fun unpackNodeArchive() {
        val archiveFile = nodeArchiveFile.get().asFile
        val targetDirectory = vaadinConfig.buildDir

        println("\t   Unpack: $archiveFile")
        println("\t   Target: $targetDirectory")

        // if we already exist (because somethign screwed up), delete it!
        if (targetDirectory.exists()) {
            targetDirectory.deleteRecursively()
        }

        if (archiveFile.name.endsWith("zip")) {
            projectHelper.copy {
                it.from(projectHelper.zipTree(archiveFile))
                it.into(vaadinConfig.buildDir)
            }
        } else {
            projectHelper.copy {
                it.from(projectHelper.tarTree(archiveFile))
                it.into(vaadinConfig.buildDir)
            }
        }
    }

    private fun renameDirectory() {
        val nodeDirProvider = vaadinConfig.nodeJsDir
        val extractionName = VariantComputer.computeExtractionName(vaadinConfig)

        val nodeDir = nodeDirProvider.get().asFile
        val baseFile = nodeDir.parentFile

        val extractedNode = baseFile.resolve(extractionName)
        val targetNode = nodeDir

        if (debug) {
            println("\t Renaming: $extractedNode")
            println("\t       to: $targetNode")
        }

        if (!extractedNode.renameTo(targetNode)) {
            throw IOException("Unable to rename directory! Aborting.")
        }
    }

    private fun fixSymbolicLinks() {
        if (!PlatformHelper.INSTANCE.isWindows) {
            // gradle does not support symbolic links!
            // https://github.com/gradle/gradle/issues/10676
            val nodeDirProvider = vaadinConfig.nodeJsDir
            val nodeBinDirProvider = VariantComputer.computeNodeBinDir(nodeDirProvider)

            val nodeBinDirPath = nodeBinDirProvider.get().asFile.toPath()
            val npm = nodeBinDirPath.resolve("npm")
            val npmScriptFile = VariantComputer.computeNpmScriptFile(nodeDirProvider, "npm")

            Files.deleteIfExists(npm)
            val npx = nodeBinDirPath.resolve("npx")
            val npxScriptFile = VariantComputer.computeNpmScriptFile(nodeDirProvider, "npx")
            Files.deleteIfExists(npx)

            try {
                Files.createSymbolicLink(npm, Paths.get(npmScriptFile))
                Files.createSymbolicLink(npx, Paths.get(npxScriptFile))
            } catch (e: Exception) {
                // SOMETIMES.... symbolic links aren't supported -- for example a FUSE mounted file system.
                // so we copy it instead. Not as "good", but good enough
                File(npmScriptFile).copyTo(npm.toFile(), overwrite = true)
                File(npxScriptFile).copyTo(npx.toFile(), overwrite = true)
            }

            // FrontendUtils.getNpmExecutable is a little funny. It
            //      checks for  "node/node_modules/npm/bin/npm-cli.js", which is the WINDOWS variant.
            //      Linux is "node/lib/node_modules/npm/bin/npm-cli.js"
            val nodeJsName = vaadinConfig.nodeJsDir.get().asFile.name

            val fakedNpm = npmScriptFile.replace("$nodeJsName/lib/node_modules/", "$nodeJsName/node_modules/")
            val fakedNpx = npxScriptFile.replace("$nodeJsName/lib/node_modules/", "$nodeJsName/node_modules/")
            if (debug) {
                println("\t\tCreating FIXED npm")
                println("\t\t\t $fakedNpm to $npmScriptFile")
                println("\t\t\t   to")
                println("\t\t\t $npmScriptFile")
            }

            try {
                Files.createSymbolicLink(Paths.get(fakedNpm), Paths.get(npmScriptFile))
                Files.createSymbolicLink(Paths.get(fakedNpx), Paths.get(npxScriptFile))
            } catch (e: Exception) {
                // SOMETIMES.... symbolic links aren't supported -- for example a FUSE mounted file system.
                // so we copy it instead. Not as "good", but good enough
                File(npmScriptFile).copyTo(File(fakedNpm), overwrite = true)
                File(npxScriptFile).copyTo(File(fakedNpx), overwrite = true)
            }
        }
    }


    private fun setExecutableFlag() {
        if (!PlatformHelper.INSTANCE.isWindows) {
            val nodeDirProvider = vaadinConfig.nodeJsDir
            val nodeBinDirProvider = VariantComputer.computeNodeBinDir(nodeDirProvider)
            val nodeExecProvider = VariantComputer.computeNodeExec(vaadinConfig, nodeBinDirProvider)
            File(nodeExecProvider.get()).setExecutable(true)
        }
    }

    private fun validateNodeInstall(silent: Boolean = false): Boolean {
        val nodeExec = File(nodeExec)

        if (!vaadinConfig.nodeJsDir.get().asFile.exists() || !nodeExec.exists()) {
            if (debug) {
                println("Doesn't exist ${vaadinConfig.nodeJsDir.get().asFile}  ${nodeExec}")
            }
            return false
        }

        try {
            // gets the version of Node and NPM and compares them against the supported versions


            var exe = nodeInfo.nodeExe()
                .enableRead()
                .addArg("--version")

            if (debug) {
                Util.execDebug(exe)
            }

            var detectedVersion = exe.startBlocking().output.utf8()

            if (debug) {
                println("\t\tNODE Detection: $detectedVersion")
            }

            detectedVersion = PlatformHelper.parseVersionString(detectedVersion)
            var parsedVersion = Version.from(detectedVersion)
            detectedNodeVersion = detectedVersion

            @Suppress("DEPRECATION")
            val SUPPORTED_NODE_VERSION = Version.from(Constants.SUPPORTED_NODE_MAJOR_VERSION, Constants.SUPPORTED_NODE_MINOR_VERSION)
            val nodeIsOK = validateToolVersion("node", parsedVersion, SUPPORTED_NODE_VERSION, silent)

            if (!nodeIsOK) {
                return false
            }

            exe = nodeInfo.npmExe()
                .enableRead()
                .addArg("--version")

            if (debug) {
                Util.execDebug(exe)
            }

            detectedVersion = exe.startBlocking().output.utf8()

            if (debug) {
                println("\t\tNPM Detection: $detectedVersion")
            }

            detectedVersion = PlatformHelper.parseVersionString(detectedVersion)
            parsedVersion = Version.from(detectedVersion)
            detectedNpmVersion = detectedVersion

            @Suppress("DEPRECATION")
            val SUPPORTED_NPM_VERSION = Version.from(Constants.SUPPORTED_NPM_MAJOR_VERSION, Constants.SUPPORTED_NPM_MINOR_VERSION)
            val npmIsOK = validateToolVersion("npm", parsedVersion, SUPPORTED_NPM_VERSION, silent)

            if (!npmIsOK) {
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

        println("PNP needs to be configured correctly!")

        val exe = nodeInfo.npmExe()
            .workingDirectory(projectDir)
            .enableRead()
            .addArg("list", "pnpm", "--depth=0")

        if (!silent) {
            Util.execDebug(exe)
        }

        val output = exe.startBlocking().output.utf8()
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

            val exe = nodeInfo.npmExe()
                .workingDirectory(projectDir)
                .addArg("--shamefully-hoist=true", "install", "pnpm@$pnpmVersion")
//                .addArg("--scripts-prepend-node-path")

            if (debug) {
                exe.enableRead()
                Util.execDebug(exe)
            }

            val result = exe.startBlocking()
            if (debug) {
                result.output.linesAsUtf8().forEach {
                    println("\t$it")
                }
            }

            if (result.exitValue == 1) {
                println("\tCouldn't install 'pnpm', disabling it's usage...")
                vaadinConfig.enablePnpm = false
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
