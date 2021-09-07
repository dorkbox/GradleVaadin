package com.vaadin.flow.server.frontend

import dorkbox.gradleVaadin.CustomClassFinder
import dorkbox.gradleVaadin.JsonPackageTools
import dorkbox.gradleVaadin.node.NodeInfo
import org.apache.commons.io.FileUtils
import java.io.FileOutputStream

/**
 * flow-server-2.4.6
 */
object TaskUpdateWebpack_ {
    fun execute(nodeInfo: NodeInfo, customClassFinder: CustomClassFinder) {
        // NOTE: we do not use the default TaskUpdateWebpack execution, because there are some changes we have to make
        // so we copy/modify it

        // If we have an old config file we remove it and create the new one
        // using the webpack.generated.js
        val configFile = nodeInfo.webPackFile

        if (configFile.exists()) {
            if (!FrontendUtils.isWebpackConfigFile(configFile)) {
                Util.logger.warn(
                    "Flow generated webpack configuration was not mentioned "
                            + "in the configuration file: {}."
                            + "Please verify that './webpack.generated.js' is used "
                            + "in the merge or remove the file to generate a new one.",
                    configFile
                )
            }
        } else {
            val resource = this.javaClass.classLoader
                .getResource(FrontendUtils.WEBPACK_CONFIG)
            FileUtils.copyURLToFile(resource, configFile)
            Util.logger.info("Created webpack configuration file: $configFile")
        }

        JsonPackageTools.compareAndCopy(nodeInfo.origWebPackFile, nodeInfo.webPackFile)
        JsonPackageTools.compareAndCopy(nodeInfo.origWebPackProdFile, nodeInfo.webPackProdFile)


        // we have to make additional customizations to the webpack.generated.js file
        val relativeStaticResources = JsonPackageTools.relativize(nodeInfo.baseDir, nodeInfo.metaInfDir).replace("./", "")


        val sourceFrontEndDir = nodeInfo.frontendDir
        val webpackOutputDir = nodeInfo.vaadinDir
        val flowImportFile = nodeInfo.flowImportFile



        val parentDirectory = configFile.parentFile
        val absoluteConfigPath = parentDirectory.absoluteFile
        val absoluteSourceFrontEndPath = sourceFrontEndDir.absoluteFile
        val absoluteWebpackOutputPath = webpackOutputDir.absoluteFile


        // Generated file is always re-written
        val generatedFile = parentDirectory.resolve(FrontendUtils.WEBPACK_GENERATED).absoluteFile
        println("\tUpdating generated webpack file: $generatedFile")


        val resource = customClassFinder.getResource(FrontendUtils.WEBPACK_GENERATED)
        resource?.openStream()?.copyTo(FileOutputStream(generatedFile))

        val frontEndReplacement = JsonPackageTools.relativize(absoluteConfigPath, absoluteSourceFrontEndPath)
        val frontendLine = "const frontendFolder = require('path').resolve(__dirname, '$frontEndReplacement');"

        // this is the 'META-INF/resources/VAADIN' directory
        val webPackReplacement = JsonPackageTools.relativize(absoluteConfigPath, absoluteWebpackOutputPath)
        val outputLine = "const mavenOutputFolderForFlowBundledFiles = require('path').resolve(__dirname, '$webPackReplacement');"

        val flowImportsReplacement = JsonPackageTools.relativize(absoluteConfigPath, flowImportFile)
        val mainLine = "const fileNameOfTheFlowGeneratedMainEntryPoint = require('path').resolve(__dirname, '$flowImportsReplacement');"

        val devmodeGizmoJSLine = "const devmodeGizmoJS = '" + FrontendUtils.DEVMODE_GIZMO_MODULE + "'"


        // NOTE: new stuff. Change 'src/main/webapp' -> 'webapp' (or META-INF? which is where all static resources are served...)
        val webappDirLine = "contentBase: [mavenOutputFolderForFlowBundledFiles, '$relativeStaticResources'],"



        val lines = generatedFile.readLines(Charsets.UTF_8).toMutableList()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("const frontendFolder")) {
                lines[i] = frontendLine
            } else if (line.startsWith("const mavenOutputFolderForFlowBundledFiles") && line != outputLine) {
                lines[i] = outputLine
            } else if (line.startsWith("const fileNameOfTheFlowGeneratedMainEntryPoint") && line != mainLine) {
                lines[i] = mainLine
            } else if (line.startsWith("const devmodeGizmoJS") && line != devmodeGizmoJSLine) {
                lines[i] = devmodeGizmoJSLine
            } else if (line.startsWith("contentBase: [mavenOutputFolderForFlowBundledFiles") && line != webappDirLine) {
                lines[i] = webappDirLine
            }
        }

        generatedFile.writeText(lines.joinToString(separator = "\n"))
    }
}
