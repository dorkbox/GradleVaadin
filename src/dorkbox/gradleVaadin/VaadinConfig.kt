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

import com.vaadin.flow.server.Constants
import com.vaadin.flow.server.frontend.FrontendUtils
import dorkbox.gradleVaadin.node.variant.VariantComputer
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.internal.impldep.org.apache.maven.model.Developer
import org.gradle.internal.impldep.org.apache.maven.model.IssueManagement
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.signatory.internal.pgp.InMemoryPgpSignatoryProvider
import java.io.File
import java.security.PrivateKey
import java.time.Duration

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

    /**
     * Version of node to download and install
     */
    @get:Input
    var nodeVersion = "13.7.0"
    set(value) {
        field = value
        NodeExtension[project].version.set(value)
    }

    @get:Input
    var vaadinVersion = "14.1.17"

    @get:Input
    var workDir = project.buildDir
        set(value) {
            field = value
//            NodeExtension[project].test.set(value)
        }


//    @get:Input
//    var test = "aaaa"
//        set(value) {
//            field = value
//            NodeExtension[project].test.set(value)
//        }
}
