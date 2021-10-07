package com.vaadin.flow.server.frontend

import java.io.File

/**
 * flow-server-2.7.1
 */
object TaskInstallWebpackPlugins_ {
    fun execute(nodeModulesDir: File) {
        val install = TaskInstallWebpackPlugins(nodeModulesDir)
        install.execute()
    }
}
