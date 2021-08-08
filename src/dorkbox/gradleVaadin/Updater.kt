package dorkbox.gradleVaadin

import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.frontend.FrontendUtils
import elemental.json.impl.JsonUtil
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class Updater {
    companion object {
        private const val DEPENDENCIES = "dependencies"
        private const val DEV_DEPENDENCIES = "devDependencies"
        private const val DEP_NAME_KEY = "name"
        private const val DEP_NAME_DEFAULT = "no-name"
        private const val DEP_NAME_FLOW_DEPS = "@vaadin/flow-deps"
        private const val DEP_VERSION_KEY = "version"
        private const val DEP_VERSION_DEFAULT = "1.0.0"
        private const val DEP_LICENSE_KEY = "license"
        private const val DEP_LICENSE_DEFAULT = "UNLICENSED"


        private const val APP_PACKAGE_HASH = "vaadinAppPackageHash"
        private const val FORCE_INSTALL_HASH = "Main dependencies updated, force install"
        private const val VERSION = "version"
        private const val SHRINK_WRAP = "@vaadin/vaadin-shrinkwrap"

        private val regex = "\\\\".toRegex()


        fun createMissingPackageJson(jsonPackageFile: File, generatedJsonPackageFile: File, polymerVersion: String): Boolean {

            val mainContent = getOrCreateJson(jsonPackageFile)

            // where the custom package.json is relative to our build
            // NOTE: this ABSOLUTELY MUST start with a "./", otherwise NPM freaks out
            val generatedDirAsRelative = "./" + relativize(jsonPackageFile.parentFile, generatedJsonPackageFile.parentFile)

            var modified = updateMainDefaultDependencies(mainContent, polymerVersion, generatedDirAsRelative)
            if (modified) {
                if (mainContent.hasKey(APP_PACKAGE_HASH)) {
                    println("\t\t" + FORCE_INSTALL_HASH)
                    mainContent.put(APP_PACKAGE_HASH, FORCE_INSTALL_HASH)
                } else {
                    mainContent.put(APP_PACKAGE_HASH, "")
                }

                writeJson(jsonPackageFile, mainContent)
            }

            var customContent = getJson(generatedJsonPackageFile)
            if (customContent == null) {
                modified = true
                customContent = elemental.json.Json.createObject()
                updateAppDefaultDependencies(customContent)

                writeJson(generatedJsonPackageFile, customContent)
            }

            return modified
        }

        private fun updateMainDefaultDependencies(packageJson: elemental.json.JsonObject, polymerVersion: String, generatedDirAsRelative: String):
                Boolean {
            var added = 0
            added += addDependency(packageJson, null, DEP_NAME_KEY, DEP_NAME_DEFAULT)
            added += addDependency(packageJson, null, DEP_LICENSE_KEY, DEP_LICENSE_DEFAULT)
            added += addDependency(packageJson, DEPENDENCIES, "@polymer/polymer", polymerVersion)
            added += addDependency(packageJson, DEPENDENCIES, "@webcomponents/webcomponentsjs", "^2.2.10")

            // dependency for the custom package.json placed in the generated folder.
            added += addDependency(packageJson, DEPENDENCIES, DEP_NAME_FLOW_DEPS, generatedDirAsRelative)

            added += addDependency(packageJson, DEV_DEPENDENCIES, "webpack", "4.30.0")
            added += addDependency(packageJson, DEV_DEPENDENCIES, "webpack-cli", "3.3.10")
            added += addDependency(packageJson, DEV_DEPENDENCIES, "webpack-dev-server", "3.9.0")
            added += addDependency(packageJson, DEV_DEPENDENCIES, "webpack-babel-multi-target-plugin", "2.3.3")
            added += addDependency(packageJson, DEV_DEPENDENCIES, "copy-webpack-plugin", "5.1.0")
            added += addDependency(packageJson, DEV_DEPENDENCIES, "compression-webpack-plugin", "3.0.1")
            added += addDependency(packageJson, DEV_DEPENDENCIES, "webpack-merge", "4.2.2")
            added += addDependency(packageJson, DEV_DEPENDENCIES, "raw-loader", "3.0.0")

            if (added > 0) {
                println("\t\tAdded $added dependencies to main package.json")
            }
            return added > 0
        }

        private fun updateAppDefaultDependencies(packageJson: elemental.json.JsonObject) {
            addDependency(packageJson, null, DEP_NAME_KEY, DEP_NAME_FLOW_DEPS)
            addDependency(packageJson, null, DEP_VERSION_KEY, DEP_VERSION_DEFAULT)
            addDependency(packageJson, null, DEP_LICENSE_KEY, DEP_LICENSE_DEFAULT)
        }

        fun updateGeneratedPackageJsonDependencies(packageJson: elemental.json.JsonObject,
                                                   scannedDependencies: Map<String, String>,
                                                   mainPackageJson: File,
                                                   generatedPackageJson: File,
                                                   npmModulesDir: File,
                                                   packageLockFile: File): Pair<Boolean, Boolean> {
            println("\t\tChecking '$generatedPackageJson' to see if all dependencies are added....")

            var added = 0

            // Add application dependencies
            for ((key, value) in scannedDependencies) {
                added += addDependency(packageJson, DEPENDENCIES, key, value)
            }

            if (added > 0) {
                println("\t\tAdded $added dependencies")
            } else {
                println("\t\tNo extra dependencies added...")
            }

            var doCleanUp = false

            val dependencies = packageJson.getObject(DEPENDENCIES)
            if (dependencies != null) {
                // Remove obsolete dependencies
                val copyOfKeys = dependencies.keys()
                for (key in copyOfKeys) {
                    if (!scannedDependencies.containsKey(key)) {
                        dependencies.remove(key)
                    }
                }

                val flowDeps = npmModulesDir.resolve(DEP_NAME_FLOW_DEPS).resolve(Constants.PACKAGE_JSON)
                doCleanUp = !isReleaseVersion(dependencies, mainPackageJson, generatedPackageJson, flowDeps, packageLockFile)
            }

            return Pair(added > 0, doCleanUp)
        }


        fun generateWebPackGeneratedConfig(configFile: File, sourceFrontEndDir: File, webpackOutputDir: File,
                                           flowImportFile: File, relativeStaticResources: String,
                                           customClassFinder: CustomClassFinder) {

            val absoluteConfigPath = configFile.parentFile.absoluteFile
            val absoluteSourceFrontEndPath = sourceFrontEndDir.absoluteFile
            val absoluteWebpackOutputPath = webpackOutputDir.absoluteFile

            // Generated file is always re-written
            val generatedFile = configFile.parentFile.resolve(FrontendUtils.WEBPACK_GENERATED).absoluteFile
            println("\t\tUpdating generated webpack file: $generatedFile")


            val resource = customClassFinder.getResource(FrontendUtils.WEBPACK_GENERATED)
            resource?.openStream()?.copyTo(FileOutputStream(generatedFile))

            val frontEndReplacement = relativize(absoluteConfigPath, absoluteSourceFrontEndPath)
            val frontendLine = "const frontendFolder = require('path').resolve(__dirname, '$frontEndReplacement');"

            val webPackReplacement = relativize(absoluteConfigPath, absoluteWebpackOutputPath)
            val outputLine = "const mavenOutputFolderForFlowBundledFiles = require('path').resolve(__dirname, '$webPackReplacement');"

            val flowImportsReplacement = relativize(absoluteConfigPath, flowImportFile)
            val mainLine = "const fileNameOfTheFlowGeneratedMainEntryPoint = require('path').resolve(__dirname, '$flowImportsReplacement');"

            // NOTE: new stuff. Change 'src/main/webapp' -> 'webapp' (or META-INF? which is where all static resources are served...)
            val webappDirLine = "contentBase: [mavenOutputFolderForFlowBundledFiles, '$relativeStaticResources'],"


            val lines = generatedFile.readLines(Charsets.UTF_8).toMutableList()
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("const frontendFolder")) {
                    lines[i] = frontendLine
                } else if (line.startsWith("const mavenOutputFolderForFlowBundledFiles") && line != outputLine) {
                    lines[i] = outputLine
                } else if (line.startsWith("const fileNameOfTheFlowGeneratedMainEntryPoint") && line != mainLine) {
                    lines[i] = mainLine
                } else if (line.startsWith("contentBase: [mavenOutputFolderForFlowBundledFiles") && line != webappDirLine) {
                    lines[i] = webappDirLine
                }
            }

            generatedFile.writeText(lines.joinToString(separator = System.lineSeparator()), Charsets.UTF_8)
        }

        /**
         * Check and update the main package hash in all cases as we might have updated the main package with new dependencies.
         *
         * @return true if hash has changed
         */
        fun updatePackageHash(jsonPackageFile: File, generatedPackageJson: elemental.json.JsonObject): Boolean {
            var content = ""
            // If we have dependencies generate hash on ordered content.
            if (generatedPackageJson.hasKey(DEPENDENCIES)) {
                val dependencies = generatedPackageJson.getObject(DEPENDENCIES)
                content = dependencies.keys().map {
                    String.format("\"%s\": \"%s\"", it, dependencies.getString(it))
                }
                        .sortedDescending()
                        .joinToString(". \n ")
            }
            val hash = getHash(content)

            val origJson = getJson(jsonPackageFile)!!
            val modified = (!origJson.hasKey(APP_PACKAGE_HASH) || hash != origJson.getString(APP_PACKAGE_HASH))
            if (modified) {
                println("\tChanges to dependencies found! Saving new checksum")
                origJson.put(APP_PACKAGE_HASH, hash)
                writeJson(jsonPackageFile, origJson)
            }

            return modified
        }

        fun getOrCreateJson(jsonFile: File): elemental.json.JsonObject {
            return getJson(jsonFile) ?: elemental.json.Json.createObject()
        }

        fun getJson(jsonFile: File): elemental.json.JsonObject? {
            if (jsonFile.canRead()) {
                return JsonUtil.parse(jsonFile.readText(Charsets.UTF_8)) as elemental.json.JsonObject?
            }
            return null
        }

        fun writeJson(jsonFile: File, jsonObject: elemental.json.JsonObject) {
            jsonFile.ensureParentDirsCreated()
            jsonFile.writeText(JsonUtil.stringify(jsonObject, 2) + "\n", Charsets.UTF_8)
        }

        // add, if necessary a dependency. If it's missing, return 1, otherwise 0
        private fun addDependency(json: elemental.json.JsonObject, key: String?, pkg: String?, version: String): Int {
            @Suppress("NAME_SHADOWING")
            var json = json

            if (key != null) {
                if (!json.hasKey(key)) {
                    json.put(key, elemental.json.Json.createObject())
                }
                json = json.get(key)
            }

            if (!json.hasKey(pkg) || json.getString(pkg) != version) {
                json.put(pkg, version)
                println("\t\t\tAdded '$pkg':'$version'")

                return 1
            }

            return 0
        }

        /**
         * Compares vaadin-shrinkwrap dependency version from the `dependencies` object with the current vaadin-shrinkwrap version
         * (retrieved from various sources like package.json, package-lock.json).
         */
        private fun isReleaseVersion(dependencies: elemental.json.JsonObject,
                                     mainPackageJson: File,
                                     generatedPackageJson: File,
                                     flowDeps: File,
                                     packageLockFile: File): Boolean {

            var shrinkWrapVersion: String? = null
            if (dependencies.hasKey(SHRINK_WRAP)) {
                shrinkWrapVersion = dependencies.getString(SHRINK_WRAP)
            }

            return shrinkWrapVersion == getCurrentShrinkWrapVersion(mainPackageJson, generatedPackageJson, flowDeps, packageLockFile)
        }

        // get's the shrink-wrap version info, in the following order:
        // MAIN json -> GENERATED json -> FLOW-DEPENDENCY json -> MAIN package-lock
        private fun getCurrentShrinkWrapVersion(mainPackageJson: File,
                                                generatedPackageJson: File,
                                                flowDeps: File,
                                                packageLockFile: File): String? {
            var shrinkWrapVersion = getShrinkWrapVersion(mainPackageJson)
            if (shrinkWrapVersion != null) {
                return shrinkWrapVersion
            }

            shrinkWrapVersion = getShrinkWrapVersion(generatedPackageJson)
            if (shrinkWrapVersion != null) {
                return shrinkWrapVersion
            }

            shrinkWrapVersion = getShrinkWrapVersion(flowDeps)
            if (shrinkWrapVersion != null) {
                return shrinkWrapVersion
            }

            shrinkWrapVersion = getShrinkWrapVersion(packageLockFile)
            return shrinkWrapVersion
        }

        private fun getShrinkWrapVersion(packageFile: File): String? {
            val packageJson = getJson(packageFile) ?: return null
            if (!packageJson.hasKey(DEPENDENCIES)) {
                return null
            }

            val dependencies = packageJson.getObject(DEPENDENCIES)
            if (!dependencies.hasKey(SHRINK_WRAP)) {
                return null
            }

            if (dependencies.hasKey(SHRINK_WRAP)) {
                if (packageFile.nameWithoutExtension.contains("-lock")) {
                    // the package.lock.json file has this!
                    val shrinkWrap = dependencies.getObject(SHRINK_WRAP)
                    if (shrinkWrap.hasKey(VERSION)) {
                        return shrinkWrap.get<elemental.json.JsonValue>(VERSION).asString()
                    }
                } else {
                    return dependencies.getString(SHRINK_WRAP)
                }
            }

            return null
        }

        fun relativize(sourceFile: File, targetFile: File): String {
            // the regex is to make sure that the '/' is properly escaped in the file (otherwise it's interpreted incorrectly by webpack)
            return targetFile.toRelativeString(sourceFile).replace(regex, "/")
        }

        fun compareAndCopy(fileSource: File, fileTarget: File) {
            val relative = relativize(fileSource, fileTarget)

            if (getHash(fileSource) != getHash(fileTarget)) {
                val canRead = fileSource.copyTo(fileTarget, true).canRead()
                if (canRead) {
                    println("\t\tCopy SUCCESS: $fileSource -> $relative")
                } else {
                    println("\t\tCopy FAILED: $fileSource -> $relative")
                }
            }
            else {
                println("\t\tCopy SKIP: $fileSource -> $relative")
            }
        }

        private fun getHash(file: File): String {
            return if (file.canRead()) {
                getHash(file.readText(Charsets.UTF_8))
            } else {
                "cannot read: ${file.absolutePath}"
            }
        }

        private fun getHash(content: String): String {
            return if (content.isEmpty()) {
                content
            } else try {
                val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
                digest.digest(content.toByteArray()).joinToString("", transform = { "%02x".format(it) })
            } catch (e: NoSuchAlgorithmException) {
                // Unrecoverable runtime exception, it should not happen
                throw RuntimeException("Unable to find a provider for SHA-256 algorithm", e)
            }
        }
    }
}
