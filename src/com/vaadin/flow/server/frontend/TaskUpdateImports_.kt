package com.vaadin.flow.server.frontend

import com.vaadin.flow.function.SerializableFunction
import com.vaadin.flow.server.frontend.scanner.ClassFinder
import com.vaadin.flow.server.frontend.scanner.FrontendDependenciesScanner
import dorkbox.gradleVaadin.CustomClassFinder
import dorkbox.gradleVaadin.JsonPackageTools
import dorkbox.gradleVaadin.node.NodeInfo

/**
 * flow-server-2.8.3
 */
object TaskUpdateImports_ {
    fun execute(nodeInfo: NodeInfo, customClassFinder: CustomClassFinder, dependencyScanner: FrontendDependenciesScanner, additionalFrontendModules: List<String>, productionMode: Boolean, oldLicenseChecker: Boolean) {
        // enableImportsUpdate
        val start = System.nanoTime()

        println("\tGenerating vaadin flow files...")
        println("\t\t${nodeInfo.flowImportFile}")
        println("\t\t${nodeInfo.flowFallbackImportFile}")

        val fallbackDepScanner =
            SerializableFunction<ClassFinder, FrontendDependenciesScanner> { t ->
                FrontendDependenciesScanner.FrontendDependenciesScannerFactory().createScanner(true, t, true)
            }

        val tokenFile = nodeInfo.tokenFile

        // we know this is not null, because we explicitly created it earlier
        val tokenJson = JsonPackageTools.getJson(tokenFile)!!


        val task = TaskUpdateImports(
            customClassFinder,     // a reusable class finder
            dependencyScanner,     // a reusable frontend dependencies scanner
            fallbackDepScanner,    // fallback scanner provider, not {@code null}
            nodeInfo.buildDir,     // folder with the `package.json` file
            nodeInfo.generatedFilesDir, // folder where flow generated files will be placed.
            nodeInfo.frontendDir,       // a directory with project's frontend files
            tokenFile,    // the token (flow-build-info.json) path, may be {@code null}
            tokenJson,    // object to fill with token file data, may be {@code null}
            !nodeInfo.enablePnpm,
            additionalFrontendModules, // additional frontEnd modules
            productionMode,
            oldLicenseChecker
        )

        task.execute()


        val ms = (System.nanoTime() - start) / 1000000
        println("\t\tFinished in $ms ms")
    }
}
