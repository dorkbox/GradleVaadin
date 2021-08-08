/*
 * Copyright 2021 dorkbox, llc
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

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.*
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.util.GradleVersion
import java.io.File
import java.net.URL

open class
PrepJarsTask : DefaultTask() {
    companion object {

    }

    @OutputFiles
    val allLibrariesRev = mutableMapOf<File, String>()

    init {
        group = "vaadin"
        description = "Prepares and checks the libraries used by all projects."

        outputs.cacheIf { false }
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun run() {
        val librariesByFileName = mutableMapOf<String, File>()
        synchronized(allLibrariesRev) {
            // make sure all projects and subprojects are considered
            project.allprojects.forEach { subProject ->
                val resolveAllDependencies = Vaadin.resolveAllDependencies(subProject).flatMap { it.artifacts }
                resolveAllDependencies.forEach { artifact ->
                    val file = artifact.file
                    var fileName = file.name
                    var firstNumCheck = 0

                    while (librariesByFileName.containsKey(fileName)) {
                        // whoops! this is not good! Rename the file so it will be included. THIS PROBLEM ACTUALLY EXISTS, and is by accident!
                        // if the target FILE is the same file (as the filename) then it's OK for this to be a duplicate
                        if (file != librariesByFileName[fileName]) {
                            fileName = "${file.nameWithoutExtension}_DUP_${firstNumCheck++}.${file.extension}"
                        } else {
                            // the file name and path are the same, meaning this is just a duplicate library
                            // instead of a DIFFERENT library with the same library file name.
                            break
                        }
                    }

                    if (firstNumCheck != 0) {
                        println("\tTarget file exists already! Renaming to $fileName")
                    }

                    // println("adding: " + file)
                    librariesByFileName[fileName] = file
                    allLibrariesRev[file] = fileName
                }
            }
        }
    }

    // get all jars needed on the library classpath, for RUNTIME (this is placed in the jar manifest)
    // NOTE: This must be referenced via a TASK, otherwise it will not work.
    fun getJarLibraryClasspath(project: Project): String {
        val libraries = mutableMapOf<String, File>()

        val resolveAllDependencies = Vaadin.resolveRuntimeDependencies(project).dependencies
        synchronized(allLibrariesRev) { // we must synchronize on it for thread safety
            resolveAllDependencies.forEach { dep ->
                dep.artifacts.forEach { artifact ->
                    val file = artifact.file

                    // get the file info from the reverse lookup, because we might have mangled the filename!
                    val cacheFileName = allLibrariesRev[file]!!
                    libraries[cacheFileName] = file
                }
            }
        }

        return libraries.keys.sorted().joinToString(prefix = "lib/", separator = " lib/", postfix = "\r\n")
    }

    fun copyLibrariesTo(targetDir: File) {
        synchronized(allLibrariesRev) {
            allLibrariesRev.forEach { (file, fileName) ->
                // we don't overwrite the file if it already exists
                val destFile = File(targetDir, fileName)
                if (!destFile.exists()) {
                    file.copyTo(destFile)
                }
            }
        }
    }
}
