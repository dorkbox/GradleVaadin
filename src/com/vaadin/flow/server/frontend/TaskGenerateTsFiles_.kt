package com.vaadin.flow.server.frontend

import java.io.File

/**
 * flow-server-2.7.1
 */
object TaskGenerateTsFiles_ {
    fun execute(npmFolder: File, modules: List<String>) {
        val genTs = TaskGenerateTsFiles(npmFolder, modules)
        genTs.execute()
    }
}
