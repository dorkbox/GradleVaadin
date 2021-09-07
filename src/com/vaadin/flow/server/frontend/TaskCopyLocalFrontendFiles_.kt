package com.vaadin.flow.server.frontend

import dorkbox.gradleVaadin.node.NodeInfo

/**
 * flow-server-2.4.6
 */
object TaskCopyLocalFrontendFiles_ {
    fun execute(nodeInfo: NodeInfo) {
        val frontendDir = nodeInfo.frontendDir

        // copy Local Resources
        if (frontendDir.isDirectory) {
            val targetDirectory = Util.createFrontendDir(nodeInfo)

            println("\tCopying local frontend resources to '$targetDirectory'")

            val start = System.nanoTime()
            frontendDir.absoluteFile.copyRecursively(targetDirectory, true)

            val ms = (System.nanoTime() - start) / 1000000
            println("\t\tCopied frontend directory $frontendDir")
            println("\t\t                     took $ms ms")
        } else {
            println("\t\tFound no local frontend resources for the project")
        }
    }
}
