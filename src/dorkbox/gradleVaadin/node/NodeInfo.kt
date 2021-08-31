package dorkbox.gradleVaadin.node

import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.variant.VariantComputer
import org.gradle.api.Project

/**
 *
 */
class NodeInfo(val project: Project) {
    val vaadinConfig = VaadinConfig[project]

    val variantComputer = VariantComputer()

    val buildDir = vaadinConfig.buildDir

    val nodeDirProvider = vaadinConfig.nodeJsDir
    val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
    val nodeBinExec = variantComputer.computeNodeExec(vaadinConfig, nodeBinDirProvider).get()


    val nodeDir = nodeDirProvider.get().asFile

    val nodeModulesDir = nodeDir.parentFile.resolve("node_modules")

    val npmDirProvider = variantComputer.computeNpmDir(vaadinConfig, nodeDirProvider)
    val npmBinDirProvider = variantComputer.computeNpmBinDir(npmDirProvider)


    val npmScript = variantComputer.computeNpmScriptFile(nodeDirProvider, "npm")

    val pnpmScript = variantComputer.computePnpmScriptFile(vaadinConfig)



}
