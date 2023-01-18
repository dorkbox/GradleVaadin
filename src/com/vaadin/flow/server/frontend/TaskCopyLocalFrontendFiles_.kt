package com.vaadin.flow.server.frontend

import dorkbox.gradleVaadin.node.NodeInfo
import java.nio.file.Files
import java.nio.file.Paths




/**
 * flow-server-2.8.3
 */
object TaskCopyLocalFrontendFiles_ {
    fun execute(nodeInfo: NodeInfo) {
        val frontendDir = nodeInfo.frontendDir
        val targetDirectory = nodeInfo.createFrontendDir()

        if (nodeInfo.debug) {
            println("Copy Source: $frontendDir")
            println("Copy Target: $targetDirectory")
        }

        // copy Local Resources
        if (frontendDir.isDirectory) {
            println("\tCopying local frontend resources")

            val start = System.nanoTime()
            frontendDir.absoluteFile.copyRecursively(targetDirectory, true)

            Files.walk(Paths.get(targetDirectory.path)).use { fileStream ->
                // used with try-with-resources as defined in walk API note
                fileStream.filter { file -> !Files.isWritable(file) }.forEach { filePath -> filePath.toFile().setWritable(true) }
            }

            val ms = (System.nanoTime() - start) / 1000000
            println("\t\tCopied frontend directory $frontendDir")
            println("\t\t                     took $ms ms")
        } else {
            println("\t\tFound no local frontend resources for the project")
        }
    }
}
