package com.vaadin.flow.server.frontend

import com.vaadin.flow.server.frontend.scanner.ClassFinder
import com.vaadin.flow.server.frontend.scanner.FrontendDependenciesScanner
import dorkbox.gradleVaadin.node.NodeInfo
import org.slf4j.Logger

/**
 * flow-server-2.9.2
 */
object TaskUpdatePackages_ {
    fun execute(classFinder: ClassFinder, frontendDependenciesScanner: FrontendDependenciesScanner, nodeInfo: NodeInfo): TaskUpdatePackages {
        val buildDir = nodeInfo.buildDir
        val enablePnpm = nodeInfo.enablePnpm

        println("\tUpdating package dependencies in $buildDir")

        val logger = Util.logger
        logger.enable = !nodeInfo.debug

        val packageUpdater = object: TaskUpdatePackages(
            /* finder = */ classFinder,
            /* frontendDependencies = */ frontendDependenciesScanner,
            /* npmFolder = */ buildDir,
            /* generatedPath = */ buildDir,
            /* forceCleanUp = */ false,
            /* enablePnpm = */ enablePnpm
        ) {
            public override fun log(): Logger {
                return logger
            }
        }

        packageUpdater.execute()
        logger.enable = nodeInfo.debug

        return packageUpdater
    }
}
