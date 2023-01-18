package com.vaadin.flow.server.frontend

import java.io.File

/**
 * flow-server-2.8.3
 */
object TaskGenerateTsFiles_ {
    fun execute(npmFolder: File) {
        val genTs = TaskGenerateTsFiles(npmFolder)
        genTs.execute()
    }
}
