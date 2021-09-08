package com.vaadin.flow.server.frontend

import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.frontend.scanner.ClassFinder
import com.vaadin.flow.server.frontend.scanner.FrontendDependenciesScanner
import dorkbox.gradleVaadin.JsonPackageTools
import elemental.json.JsonObject
import org.slf4j.Logger
import java.io.File


/**
 *                  flow-server-2.4.6
 *
 *  Allows access to NodeUpdater.java and NodeTasks in the vaadin project
 *
 *  For the compile steps, we have to match what is happening (for the most part) in the NodeTasks file
 */
class NodeUpdaterAccess(
    finder: ClassFinder?,
    frontendDependencies: FrontendDependenciesScanner?,
    npmFolder: File?,
    generatedPath: File?,
    val logger: Logger
) : NodeUpdater(finder, frontendDependencies, npmFolder, generatedPath) {
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
