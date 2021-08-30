package dorkbox.gradleVaadin.node

import dorkbox.gradleVaadin.NodeExtension
import dorkbox.gradleVaadin.node.variant.VariantComputer
import org.gradle.api.Project

/**
 *
 */
class NodeInfo(val project: Project) {
    val nodeExtension = NodeExtension[project]

    val variantComputer = VariantComputer()

    val buildDir = nodeExtension.buildDir.get().asFile

    val nodeDirProvider = nodeExtension.workDir
    val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
    val nodeBinExec = variantComputer.computeNodeExec(nodeExtension, nodeBinDirProvider).get()


    val nodeDir = nodeDirProvider.get().asFile

    val nodeModulesDir = nodeDir.parentFile.resolve("node_modules")

    val npmDirProvider = variantComputer.computeNpmDir(nodeExtension, nodeDirProvider)
    val npmBinDirProvider = variantComputer.computeNpmBinDir(npmDirProvider)


    val npmScript = variantComputer.computeNpmScriptFile(nodeDirProvider, "npm")

    val pnpmScript = variantComputer.computePnpmScriptFile(nodeExtension)



}
