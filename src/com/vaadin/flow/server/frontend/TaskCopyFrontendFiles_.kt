package com.vaadin.flow.server.frontend

import com.vaadin.flow.server.Constants
import dorkbox.gradleVaadin.node.NodeInfo
import java.io.File

/**
 * flow-server-2.8.3
 */
object TaskCopyFrontendFiles_ {
    @Suppress("LocalVariableName")
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

            if (interiorPath.startsWith(Constants.RESOURCES_FRONTEND_DEFAULT) // "META-INF/frontend"
                ||
                interiorPath.startsWith(Constants.COMPATIBILITY_RESOURCES_FRONTEND_DEFAULT) // "META-INF/resources/frontend"
            ) {

                frontendLocations.add(jarContainer)
            }
        }

        classPathScanResult.close()
        /////////////////////

        val flowTargetDirectory = nodeInfo.createFrontendDir()

        val themeJarTargetDirectory = nodeInfo.frontendGeneratedDir

        println("\tCopying jar frontend resources to '$flowTargetDirectory'")
        println("\tCopying theme resources to '$themeJarTargetDirectory'")

        // get all jar files having files in the 'META-INF/frontend' or 'META-INF/resources/frontend' folder.
        println("\t\tFound ${frontendLocations.size} resources")
        frontendLocations.forEach {
            println("\t\t\t${it.name}")
        }

        // copy jar resources

        val WILDCARD_INCLUSIONS = arrayOf("**/*.js", "**/*.js.map", "**/*.css", "**/*.css.map", "**/*.ts", "**/*.ts.map" )
        val WILDCARD_INCLUSION_APP_THEME_JAR = "**/themes/**/*"


        val jarContentsManager = JarContentsManager()
        frontendLocations.forEach { location ->
            if (location.isDirectory) {
                TaskCopyLocalFrontendFiles.copyLocalResources(
                    File(location, Constants.RESOURCES_FRONTEND_DEFAULT),
                    flowTargetDirectory
                )
                TaskCopyLocalFrontendFiles.copyLocalResources(
                    File(
                        location,
                        Constants.COMPATIBILITY_RESOURCES_FRONTEND_DEFAULT
                    ),
                    flowTargetDirectory
                )
            } else {
                jarContentsManager.copyIncludedFilesFromJarTrimmingBasePath(
                    location, Constants.RESOURCES_FRONTEND_DEFAULT, // "META-INF/frontend"
                    flowTargetDirectory, *WILDCARD_INCLUSIONS
                )

                jarContentsManager.copyIncludedFilesFromJarTrimmingBasePath(
                    location, Constants.COMPATIBILITY_RESOURCES_FRONTEND_DEFAULT, // "META-INF/resources/frontend"
                    flowTargetDirectory, *WILDCARD_INCLUSIONS
                )

                jarContentsManager.copyIncludedFilesFromJarTrimmingBasePath(
                    location, Constants.RESOURCES_JAR_DEFAULT, // "META-INF/resources/"
                    themeJarTargetDirectory,
                    WILDCARD_INCLUSION_APP_THEME_JAR
                )
            }
        }

        val ms = (System.nanoTime() - start) / 1000000
        println("\t\tCopied ${frontendLocations.size} resources in $ms ms")
    }
}
