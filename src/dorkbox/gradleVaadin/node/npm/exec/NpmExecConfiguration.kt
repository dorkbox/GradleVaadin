package dorkbox.gradleVaadin.node.npm.exec

import dorkbox.gradleVaadin.NodeExtension
import dorkbox.gradleVaadin.node.variant.VariantComputer
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

internal typealias CommandExecComputer = (variantComputer: VariantComputer, nodeExtension: NodeExtension,
                                          npmBinDir: Provider<Directory>) -> Provider<String>

internal data class NpmExecConfiguration(
        val command: String,
        val commandExecComputer: CommandExecComputer
)
