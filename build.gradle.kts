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
import java.time.Instant

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS_FULL   // always show the stacktrace!

plugins {
    java
    `java-gradle-plugin`
    `kotlin-dsl`

    id("com.gradle.plugin-publish") version "0.14.0"

    id("com.dorkbox.Licensing") version "2.9.1"
    id("com.dorkbox.VersionUpdate") version "2.4"
    id("com.dorkbox.GradleUtils") version "2.9"

    kotlin("jvm") version "1.4.31"
}

object Extras {
    // set for the project
    const val description = "Gradle Plugin to build Vaadin for use by the VaadinUndertow library"
    const val group = "com.dorkbox"
    const val version = "0.1"

    // set as project.ext
    const val name = "Gradle Vaadin"
    const val id = "GradleVaadin"
    const val vendor = "Dorkbox LLC"
    const val url = "https://git.dorkbox.com/dorkbox/GradleVaadin"
    val tags = listOf("vaadin", "undertow")
    val buildDate = Instant.now().toString()

    const val coroutineVer = "1.4.3"
    const val vaadinVer = "14.1.17"
    const val undertowVer = "2.2.9.Final"
}

///////////////////////////////
/////  assign 'Extras'
///////////////////////////////
GradleUtils.load("$projectDir/../../gradle.properties", Extras)
GradleUtils.defaults()
GradleUtils.compileConfiguration(JavaVersion.VERSION_1_8)

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

    // setup node.js
    // https://github.com/node-gradle/gradle-node-plugin
//    implementation("com.github.node-gradle:gradle-node-plugin:3.1.0")

    // Uber-fast, ultra-lightweight Java classpath and module path scanner
    implementation("io.github.classgraph:classgraph:4.8.110")

    // for parsing JSON
//    implementation("org.json:json:20210307")

//    implementation("com.vaadin:flow-server:2.1.5")
    implementation("com.vaadin:vaadin:${Extras.vaadinVer}")

    implementation("com.dorkbox:Executor:3.3")
    implementation("com.dorkbox:Version:2.4")
}

tasks.jar.get().apply {
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


/////////////////////////////////
////////    Plugin Publishing + Release
/////////////////////////////////
gradlePlugin {
    plugins {
        create("GradlePublish") {
            id = "${Extras.group}.${Extras.id}"
            implementationClass = "dorkbox.gradleVaadin.Vaadin"
        }
    }
}

pluginBundle {
    website = Extras.url
    vcsUrl = Extras.url

    (plugins) {
        "GradlePublish" {
            id = "${Extras.group}.${Extras.id}"
            displayName = Extras.name
            description = Extras.description
            tags = Extras.tags
            version = Extras.version
        }
    }
}
