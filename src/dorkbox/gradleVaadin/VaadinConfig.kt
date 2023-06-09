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
import com.vaadin.flow.server.frontend.FrontendUtils.FRONTEND
import dorkbox.gradleVaadin.node.npm.proxy.ProxySettings
import dorkbox.vaadin.VaadinApplication
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Input
import java.io.File
import java.util.*

open class VaadinConfig(private val project: Project): java.io.Serializable {

    companion object {
        // we are locked to this version number! any change to this must reflect changes to our API, and how we access vaadin
        // so tests must be run to make sure our api is compatible with this release
        // https://vaadin.com/docs/v14/guide/install/frontend
        const val VAADIN_VERSION = VaadinApplication.vaadinVersion
        const val UNDERTOW_VERSION = VaadinApplication.undertowVersion

        const val OSHI_VERSION = VaadinApplication.oshiVersion
        const val JNA_VERSION = VaadinApplication.jnaVersion


        // This will always match what the build file imports!
        const val MAVEN_VAADIN_GRADLE_VERSION = VaadinApplication.version

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

    internal var explicitRun = project.objects.property(Boolean::class.java).convention(false)

    /**
     * Directory where all of the source code lives
     */
    @get:Input
    protected var projectDir_ = project.objects.property(File::class.java).convention(project.projectDir)
    var projectDir: File
        get() { return projectDir_.get() }
        set(value) { projectDir_.set(value)}


    /**
     * The directory where the build occurs.
     * Also where package.json and the node_modules directory are located
     */
    @get:Input
    protected var buildDir_ = project.objects.property(File::class.java).convention(project.buildDir)
    var buildDir: File
        get() { return buildDir_.get() }
        set(value) { buildDir_.set(value) }



    // this changes if the build production task is run. WE DO NOT MANUALLY CHANGE THIS, it is automatic!
    internal var productionMode = project.objects.property(Boolean::class.java).convention(false)

    internal val nodeModulesDir: File
    get() { return buildDir.resolve("node_modules") }

    @get:Input
    protected var debug_ = project.objects.property(Boolean::class.java).convention(false)
    var debug: Boolean
    get() { return debug_.get() }
    set(value) { debug_.set(value)}

    @get:Input
    protected var debugNodeJs_ = project.objects.property(Boolean::class.java).convention(false)
    var debugNodeJs: Boolean
    get() { return debugNodeJs_.get() }
    set(value) { debugNodeJs_.set(value)}

    /**
     * Adds "NODE_OPTIONS" to the environment when launching node.js
     */
    @get:Input
    protected var nodeJsOptions_ = project.objects.property(String::class.java).convention("")
    var nodeOptions: String
        get() { return nodeJsOptions_.get() }
        set(value) { nodeJsOptions_.set(value)}


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
     * Used when compiling Vaadin.
     * Old License mode (false, default): Javascript license checker ("old" license validation method). Compatibility/Bower mode always uses old license checking.
     * New License mode (true): Check the license when compiling vaadin ("new" license validation method)
     */
    @get:Input
    protected var newLicenseMode_ = project.objects.property(Boolean::class.java).convention(false)
    var newLicenseMode: Boolean
    get() { return newLicenseMode_.get() }
    set(value) { newLicenseMode_.set(value)}

    @get:Input
    protected var enablePnpm_ = project.objects.property(Boolean::class.java).convention(false)
    var enablePnpm: Boolean
        get() { return enablePnpm_.get() }
        set(value) { enablePnpm_.set(value)}

    @get:Input
    protected var enableCiBuild_ = project.objects.property(Boolean::class.java).convention(false)
    var enableCiBuild: Boolean
        get() { return enableCiBuild_.get() }
        set(value) { enableCiBuild_.set(value)}


    @get:Input
    protected var flowDirectory_ = project.objects.property(String::class.java).convention("./$FRONTEND")
    var flowDirectory: String
    get() { return flowDirectory_.get() }
    set(value) { flowDirectory_.set(value)}


    // There are SUBTLE differences between windows and not-windows with webpack!
    // NOTE: RELATIVE TO THE BUILD DIR!
    // NOTE: this does NOT make sense!
    //  windows: '..\frontend' (windows + linux -> undertow example only works here, windows -> netref works here)
    //    linux: 'frontend'  (linux -> netref only works here)
    //    if (PlatformHelper.INSTANCE.isWindows)
    //        JsonPackageTools.relativize(buildDir, sourceDir.resolve(config.frontendSourceDirectory))
    //    else
    //        FrontendUtils.FRONTEND
    /**
     * the location of the GENERATED frontend files, relative to the build directory.
     */
    @get:Input
    protected var frontendGeneratedDirectory_ = project.objects.property(String::class.java).convention("$FRONTEND")
    var frontendGeneratedDir: String
    get() { return frontendGeneratedDirectory_.get() }
    set(value) { frontendGeneratedDirectory_.set(value)}


    /**
     * the location of the SOURCE frontend files, relative to the source directory.
     */
    @get:Input
    protected var frontendSourceDirectory_ = project.objects.property(String::class.java).convention("$FRONTEND")
    var frontendSourceDir: String
    get() { return frontendSourceDirectory_.get() }
    set(value) { frontendSourceDirectory_.set(value)}






    /**
     * Version of PNPM to download and install
     */
    @get:Input
    var pnpmVersion = FrontendTools.DEFAULT_PNPM_VERSION

    @get:Input
    var workDir = project.buildDir


    internal val vaadinCompiler by lazy {  VaadinCompiler(project) }










    val buildDir____: DirectoryProperty = project.objects.directoryProperty()
    internal var buildDir__: DirectoryProperty
        get() {
            if (!buildDir____.isPresent) {
                buildDir____.set(buildDir)
            }
            return buildDir____
        }
        set(value) { buildDir____.set(value)}


    /**
     * The directory where Node.js is unpacked (when download is true)
     */
    val nodeJsDir_ = project.objects.directoryProperty()
    internal var nodeJsDir: DirectoryProperty
        get() {
            if (!nodeJsDir_.isPresent) {
                nodeJsDir_.set(buildDir__.dir("nodejs"))
            }
            return nodeJsDir_
        }
        set(value) { nodeJsDir_.set(value)}


    val nodeJsDir__ by lazy { buildDir.resolve("nodejs") }

    /**
     * The directory where npm is installed (when a specific version is defined)
     */
    val npmDir__ = project.objects.directoryProperty()
    internal var npmDir: DirectoryProperty
        get() {
            if (!npmDir__.isPresent) {
                npmDir__.set(buildDir__.dir("npm"))
            }
            return npmDir__
        }
        set(value) { npmDir__.set(value)}


    /**
     * Version of node to download and install (only used if download is true)
     * It will be unpacked in the workDir
     */
    @get:Input
    protected val nodeVersion_ = project.objects.property(String::class.java).convention("16.13.1")
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
     * Base URL for fetching node distributions
     * Only used if download is true
     * Change it if you want to use a mirror
     * Or set to null if you want to add the repository on your own.
     */
    val distBaseUrl = project.objects.property(String::class.java).convention("https://nodejs.org/dist")

    val npmCommand = project.objects.property(String::class.java).convention("npm")
    val npxCommand = project.objects.property(String::class.java).convention("npx")


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
