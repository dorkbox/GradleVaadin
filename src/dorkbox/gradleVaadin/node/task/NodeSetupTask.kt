package dorkbox.gradleVaadin.node.task

import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.frontend.Util
import dorkbox.gradleVaadin.JsonPackageTools
import dorkbox.gradleVaadin.Vaadin
import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.NodeInfo
import dorkbox.gradleVaadin.node.util.PlatformHelper
import dorkbox.gradleVaadin.node.util.PlatformHelper.Companion.validateToolVersion
import dorkbox.gradleVaadin.node.util.ProjectApiHelper
import dorkbox.gradleVaadin.node.variant.VariantComputer
import dorkbox.version.Version
import elemental.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
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


    // This crashes on linux! SUPER ODD...
//    @get:OutputDirectory
//    val nodeDir = vaadinConfig.nodeJsDir

    private val projectHelper = ProjectApiHelper.newInstance(project)


    private val vaadinConfig by lazy { VaadinConfig[project] }
    private val debug by lazy { vaadinConfig.debugNodeJs }

    private val nodeInfo by lazy { NodeInfo(project) }
    private val nodeArchiveFile = objects.fileProperty()

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
            var installNodeOK = validateNodeInstall(true)
            var installPnpmOK = !enablePnpm || enablePnpm && validatePnpmInstall(true)

            installNodeOK && installPnpmOK
        }
    }

    @TaskAction
    fun exec() {
        // vaadin DEV MODE looks for node in the following location: basedir + "node/node_modules/npm/bin/npm-cli.js"
        var installNodeOK = validateNodeInstall(true)
        var installPnpmOK = !enablePnpm || enablePnpm && validatePnpmInstall(true)


        // if version info is OK, no need to download + reinstall nodejs
        if (installNodeOK && installPnpmOK) {
            printLog()

            if (debug) {
                println("\tCurrent install is valid, skipping reinstall.")
            }
            return
        }


        if (!installNodeOK) {
            downloadNode()
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

    private fun downloadNode() {
        try {
            if (vaadinConfig.download.get()) {
                println("\t Downloading Node ${vaadinConfig.nodeVersion} (${VariantComputer.osName}-${VariantComputer.osArch})")
                vaadinConfig.distBaseUrl.orNull?.let { addNodeRepository(project, it) }

                val nodeArchiveDependency = VariantComputer.computeNodeArchiveDependency(vaadinConfig)
                // download the node archive.
                val archiveFileProvider = resolveNodeArchiveFile(project, nodeArchiveDependency)

                val provider = project.objects.fileProperty().apply {
                    set(archiveFileProvider)
                }.asFile

                nodeArchiveFile.set(project.layout.file(provider))
            }
        } catch (e: Exception) {
            println("Unable to configure NodeJS repository: ${vaadinConfig.nodeVersion}")
            e.printStackTrace()
        }
    }

    private fun deleteExistingNode() {
        vaadinConfig.nodeJsDir__.run {
            println("\t Deleting: $this")
            deleteRecursively()
            parentFile.mkdirs()
        }
    }

    private fun unpackNodeArchive() {
        val archiveFile = nodeArchiveFile.get().asFile
        val targetDirectory = vaadinConfig.nodeJsDir__.parentFile

        println("\t   Unpack: $archiveFile")
        println("\t   Target: $targetDirectory")

        // if we already exist (because something screwed up), delete it!
        val archiveDest = targetDirectory.resolve(archiveFile.name)
        if (archiveDest.exists()) {
            archiveDest.deleteRecursively()
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
        val extractionName = VariantComputer.computeExtractionName(vaadinConfig)

        val targetDir = vaadinConfig.nodeJsDir__
        val baseFile = targetDir.parentFile

        val extractedNode = baseFile.resolve(extractionName)

        if (debug) {
            println("\t Renaming: $extractedNode")
            println("\t       to: $targetDir")
        }

        if (!extractedNode.renameTo(targetDir)) {
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

        if (!vaadinConfig.nodeJsDir__.exists() || !nodeExec.exists()) {
            if (debug) {
                println("Doesn't exist ${vaadinConfig.nodeJsDir__}  ${nodeExec}")
            }
            return false
        }

        try {
            // gets the version of Node and NPM and compares them against the supported versions

            var detectedVersion = nodeInfo.nodeExeOutput {
                this.enableRead()
                this.addArg("--version")

                if (debug) {
                    this.defaultLogger()
                    Util.execDebug(this)
                }
            }

            detectedVersion = PlatformHelper.parseVersionString(detectedVersion)

            if (debug) {
                println("\t\tNODE Detected: $detectedVersion")
            }

            var parsedVersion = Version(detectedVersion)
            detectedNodeVersion = detectedVersion

            @Suppress("DEPRECATION")
            val SUPPORTED_NODE_VERSION = Version(Constants.SUPPORTED_NODE_MAJOR_VERSION, Constants.SUPPORTED_NODE_MINOR_VERSION)
            val nodeIsOK = validateToolVersion("node", parsedVersion, SUPPORTED_NODE_VERSION, silent)

            if (!nodeIsOK) {
                return false
            }

            detectedVersion = nodeInfo.npmExeOutput {
                this.enableRead()
                this.addArg("--version")

                if (debug) {
                    this.defaultLogger()
                    Util.execDebug(this)
                }
            }

            detectedVersion = PlatformHelper.parseVersionString(detectedVersion)
            if (debug) {
                println("\t\tNPM Detection: $detectedVersion")
            }

            if (detectedVersion.isNullOrEmpty()) {
                return false
            }

            parsedVersion = Version(detectedVersion)
            detectedNpmVersion = detectedVersion

            @Suppress("DEPRECATION")
            val SUPPORTED_NPM_VERSION = Version(Constants.SUPPORTED_NPM_MAJOR_VERSION, Constants.SUPPORTED_NPM_MINOR_VERSION)
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

        val output = nodeInfo.npmExeOutput{
            this.workingDirectory(projectDir)
                .enableRead()
                .addArg("list", "pnpm", "--depth=0")

            if (!silent) {
                this.defaultLogger()
                Util.execDebug(this)
            }
        }

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

            val result = nodeInfo.npmExe {
                this.workingDirectory(projectDir)
                    .addArg("--shamefully-hoist=true", "install", "pnpm@$pnpmVersion")
//                .addArg("--scripts-prepend-node-path")

                if (debug) {
                    this.enableRead()
                    this.defaultLogger()
                    Util.execDebug(this)
                }
            }

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


    private fun addNodeRepository(project: Project, distUrl: String) {
        project.repositories.ivy {
            it.name = "Node.js"
            it.setUrl(distUrl)
            it.patternLayout { t ->
                t.artifact("[revision]/[artifact](-[revision]-[classifier]).[ext]")
            }
            it.metadataSources { t ->
                t.artifact()
            }
            it.content { t ->
                t.includeModule("org.nodejs", "node")
            }
        }
    }

    private fun resolveNodeArchiveFile(project: Project, name: String): File {
        val dependency = project.dependencies.create(name)
        val configuration = project.configurations.detachedConfiguration(dependency)
        configuration.isTransitive = false
        return configuration.resolve().single()
    }
}
