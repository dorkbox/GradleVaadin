package com.vaadin.flow.server.frontend

import dorkbox.gradleVaadin.CustomClassFinder
import dorkbox.gradleVaadin.JsonPackageTools
import dorkbox.gradleVaadin.node.NodeInfo
import java.io.FileOutputStream


/**
 * flow-server-2.4.6
 */
object TaskUpdateWebpack_ {
    fun execute(nodeInfo: NodeInfo, customClassFinder: CustomClassFinder) {
        // NOTE: we do not use the default TaskUpdateWebpack execution, because there are some changes we have to make
        //  so we copy/modify it

        // If we have an old config file we remove it and create the new one
        // using the webpack.generated.js
        val configFile = nodeInfo.origWebPackFile

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
            val resource = this.javaClass.classLoader.getResource(FrontendUtils.WEBPACK_CONFIG)
            resource?.openStream()?.copyTo(FileOutputStream(configFile))
            Util.logger.info("Created webpack configuration file: $configFile")
        }

        JsonPackageTools.compareAndCopy(nodeInfo.origWebPackFile, nodeInfo.webPackFile)
        JsonPackageTools.compareAndCopy(nodeInfo.origWebPackProdFile, nodeInfo.webPackProdFile)



        // Generated file is always re-written
        val generatedFile = nodeInfo.webPackGeneratedFile
        println("\tUpdating generated webpack file: $generatedFile")


        val resource = customClassFinder.getResource(FrontendUtils.WEBPACK_GENERATED)
        resource?.openStream()?.copyTo(FileOutputStream(generatedFile))

        val replacements = mutableListOf<Pair<String, String>>()

        replacements.add(Pair(" * This file has been generated by the `flow:prepare-frontend` maven goal",
                              " * This file has been generated by the Vaadin `updateWebPack` gradle task."))


        // There are SUBTLE differences between windows and not-windows with webpack!
        // NOTE: this does NOT make sense!
        //  windows: '..\frontend'
        //    linux: 'frontend'
        replacements.add(Pair("const frontendFolder =",
                              "const frontendFolder = path.resolve(__dirname, '${nodeInfo.frontendDirWebPack}');"))


        // 'frontend/generated-flow-imports.js'
        val flowImportsReplacement = JsonPackageTools.relativize(nodeInfo.buildDir, nodeInfo.flowImportFile)
        replacements.add(Pair("const fileNameOfTheFlowGeneratedMainEntryPoint =",
                              "const fileNameOfTheFlowGeneratedMainEntryPoint = path.resolve(__dirname, '$flowImportsReplacement')"))



        // '../resources/META-INF/resources/VAADIN'
        val webPackReplacement = JsonPackageTools.relativize(nodeInfo.buildDir, nodeInfo.vaadinDir)
        replacements.add(Pair("const mavenOutputFolderForFlowBundledFiles =",
                              "const mavenOutputFolderForFlowBundledFiles = path.resolve(__dirname, '$webPackReplacement');"))


        replacements.add(Pair("const devmodeGizmoJS =",
                              "const devmodeGizmoJS = '" + FrontendUtils.DEVMODE_GIZMO_MODULE + "'"))


        // NOTE: new stuff. Change 'src/main/webapp' -> 'webapp' (or META-INF? which is where all static resources are served...)
        val relativeStaticResources = JsonPackageTools.relativize(nodeInfo.sourceDir, nodeInfo.metaInfDir).replace("./", "")
        replacements.add(Pair("contentBase: [mavenOutputFolderForFlowBundledFiles,",
                              "contentBase: [mavenOutputFolderForFlowBundledFiles, '$relativeStaticResources'],"))


        val lines = generatedFile.readLines(Charsets.UTF_8).toMutableList()
        for (i in lines.indices) {
            val line = lines[i].trim()

            val hasReplacement = replacements.firstOrNull { line.startsWith(it.first) }
            if (hasReplacement != null) {
                lines[i] = hasReplacement.second
            }
        }

        // always have unix!
        generatedFile.writeText(lines.joinToString(separator = "\n"))
    }
}
