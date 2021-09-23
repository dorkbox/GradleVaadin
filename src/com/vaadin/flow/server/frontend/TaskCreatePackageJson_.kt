package com.vaadin.flow.server.frontend

import dorkbox.gradleVaadin.JsonPackageTools
import dorkbox.gradleVaadin.node.NodeInfo
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import org.slf4j.Logger

/**
 * flow-server-2.4.6
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

        // we want to also MERGE in our saved (non-generated) json file contents to the generated file
        println("\tMerging original json into generated json.")

        val origJson = Util.getJsonFileContent(nodeInfo.jsonPackageFile)
        val genJson = Util.getJsonFileContent(nodeInfo.buildDirJsonPackageFile)
        JsonPackageTools.mergeJson(origJson, genJson)
        Util.disableVaadinStatistics(genJson)


        JsonPackageTools.writeJson(nodeInfo.buildDirJsonPackageFile, genJson)


        val locationOfGeneratedJsonForFlowDependencies = nodeInfo.buildDir.resolve("frontend")

        val task = object: TaskCreatePackageJson(nodeInfo.buildDir, locationOfGeneratedJsonForFlowDependencies) {
            override fun log(): Logger {
                return Util.logger
            }
        }
        task.execute()
    }
}
