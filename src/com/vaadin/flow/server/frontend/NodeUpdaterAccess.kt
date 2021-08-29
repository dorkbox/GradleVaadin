package com.vaadin.flow.server.frontend

import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.frontend.scanner.ClassFinder
import com.vaadin.flow.server.frontend.scanner.FrontendDependenciesScanner
import dorkbox.gradleVaadin.JsonPackageTools
import elemental.json.Json
import elemental.json.JsonObject
import org.slf4j.Logger
import java.io.File


/**
 *                  flow-server-2.3.5
 */
class NodeUpdaterAccess(
    finder: ClassFinder?,
    frontendDependencies: FrontendDependenciesScanner?,
    npmFolder: File?,
    generatedPath: File?,
    val logger: Logger
) : NodeUpdater(finder, frontendDependencies, npmFolder, generatedPath) {
    companion object {

        /**
         * Creates the <code>package.json</code> if missing.
         *
         * @since 2.0
         */
        fun createMissingPackageJson(npmFolder: File, generatedPath: File) {
            val task = TaskCreatePackageJson(npmFolder, generatedPath)
            task.execute()
        }

        /**
         * Updates <code>package.json</code> by visiting {@link NpmPackage} annotations
         * found in the classpath. It also visits classes annotated with
         * {@link NpmPackage}
         *
         * @since 2.0
         */
        fun enablePackagesUpdate(classFinder: ClassFinder, frontendDependenciesScanner: FrontendDependenciesScanner,
                                 npmFolder: File, enablePnpm: Boolean = false) {
            val packageUpdater = TaskUpdatePackages(classFinder, frontendDependenciesScanner,
                npmFolder,
                npmFolder,
                true, enablePnpm
            )

            packageUpdater.execute()

//            commands.add(packageUpdater)
//
//            if (builder.runNpmInstall) {
//                commands.add(
//                    TaskRunNpmInstall(
//                        classFinder, packageUpdater,
//                        builder.enablePnpm, builder.requireHomeNodeExec,
//                        builder.nodeVersion, builder.nodeDownloadRoot
//                    )
//                )
//            }
//            val task =
        }









        fun getGeneratedModules(directory: File, excludes: Set<String>): Set<String> {
            return NodeUpdater.getGeneratedModules(directory, excludes)
        }

        fun getJsonFileContent(packageFile: File): JsonObject {
            return NodeUpdater.getJsonFileContent(packageFile) ?: Json.createObject()
        }

        fun disableVaadinStatistics(packageJson: JsonObject) {
            // for node_modules\@vaadin\vaadin-usage-statistics
            //or you can disable vaadin-usage-statistics for the project by adding
            //```
            //   "vaadin": { "disableUsageStatistics": true }
            //```
            //to your project `package.json` and running `npm install` again (remove `node_modules` if needed).
            //
            //You can verify this by checking that `vaadin-usage-statistics.js` contains an empty function.
            val vaadinKey = JsonPackageTools.getOrCreateKey(packageJson, VAADIN_DEP_KEY)
            JsonPackageTools.addDependency(vaadinKey, "disableUsageStatistics", true)
        }
    }


    override fun execute() {
        TODO("Not yet implemented")
    }

    public fun getPackageJsonFile(): File {
        return File(npmFolder, Constants.PACKAGE_JSON)
    }

    public override fun getPackageJson(): JsonObject {
        return super.getPackageJson()
    }

    /**
     * Updates default dependencies and development dependencies to
     * package.json.
     *
     * @param packageJson
     *            package.json json object to update with dependencies
     * @return true if items were added or removed from the {@code packageJson}
     */
    public override fun updateDefaultDependencies(packageJson: JsonObject): Boolean {
        var added = super.updateDefaultDependencies(packageJson)


        // for node_modules\@vaadin\vaadin-usage-statistics
        //or you can disable vaadin-usage-statistics for the project by adding
        //```
        //   "vaadin": { "disableUsageStatistics": true }
        //```
        //to your project `package.json` and running `npm install` again (remove `node_modules` if needed).
        //
        //You can verify this by checking that `vaadin-usage-statistics.js` contains an empty function.
        val vaadinKey = JsonPackageTools.getOrCreateKey(packageJson, VAADIN_DEP_KEY)
        added = added || JsonPackageTools.addDependency(vaadinKey, "disableUsageStatistics", true) > 0

        return added
    }

    public override fun writePackageFile(packageJson: JsonObject?): String {
        return super.writePackageFile(packageJson)
    }

    public override fun log(): Logger {
        return logger
    }
}
