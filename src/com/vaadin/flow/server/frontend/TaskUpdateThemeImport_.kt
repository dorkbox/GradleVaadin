package com.vaadin.flow.server.frontend

import com.vaadin.flow.theme.ThemeDefinition
import dorkbox.gradleVaadin.node.NodeInfo

/**
 * flow-server-2.7.1
 */
object TaskUpdateThemeImport_ {
    fun execute(nodeInfo: NodeInfo, themeDefinition: ThemeDefinition?) {
        val updater = TaskUpdateThemeImport(nodeInfo.buildDir, themeDefinition, nodeInfo.frontendDir)
        updater.execute()
    }
}
