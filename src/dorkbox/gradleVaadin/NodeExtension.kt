package dorkbox.gradleVaadin

import dorkbox.gradleVaadin.node.npm.proxy.ProxySettings
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.property

open class NodeExtension(project: Project) {
    private val buildDir_ = project.layout.buildDirectory

    /**
     * The directory where the build occurs
     */
    val buildDir = project.objects.directoryProperty().convention(buildDir_)

    /**
     * The directory where Node.js is unpacked (when download is true)
     */
    val workDir = project.objects.directoryProperty().convention(buildDir_.dir("nodejs"))

    /**
     * The directory where npm is installed (when a specific version is defined)
     */
    val npmWorkDir = project.objects.directoryProperty().convention(buildDir_.dir("npm"))

    /**
     * The directory where yarn is installed (when a Yarn task is used)
     */
    val yarnWorkDir = project.objects.directoryProperty().convention(buildDir_.dir("yarn"))

    /**
     * The Node.js project directory location
     * This is where the package.json file and node_modules directory are located
     * By default it is at the root of the current project
     */
    val nodeProjectDir = project.objects.directoryProperty().convention(buildDir_)

    /**
     * Version of node to download and install (only used if download is true)
     * It will be unpacked in the workDir
     */
    val version = project.objects.property<String>().convention(DEFAULT_NODE_VERSION)

    /**
     * Version of npm to use
     * If specified, installs it in the npmWorkDir
     * If empty, the plugin will use the npm command bundled with Node.js
     */
    val npmVersion = project.objects.property<String>().convention("")

    /**
     * Version of Yarn to use
     * Any Yarn task first installs Yarn in the yarnWorkDir
     * It uses the specified version if defined and the latest version otherwise (by default)
     */
    val yarnVersion = project.objects.property<String>().convention("")

    /**
     * Base URL for fetching node distributions
     * Only used if download is true
     * Change it if you want to use a mirror
     * Or set to null if you want to add the repository on your own.
     */
    val distBaseUrl = project.objects.property<String>().convention("https://nodejs.org/dist")

    val npmCommand = project.objects.property<String>().convention("npm")
    val npxCommand = project.objects.property<String>().convention("npx")
    val yarnCommand = project.objects.property<String>().convention("yarn")

    /**
     * The npm command executed by the npmInstall task
     * By default it is install but it can be changed to ci
     */
    val npmInstallCommand = project.objects.property<String>().convention("install")

    /**
     * Whether to download and install a specific Node.js version or not
     * If false, it will use the globally installed Node.js
     * If true, it will download node using above parameters
     * Note that npm is bundled with Node.js
     */
    var download = project.objects.property<Boolean>().convention(true)
        set(value) {
            field.set(value)
        }

    /**
     * Whether the plugin automatically should add the proxy configuration to npm and yarn commands
     * according the proxy configuration defined for Gradle
     *
     * Disable this option if you want to configure the proxy for npm or yarn on your own
     * (in the .npmrc file for instance)
     *
     */
    val nodeProxySettings = project.objects.property<ProxySettings>().convention(ProxySettings.SMART)

    companion object {
        const val NAME = "node"
        const val DEFAULT_NODE_VERSION = "14.15.4"
        const val DEFAULT_NPM_VERSION = "6.14.10"

        @JvmStatic
        operator fun get(project: Project): NodeExtension {
            return project.extensions.getByType()
        }

        @JvmStatic
        fun create(project: Project): NodeExtension {
            return project.extensions.create(NAME, project)
        }
    }
}
