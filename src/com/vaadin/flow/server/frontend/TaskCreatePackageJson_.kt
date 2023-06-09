package com.vaadin.flow.server.frontend

import dorkbox.gradleVaadin.node.NodeInfo
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import org.slf4j.Logger

/**
 * flow-server-2.9.2
 */
object TaskCreatePackageJson_ {
    fun execute(nodeInfo: NodeInfo) {
        // always delete the VAADIN directory!
        nodeInfo.vaadinDir.deleteRecursively()

        // always delete the generated directory
        nodeInfo.generatedFilesDir.deleteRecursively()

        Util.ensureDirectoryExists(nodeInfo.frontendGeneratedDir)


        // make sure the flow parent dir exists
        nodeInfo.flowJsonPackageFile.ensureParentDirsCreated()

        // create json file in BUILD dir
        val task = object: TaskCreatePackageJson(nodeInfo.buildDir, nodeInfo.frontendGeneratedDir) {
            override fun log(): Logger {
                return Util.logger
            }
        }
        task.execute()
    }
}
