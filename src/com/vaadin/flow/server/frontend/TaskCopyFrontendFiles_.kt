package com.vaadin.flow.server.frontend

import com.vaadin.flow.server.Constants
import dorkbox.gradleVaadin.node.NodeInfo
import java.io.File

/**
 * flow-server-2.4.6
 */
object TaskCopyFrontendFiles_ {
    fun execute(projectDependencies: List<File>, nodeInfo: NodeInfo) {
        println("\tCopying jar/embedded resources...")

        val start = System.nanoTime()

        /////////////////////
        val classPathScanResult = io.github.classgraph.ClassGraph()
            .overrideClasspath(projectDependencies)
            .enableSystemJarsAndModules()
            .enableInterClassDependencies()
            .enableExternalClasses()
            .enableAllInfo()
            .scan()

        val frontendLocations = mutableSetOf<File>()

        // all jar files having files in the 'META-INF/frontend' or 'META-INF/resources/frontend' folder.
        //  We don't use URLClassLoader because will fail in Java 9+
        classPathScanResult.allResources.forEach {
            val interiorPath = it.path
            val jarContainer = it.classpathElementFile

            if (interiorPath.startsWith(Constants.RESOURCES_FRONTEND_DEFAULT) || interiorPath.startsWith(Constants.COMPATIBILITY_RESOURCES_FRONTEND_DEFAULT)) {
                frontendLocations.add(jarContainer)
            }
        }

        classPathScanResult.close()
        /////////////////////

        val targetDirectory = nodeInfo.createFrontendDir()

        println("\tCopying jar frontend resources to '$targetDirectory'")

        // get all jar files having files in the 'META-INF/frontend' or 'META-INF/resources/frontend' folder.
        println("\t\tFound ${frontendLocations.size} resources")
        frontendLocations.forEach {
            println("\t\t\t${it.name}")
        }

        // copy jar resources
        @Suppress("LocalVariableName")
        val WILDCARD_INCLUSIONS = arrayOf("**/*.js", "**/*.css")
        val jarContentsManager = JarContentsManager()
        frontendLocations.forEach { location ->
            jarContentsManager.copyIncludedFilesFromJarTrimmingBasePath(
                location, Constants.RESOURCES_FRONTEND_DEFAULT,
                targetDirectory, *WILDCARD_INCLUSIONS
            )

            jarContentsManager.copyIncludedFilesFromJarTrimmingBasePath(
                location, Constants.COMPATIBILITY_RESOURCES_FRONTEND_DEFAULT,
                targetDirectory, *WILDCARD_INCLUSIONS
            )
        }

        val ms = (System.nanoTime() - start) / 1000000
        println("\t\tCopied ${frontendLocations.size} resources in $ms ms")
    }
}
