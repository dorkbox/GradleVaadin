/*
 * Copyright 2020 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.gradleVaadin

import com.vaadin.flow.server.frontend.FrontendTools
import dorkbox.gradleVaadin.node.npm.proxy.ProxySettings
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import java.io.File
import java.util.*

open class VaadinConfig(private val project: Project): java.io.Serializable {
    companion object {
        // we are locked to this version number! any change to this must reflect changes to our API, and how we access vaadin
        // so tests must be run to make sure our api is compatible with this release
        const val VAADIN_VERSION = "14.4.8"
        const val DEFAULT_NPM_VERSION = "6.14.10"

        const val NAME = "node"

        @JvmStatic
        operator fun get(project: Project): VaadinConfig {
            return project.extensions.getByType(VaadinConfig::class.java)
        }

        @JvmStatic
        fun create(project: Project): VaadinConfig {
            return project.extensions.create("vaadin", VaadinConfig::class.java, project)
        }
    }
    // the gradle property model is retarded, but sadly the "right way to do it"

    val version = VAADIN_VERSION




    // this changes if the build production task is run. WE DO NOT MANUALLY CHANGE THIS, it is automatic!
    internal var productionMode = project.objects.property(Boolean::class.java).convention(false)

    internal val nodeModulesDir: File
    get() {
        return buildDir.resolve("node_modules")
    }

    /**
     * Directory where all of the source code lives
     */
    @get:Input
    protected var sourceRootDir_ = project.objects.property(File::class.java).convention(project.projectDir)
    var sourceRootDir: File
        get() { return sourceRootDir_.get() }
        set(value) { sourceRootDir_.set(value)}


    /**
     * The directory where the build occurs.
     * Also where package.json and the node_modules directory are located
     */
    @get:Input
    protected var buildDir_ = project.objects.property(File::class.java).convention(project.buildDir)
    var buildDir: File
        get() { return buildDir_.get() }
        set(value) { buildDir_.set(value)}


    @get:Input
    protected var debug_ = project.objects.property(Boolean::class.java).convention(false)
    var debug: Boolean
    get() { return debug_.get() }
    set(value) { debug_.set(value)}

    /**
     * Used by the custom vaadin application launcher.
     * When building a production jar, to extract the contents of the jar or not
     * Usually -- for performance reasons, you want it to "self extract" the jar. For rapid testing, you don't always want this.
     */
    @get:Input
    protected var extractJar_ = project.objects.property(Boolean::class.java).convention(true)
    var extractJar: Boolean
    get() { return extractJar_.get() }
    set(value) { extractJar_.set(value)}

    /**
     * Used by the custom vaadin application launcher.
     * When extracting contents from a jar, to overwrite the destination file or not
     * Usually -- for performance reasons, you want it to "self extract" the jar. For rapid testing, you don't always want this.
     */
    @get:Input
    protected var extractJarOverwrite_ = project.objects.property(Boolean::class.java).convention(false)
    var extractJarOverwrite: Boolean
    get() { return extractJarOverwrite_.get() }
    set(value) { extractJarOverwrite_.set(value)}


    @get:Input
    protected var enablePnpm_ = project.objects.property(Boolean::class.java).convention(false)
    var enablePnpm: Boolean
        get() { return enablePnpm_.get() }
        set(value) { enablePnpm_.set(value)}


    @get:Input
    protected var flowDirectory_ = project.objects.property(String::class.java).convention("./${com.vaadin.flow.server.frontend.FrontendUtils.FRONTEND}")
    var flowDirectory: String
    get() { return flowDirectory_.get() }
    set(value) { flowDirectory_.set(value)}






    /**
     * Version of PNPM to download and install
     */
    @get:Input
    var pnpmVersion = FrontendTools.DEFAULT_PNPM_VERSION
//    set(value) {
//        field = value
//        NodeExtension[project].version.set(value)
//    }

    // undertowVersion

    @get:Input
    var workDir = project.buildDir
        set(value) {
            field = value
//            NodeExtension[project].test.set(value)
        }


    internal val vaadinCompiler by lazy {  VaadinCompiler(project) }
//    internal fun init() {
//        println("\tInitializing the vaadin compiler")
//    }




















    private val buildDir__ = project.layout.buildDirectory

//    /**
//     * The directory where the build occurs.
//     * Also where package.json and the node_modules directory are located
//     */
//    val buildDir = project.objects.directoryProperty().convention(buildDir_)

    /**
     * The directory where Node.js is unpacked (when download is true)
     */
    val nodeJsDir = project.objects.directoryProperty().convention(buildDir__.dir("nodejs"))

    /**
     * The directory where npm is installed (when a specific version is defined)
     */
    val npmDir = project.objects.directoryProperty().convention(buildDir__.dir("npm"))

    /**
     * The directory where yarn is installed (when a Yarn task is used)
     */
    val yarnDir = project.objects.directoryProperty().convention(buildDir__.dir("yarn"))

    /**
     * The Node.js project directory location
     * This is where the package.json file and node_modules directory are located
     * By default it is at the root of the current project
     */
    val nodeProjectDir = project.objects.directoryProperty().convention(buildDir__)

    /**
     * Version of node to download and install (only used if download is true)
     * It will be unpacked in the workDir
     */
    @get:Input
    protected val nodeVersion_ = project.objects.property(String::class.java).convention(FrontendTools.DEFAULT_NODE_VERSION)
    var nodeVersion: String
        get() { return nodeVersion_.get().let {
            if (it.lowercase(Locale.getDefault()).startsWith('v')) {
                it
            } else {
                "v$it"
            }
        } }
        set(value) { nodeVersion_.set(value)}

    /**
     * Version of npm to use
     * If specified, installs it in the npmWorkDir
     * If empty, the plugin will use the npm command bundled with Node.js
     */
    val npmVersion = project.objects.property(String::class.java).convention("")

    /**
     * Version of Yarn to use
     * Any Yarn task first installs Yarn in the yarnWorkDir
     * It uses the specified version if defined and the latest version otherwise (by default)
     */
    val yarnVersion = project.objects.property(String::class.java).convention("")

    /**
     * Base URL for fetching node distributions
     * Only used if download is true
     * Change it if you want to use a mirror
     * Or set to null if you want to add the repository on your own.
     */
    val distBaseUrl = project.objects.property(String::class.java).convention("https://nodejs.org/dist")

    val npmCommand = project.objects.property(String::class.java).convention("npm")
    val npxCommand = project.objects.property(String::class.java).convention("npx")
    val yarnCommand = project.objects.property(String::class.java).convention("yarn")

    /**
     * The npm command executed by the npmInstall task
     * By default it is install but it can be changed to ci
     */
    val npmInstallCommand = project.objects.property(String::class.java).convention("install")

    /**
     * Whether to download and install a specific Node.js version or not
     * If false, it will use the globally installed Node.js
     * If true, it will download node using above parameters
     * Note that npm is bundled with Node.js
     */
    var download = project.objects.property(Boolean::class.java).convention(true)
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
    val nodeProxySettings = project.objects.property(ProxySettings::class.java).convention(ProxySettings.SMART)
}
