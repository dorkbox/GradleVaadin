package com.vaadin.flow.server.frontend

import com.vaadin.flow.server.frontend.scanner.ClassFinder
import com.vaadin.flow.server.frontend.scanner.FrontendDependenciesScanner
import dorkbox.gradleVaadin.node.NodeInfo
import org.slf4j.Logger

/**
 * flow-server-2.8.3
 */
object TaskUpdatePackages_ {
    fun execute(classFinder: ClassFinder, frontendDependenciesScanner: FrontendDependenciesScanner, nodeInfo: NodeInfo): TaskUpdatePackages {
        val buildDir = nodeInfo.buildDir
        val enablePnpm = nodeInfo.enablePnpm

        println("\tUpdating package dependencies in $buildDir")

        val packageUpdater = object: TaskUpdatePackages(classFinder, frontendDependenciesScanner,
            buildDir,
            buildDir,
            false, enablePnpm
        ) {
            public override fun log(): Logger {
                return Util.logger
            }
        }

        packageUpdater.execute()

        return packageUpdater
    }
}
