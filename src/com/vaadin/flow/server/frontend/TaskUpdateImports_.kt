package com.vaadin.flow.server.frontend

import com.vaadin.flow.function.SerializableFunction
import com.vaadin.flow.server.frontend.scanner.ClassFinder
import com.vaadin.flow.server.frontend.scanner.FrontendDependenciesScanner
import dorkbox.gradleVaadin.CustomClassFinder
import dorkbox.gradleVaadin.JsonPackageTools
import dorkbox.gradleVaadin.node.NodeInfo

/**
 * flow-server-2.4.6
 */
object TaskUpdateImports_ {
    fun execute(nodeInfo: NodeInfo, customClassFinder: CustomClassFinder, frontendDependencies: FrontendDependenciesScanner) {
        val generatedFilesDir = nodeInfo.generatedFilesDir
        // enableImportsUpdate
        val start = System.nanoTime()

        println("\tGenerating vaadin flow files...")
        val genFile = generatedFilesDir.resolve("generated-flow-imports.js")
        val genFallbackFile = generatedFilesDir.resolve("generated-flow-imports-fallback.js")

        println("\t\tGenerating  $genFile")
        println("\t\tGenerating  $genFallbackFile")

        val provider =
            SerializableFunction<ClassFinder, FrontendDependenciesScanner> { t ->
                FrontendDependenciesScanner.FrontendDependenciesScannerFactory().createScanner(true, t, true)
            }

        val tokenFile = nodeInfo.tokenFile
        val frontendDir = nodeInfo.frontendDir

        // we know this is not null, because we explicitly created it earlier
        val tokenJson = JsonPackageTools.getJson(tokenFile)!!



        val task = TaskUpdateImports(
            customClassFinder,     // a reusable class finder
            frontendDependencies,  // a reusable frontend dependencies scanner
            provider,              // fallback scanner provider, not {@code null}
            nodeInfo.buildDir,     // folder with the `package.json` file
            generatedFilesDir, // folder where flow generated files will be placed.
            frontendDir,  // a directory with project's frontend files
            tokenFile,    // the token (flow-build-info.json) path, may be {@code null}
            tokenJson,    // object to fill with token file data, may be {@code null}
            !nodeInfo.enablePnpm,
            emptyList()
        )

        task.execute()


        val ms = (System.nanoTime() - start) / 1000000
        println("\t\tFinished in $ms ms")
    }
}
