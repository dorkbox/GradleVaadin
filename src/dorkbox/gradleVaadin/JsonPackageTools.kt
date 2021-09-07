package dorkbox.gradleVaadin

import com.vaadin.flow.server.Constants
import elemental.json.*
import elemental.json.impl.JsonUtil
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import java.io.File
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class JsonPackageTools {
    companion object {
        const val DEPENDENCIES = "dependencies"
        const val DEV_DEPENDENCIES = "devDependencies"
        const val DEP_NAME_KEY = "name"
        const val DEP_NAME_DEFAULT = "no-name"
        const val DEP_NAME_FLOW_DEPS = "@vaadin/flow-deps"
        const val DEP_VERSION_KEY = "version"
        const val DEP_VERSION_DEFAULT = "1.0.0"
        const val DEP_LICENSE_KEY = "license"
        const val DEP_LICENSE_DEFAULT = "UNLICENSED"


        const val APP_PACKAGE_HASH = "vaadinAppPackageHash"
        const val FORCE_INSTALL_HASH = "Main dependencies updated, force install"
        const val VERSION = "version"
        const val SHRINK_WRAP = "@vaadin/vaadin-shrinkwrap"

        val regex = "\\\\".toRegex()


//        fun fixOriginalJsonPackage(jsonPackageFile: File, polymerVersion: String, frontendDirForGenJsonFile: String) {
//
//            val jsonRoot = getOrCreateJson(jsonPackageFile)
//
//            val updatedDependencies = updateMainDefaultDependencies(jsonRoot, polymerVersion, frontendDirForGenJsonFile)
//            if (updatedDependencies > 0) {
//                println("\tAdded $updatedDependencies items to original package.json")
//
//                val hashIsModified = updatePackageHash(jsonRoot)
//                if (hashIsModified) {
//                    println("\tPrimary json package dependencies were modified!")
//
//                    if (jsonRoot.hasKey(APP_PACKAGE_HASH)) {
//                        println("\t" + FORCE_INSTALL_HASH)
//                        jsonRoot.put(APP_PACKAGE_HASH, FORCE_INSTALL_HASH)
//                    } else {
//                        jsonRoot.put(APP_PACKAGE_HASH, "")
//                    }
//                }
//
//                writeJson(jsonPackageFile, jsonRoot)
//            }
//        }

//        private fun updateMainDefaultDependencies(packageJson: JsonObject, polymerVersion: String, frontendDirForGenJsonFile: String): Int {
//            val depKey = getOrCreateKey(packageJson, DEPENDENCIES)
//            val devKey = getOrCreateKey(packageJson, DEV_DEPENDENCIES)
//
//            var added = 0
//            added += addDependency(packageJson, DEP_NAME_KEY, DEP_NAME_DEFAULT)
//            added += addDependency(packageJson, DEP_LICENSE_KEY, DEP_LICENSE_DEFAULT)
//
//            added += addDependency(depKey, "@polymer/polymer", polymerVersion)
//            added += addDependency(depKey, "@webcomponents/webcomponentsjs", "^2.2.10")
//            added += addDependency(depKey, "lit-element", "^2.2.1")
//
//            // for node_modules\@vaadin\vaadin-usage-statistics
//            //or you can disable vaadin-usage-statistics for the project by adding
//            //```
//            //   "vaadin": { "disableUsageStatistics": true }
//            //```
//            //to your project `package.json` and running `npm install` again (remove `node_modules` if needed).
//            //
//            //You can verify this by checking that `vaadin-usage-statistics.js` contains an empty function.
//            added += addDependency(packageJson, "vaadin", "disableUsageStatistics")
//
//
//            // dependency for the custom package.json placed in the generated folder.
//            added += addDependency(depKey, DEP_NAME_FLOW_DEPS, frontendDirForGenJsonFile, true)
//
//            added += addDependency(devKey, "webpack", "4.30.0")
//            added += addDependency(devKey, "webpack-cli", "3.3.10")
//            added += addDependency(devKey, "webpack-dev-server", "3.9.0")
//            added += addDependency(devKey, "webpack-babel-multi-target-plugin", "2.3.3")
//            added += addDependency(devKey, "copy-webpack-plugin", "5.1.0")
//            added += addDependency(devKey, "compression-webpack-plugin", "3.0.1")
//            added += addDependency(devKey, "webpack-merge", "4.2.2")
//            added += addDependency(devKey, "raw-loader", "3.0.0")
//
//            // defaults
//            added += addDependency(packageJson, DEP_NAME_KEY, DEP_NAME_FLOW_DEPS)
//            added += addDependency(packageJson, DEP_VERSION_KEY, DEP_VERSION_DEFAULT)
//            added += addDependency(packageJson, DEP_LICENSE_KEY, DEP_LICENSE_DEFAULT)
//
//            return added
//        }

        fun mergeJson(sourceJson: JsonObject, destJson: JsonObject) {
            sourceJson.keys().forEach { origKey ->
                val origValue = sourceJson.get(origKey) as JsonValue
                updateJsonValue(sourceJson, destJson, origKey, origValue)
            }
        }

        private fun updateJsonArray(source: JsonArray, dest: JsonArray, index: Int, value: JsonValue) {
            // overwrite the generated values.
            when (value.type) {
                JsonType.OBJECT -> {
                    val jsonObject = value as JsonObject

                    var destObject = dest.get<JsonObject>(index)
                    if (destObject !is JsonObject) {
                        destObject = Json.createObject()
                    }

                    jsonObject.keys().forEach { origKey ->
                        val origValue = jsonObject.get(origKey) as JsonValue
                        updateJsonValue(jsonObject, destObject, origKey, origValue)
                    }

//                    println("o-$index : $destObject")
                    dest.set(index, destObject)
                }
                JsonType.ARRAY  -> {
                    val origValue = source.getArray(index)
                    val destArray = Json.createArray()

                    for (i in 0..origValue.length()) {
                        val newVal = origValue.get(i) as JsonValue
                        updateJsonArray(origValue, destArray, i, newVal)
                    }

//                    println("a-$index : $destArray")
                    dest.set(index, destArray)
                }
                JsonType.STRING -> {
                    val string = source.getString(index)
//                    println("s-$index : $string")
                    dest.set(index, string)
                }
                JsonType.NUMBER -> {
                    val number = source.getNumber(index)
//                    println("n-$index : $number")
                    dest.set(index, number)
                }
                JsonType.BOOLEAN -> {
                    val boolean = source.getBoolean(index)
//                    println("b-$index : $boolean")
                    dest.set(index, boolean)
                }
                JsonType.NULL -> {
//                    println("$index : null")
                    dest.set(index, Json.createNull())
                }
                else -> println("Unable to insert key $index value ($value) into generated json array!")
            }
        }

        private fun updateJsonValue(source: JsonObject, dest: JsonObject, key: String, value: JsonValue) {
            // overwrite the generated values.
            when (value.type) {
                JsonType.OBJECT -> {
                    val jsonObject = value as JsonObject

                    var destObject = dest.get<JsonObject>(key)
                    if (destObject !is JsonObject) {
                        destObject = Json.createObject()
                    }

                    jsonObject.keys().forEach { origKey ->
                        val origValue = jsonObject.get(origKey) as JsonValue
                        updateJsonValue(jsonObject, destObject, origKey, origValue)
                    }

//                    println("o-$key : $destObject")
                    dest.put(key, destObject)
                }
                JsonType.ARRAY  -> {
                    val origValue = source.getArray(key)
                    val destArray = Json.createArray()

                    for (i in 0..origValue.length()) {
                        val newVal = origValue.get(i) as JsonValue
                        updateJsonArray(origValue, destArray, i, newVal)
                    }

//                    println("a-$key : $destArray")
                    dest.put(key, destArray)
                }
                JsonType.STRING -> {
                    val string = source.getString(key)
//                    println("s-$key : $string")
                    dest.put(key, string)
                }
                JsonType.NUMBER -> {
                    val number = source.getNumber(key)
//                    println("n-$key : $number")
                    dest.put(key, number)
                }
                JsonType.BOOLEAN -> {
                    val boolean = source.getBoolean(key)
//                    println("b-$key : $boolean")
                    dest.put(key, boolean)
                }
                JsonType.NULL -> {
//                    println("$key : null")
                    dest.put(key, Json.createNull())
                }
                else -> println("Unable to insert key $key value ($value) into generated json file!")
            }
        }


        // will also make sure that all of the contents from the original file are in the generated file.
        fun updateGeneratedPackageJsonDependencies(origJson: JsonObject,
                                                   generatedJson: JsonObject,
                                                   scannedDependencies: Map<String, String>,
                                                   origFile: File,
                                                   generatedFile: File,
                                                   npmModulesDir: File,
                                                   packageLockFile: File): Boolean {
            println("\t\tChecking '$generatedFile' to see if all dependencies are added....")

            var added = 0

//            // add all of the data from the original file to the generated file
//            origJson.keys().forEach { origKey ->
//                val origValue = origJson.get(origKey) as JsonValue
//                updateJsonValue(origJson, generatedJson, origKey, origValue)
//            }

            val dependencies = getOrCreateKey(generatedJson, DEPENDENCIES)

            // Add application dependencies
            for ((key, value) in scannedDependencies) {
                added += addDependency(dependencies, key, value)
            }

            if (added > 0) {
                println("\tAdded $added dependencies")
            } else {
                println("\tNo extra dependencies added...")
            }

            var doCleanUp = false

            if (dependencies != null) {
                // Remove obsolete/unused dependencies
                val copyOfKeys = dependencies.keys()
                for (key in copyOfKeys) {
                    if (!scannedDependencies.containsKey(key)) {
                        println("Removing : $key")
                        dependencies.remove(key)
                    }
                }

                val flowDepsJsonFile = npmModulesDir.resolve(DEP_NAME_FLOW_DEPS).resolve(Constants.PACKAGE_JSON)
                doCleanUp = !isReleaseVersion(dependencies, origFile,
                    generatedFile, flowDepsJsonFile, packageLockFile)
            }

            // always write out the file, because we might have modified it when copying over the original key/value pairs
            writeJson(generatedFile, generatedJson)

            return doCleanUp
        }


        /**
         * Check and update the main package hash in all cases as we might have updated the main package with new dependencies.
         *
         * @return true if hash has changed
         */
        fun updatePackageHash(json: JsonObject): Boolean {
            var content = ""

            // If we have dependencies generate hash on ordered content.
            if (json.hasKey(DEPENDENCIES)) {
                val dependencies = json.getObject(DEPENDENCIES)
                content = dependencies.keys().map {
                    String.format("\"%s\": \"%s\"", it, dependencies.getString(it))
                }
                        .sortedDescending()
                        .joinToString(". \n ")
            }
            val hash = getHash(content)

            if (!json.hasKey(APP_PACKAGE_HASH)) {
                    json.put(APP_PACKAGE_HASH, "")
            }

            val modified = hash != json.getString(APP_PACKAGE_HASH)
            if (modified) {
                println("\tChanges to dependencies found! Saving new checksum")
                println("\t\t$hash")
                json.put(APP_PACKAGE_HASH, hash)
            }

            return modified
        }

        fun getOrCreateJson(jsonFile: File): JsonObject {
            return getJson(jsonFile) ?: Json.createObject()
        }

        fun getJson(jsonFile: File): JsonObject? {
            if (jsonFile.canRead()) {
                return JsonUtil.parse(jsonFile.readText(Charsets.UTF_8)) as JsonObject?
            }
            return null
        }

        fun writeJson(jsonFile: File, jsonObject: JsonObject, debug: Boolean = true) {
            if (debug) {
                println("\tSaving json: $jsonFile")
            }
            jsonFile.ensureParentDirsCreated()
            jsonFile.writeText(JsonUtil.stringify(jsonObject, 2) + "\n", Charsets.UTF_8)
        }

        fun getOrCreateKey(json: JsonObject, key: String): JsonObject {
            if (!json.hasKey(key)) {
                json.put(key, Json.createObject())
            }
            return json.get(key)
        }

        // add, if necessary a dependency. If it's missing, return 1, otherwise 0
        fun addDependency(json: JsonObject, key: String, value: String, overwrite: Boolean = false): Int {
            if (!json.hasKey(key) || (overwrite && json.getString(key) != value)) {
                json.put(key, value)
                println("\t\tAdded '$key':'$value'")

                return 1
            }

            return 0
        }

        fun addDependency(json: JsonObject, key: String, value: Boolean, overwrite: Boolean = false): Int {
            if (!json.hasKey(key) || (overwrite && json.getBoolean(key) != value)) {
                json.put(key, value)
                println("\t\tAdded '$key':'$value'")

                return 1
            }

            return 0
        }

        /**
         * Compares vaadin-shrinkwrap dependency version from the `dependencies` object with the current vaadin-shrinkwrap version
         * (retrieved from various sources like package.json, package-lock.json).
         */
        private fun isReleaseVersion(dependencies: JsonObject,
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
                    println("\tCopy SUCCESS: $fileSource -> $relative")
                } else {
                    println("\tCopy FAILED: $fileSource -> $relative")
                }
            }
            else {
                println("\tCopy SKIP: $fileSource -> $relative")
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
