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
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.property

open class VaadinConfig(private val project: Project): java.io.Serializable {
    companion object {
        @JvmStatic
        operator fun get(project: Project): VaadinConfig {
            return project.extensions.getByType()
        }

        @JvmStatic
        fun create(project: Project): VaadinConfig {
            return project.extensions.create("vaadin", VaadinConfig::class.java, project)
        }
    }

    // this changes if the build production task is run
    internal var productionMode = project.objects.property<Boolean>().convention(false)


    // the gradle property model is retarded, but sadly the "right way to do it"
    @get:Input
    protected var debug_ = project.objects.property<Boolean>().convention(false)
    var debug: Boolean
    get() { return debug_.get() }
    set(value) { debug_.set(value)}


    // the gradle property model is retarded, but sadly the "right way to do it"
    @get:Input
    protected var enablePnpm_ = project.objects.property<Boolean>().convention(false)
    var enablePnpm: Boolean
        get() { return enablePnpm_.get() }
        set(value) { enablePnpm_.set(value)}


    @get:Input
    protected var flowDirectory_ = project.objects.property<String>().convention("./${com.vaadin.flow.server.frontend.FrontendUtils.FRONTEND}")
    var flowDirectory: String
    get() { return flowDirectory_.get() }
    set(value) { flowDirectory_.set(value)}


    /**
     * Version of node to download and install
     */
    @get:Input
    var nodeVersion = FrontendTools.DEFAULT_NODE_VERSION
    set(value) {
        field = value
        NodeExtension[project].version.set(value)
    }

    /**
     * Version of PNPM to download and install
     */
    @get:Input
    var pnpmVersion = FrontendTools.DEFAULT_PNPM_VERSION
//    set(value) {
//        field = value
//        NodeExtension[project].version.set(value)
//    }

    @get:Input
    var vaadinVersion = "14.1.17"

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
}
