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

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS_FULL   // always show the stacktrace!

plugins {
    `java-gradle-plugin`

    id("com.gradle.plugin-publish") version "2.0.0"

    id("com.dorkbox.GradleUtils") version "4.8"
    id("com.dorkbox.Licensing") version "3.1"
    id("com.dorkbox.VersionUpdate") version "3.0"

    kotlin("jvm") version "2.3.0"
}


///////////////////////////////
/////  assign 'Extras'
///////////////////////////////
GradleUtils.load {
    group = "com.dorkbox"
    id = "GradleVaadin"
    description = "Gradle Plugin to build Vaadin for use by the VaadinUndertow library"
    name = "Gradle Vaadin"
    // the version here ROUGHLY tracks the major/minor version of vaadin!
    version = "14.10.1"
    vendor = "Dorkbox LLC"
    url = "https://git.dorkbox.com/dorkbox/GradleVaadin"
}
GradleUtils.defaults()
GradleUtils.compileConfiguration(JavaVersion.VERSION_25)

licensing {
    license(License.APACHE_2) {
        description(Extras.description)
        author(Extras.vendor)
        url(Extras.url)
    }
}

repositories {
    gradlePluginPortal()
}

dependencies {
    val vaadinUndertowVer = "14.10"

    // These MUST be in lock-step with what the VaadinUndertow launcher defines, otherwise horrific errors can occur.
    val vaadinVer = "14.10.1"
    val undertowVer = "2.2.21.Final"

    val vaadinFlowVer = "2.9.2"

    // the kotlin version is taken from the plugin, so it is not necessary to set it here
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Uber-fast, ultra-lightweight Java classpath and module path scanner
    implementation("io.github.classgraph:classgraph:4.8.184")

    // implementation("com.vaadin:vaadin:${Extras.vaadinVer}") // NOTE: uncomment for testing ONLY
    implementation("com.vaadin:flow-server:${vaadinFlowVer}")
    implementation("com.vaadin.external.gwt:gwt-elemental:2.8.2.vaadin2")


    // this is used to announce the version of vaadin to use with the plugin
    implementation("com.dorkbox:VaadinUndertow:${vaadinUndertowVer}")
    implementation("com.dorkbox:Executor:3.14")
    implementation("com.dorkbox:Version:3.1")

    api("com.dorkbox.GradleUtils:com.dorkbox.GradleUtils.gradle.plugin:4.4")
}

tasks.jar.get().apply {
    // include the webpack.*.js files
    from("src") {
        include("com/vaadin/flow/server/frontend/webpack.*.js")
    }
}

/////////////////////////////////
////////    Plugin Publishing + Release
/////////////////////////////////
gradlePlugin {
    website.set(Extras.url)
    vcsUrl.set(Extras.url)

    plugins {
        register("GradleVaadin") {
            id = Extras.groupAndId
            implementationClass = "dorkbox.gradleVaadin.Vaadin"
            displayName = Extras.name
            description = Extras.description
            version = Extras.version
            tags.set(listOf("vaadin", "undertow"))
        }
    }
}
