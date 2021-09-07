package dorkbox.gradleVaadin.node

import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.frontend.FrontendUtils
import dorkbox.gradleVaadin.VaadinConfig
import dorkbox.gradleVaadin.node.variant.VariantComputer
import org.gradle.api.Project

/**
 *
 */
class NodeInfo(val project: Project) {
    val config = VaadinConfig[project]

    val enablePnpm = config.enablePnpm
    
    val baseDir = config.sourceRootDir
    val buildDir = config.buildDir


    val variantComputer = VariantComputer()


    val webAppDir = baseDir.resolve("resources")
    val metaInfDir = webAppDir.resolve("META-INF")
    val vaadinDir = metaInfDir.resolve("resources").resolve("VAADIN")


    val frontendGeneratedDir = buildDir.resolve(FrontendUtils.FRONTEND)

    val pnpmFile = buildDir.resolve("pnpmfile.js")


    // This file also points to the generated package file in the generated frontend dir
    val origWebPackFile = baseDir.resolve(FrontendUtils.WEBPACK_CONFIG)
    val origWebPackProdFile = baseDir.resolve("webpack.production.js")

    val jsonPackageFile = baseDir.resolve(Constants.PACKAGE_JSON)
    val jsonPackageLockFile = buildDir.resolve("package-lock.json")
    val webPackFile = buildDir.resolve(FrontendUtils.WEBPACK_CONFIG)
    val webPackProdFile = buildDir.resolve("webpack.production.js")
// // FrontendUtils.WEBPACK_CONFIG,
            //                    FrontendUtils.WEBPACK_GENERATED

    val buildDirJsonPackageFile = buildDir.resolve(Constants.PACKAGE_JSON)


    val frontendDir = baseDir.resolve(FrontendUtils.FRONTEND)


    val flowJsonPackageFile = buildDir.resolve(config.flowDirectory).resolve(Constants.PACKAGE_JSON)
    val generatedNodeModules = buildDir.resolve(FrontendUtils.NODE_MODULES)
    val webPackExecutableFile = generatedNodeModules.resolve("webpack").resolve("bin").resolve("webpack.js")

    val tokenFile = buildDir.resolve(FrontendUtils.TOKEN_FILE)
    val generatedFilesDir = buildDir.resolve(FrontendUtils.FRONTEND)

    val flowImportFile = generatedFilesDir.resolve(FrontendUtils.IMPORTS_NAME)


    val nodeDirProvider = config.nodeJsDir
    val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
    val nodeBinExec = variantComputer.computeNodeExec(config, nodeBinDirProvider).get()


    val nodeDir = nodeDirProvider.get().asFile

    val nodeModulesDir = nodeDir.parentFile.resolve("node_modules")

    val npmDirProvider = variantComputer.computeNpmDir(config, nodeDirProvider)
    val npmBinDirProvider = variantComputer.computeNpmBinDir(npmDirProvider)


    val npmScript = variantComputer.computeNpmScriptFile(nodeDirProvider, "npm")

    val pnpmScript = variantComputer.computePnpmScriptFile(config)



}
