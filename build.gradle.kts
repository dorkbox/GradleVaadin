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
import org.gradle.tooling.model.gradle.GradlePublication
import java.time.Instant

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS_FULL   // always show the stacktrace!

plugins {
    `java-gradle-plugin`

    id("com.gradle.plugin-publish") version "1.1.0"

    id("com.dorkbox.Licensing") version "2.17"
    id("com.dorkbox.VersionUpdate") version "2.5"
    id("com.dorkbox.GradleUtils") version "3.3"

    kotlin("jvm") version "1.7.20"
}

object Extras {
    // set for the project
    const val description = "Gradle Plugin to build Vaadin for use by the VaadinUndertow library"
    const val group = "com.dorkbox"
    const val version = "14.8"

    // set as project.ext
    const val name = "Gradle Vaadin"
    const val id = "GradleVaadin"
    const val vendor = "Dorkbox LLC"
    const val url = "https://git.dorkbox.com/dorkbox/GradleVaadin"
    val tags = listOf("vaadin", "undertow")
    val buildDate = Instant.now().toString()

    const val vaadinUndertowVer = "14.8"

    // These MUST be in lock-step with what the VaadinUndertow launcher defines, otherwise horrific errors can occur.
    const val vaadinVer = "14.8.20"
    const val undertowVer = "2.2.21.Final"

    const val vaadinFlowVer = "2.7.23"

}

///////////////////////////////
/////  assign 'Extras'
///////////////////////////////
GradleUtils.load("$projectDir/../../gradle.properties", Extras)
GradleUtils.defaults()
GradleUtils.compileConfiguration(JavaVersion.VERSION_11)

licensing {
    license(License.APACHE_2) {
        description(Extras.description)
        author(Extras.vendor)
        url(Extras.url)
    }
}


dependencies {
    // the kotlin version is taken from the plugin, so it is not necessary to set it here
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    // Uber-fast, ultra-lightweight Java classpath and module path scanner
    implementation("io.github.classgraph:classgraph:4.8.151")

    //  implementation("com.vaadin:vaadin:${Extras.vaadinVer}") // NOTE: uncomment for testing ONLY
    implementation("com.vaadin:flow-server:${Extras.vaadinFlowVer}")

    // this is used to announce the version of vaadin to use with the plugin
    implementation("com.dorkbox:VaadinUndertow:${Extras.vaadinUndertowVer}")
    implementation("com.dorkbox:Executor:3.11")
    implementation("com.dorkbox:Version:2.4")
}

tasks.jar.get().apply {
    // include the webpack.*.js files
    from("src") {
        include("com/vaadin/flow/server/frontend/webpack.*.js")
    }

    manifest {
        // https://docs.oracle.com/javase/tutorial/deployment/jar/packageman.html
        attributes["Name"] = Extras.name

        attributes["Specification-Title"] = Extras.name
        attributes["Specification-Version"] = Extras.version
        attributes["Specification-Vendor"] = Extras.vendor

        attributes["Implementation-Title"] = "${Extras.group}.${Extras.id}"
        attributes["Implementation-Version"] = Extras.buildDate
        attributes["Implementation-Vendor"] = Extras.vendor
    }
}
repositories {
    mavenCentral()
}


/////////////////////////////////
////////    Plugin Publishing + Release
/////////////////////////////////
gradlePlugin {
    plugins {
        create("GradlePublish") {
            id = "${Extras.group}.${Extras.id}"
            implementationClass = "dorkbox.gradleVaadin.Vaadin"
            displayName = Extras.name
            description = Extras.description
            version = Extras.version
        }
    }
}

pluginBundle {
    website = Extras.url
    vcsUrl = Extras.url
    tags = Extras.tags
}
