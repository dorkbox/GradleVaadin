package dorkbox.gradleVaadin

import org.gradle.api.Project
import org.gradle.process.ExecResult
import java.io.ByteArrayOutputStream
import java.io.File

class Exec(private val proj: Project) {
    lateinit var path: String
    lateinit var executable: String
    lateinit var workingDir: File
    lateinit var arguments: List<String>

    var environment = System.getenv().toMutableMap()

    var ignoreExitValue: Boolean = true
    var suppressOutput: Boolean = true

    var debug: Boolean = false


    fun execute(): ExecResult {
        val self = this
        return proj.exec {
            val exec = this
            exec.executable = self.executable

            exec.args = self.arguments

            // Take care of Windows environments that may contain "Path" OR "PATH" - both existing
            // possibly (but not in parallel as of now)
            if (self.environment["Path"] != null) {
                self.environment["Path"] = self.path + File.pathSeparator + self.environment["Path"]
            } else {
                self.environment["PATH"] = self.path + File.pathSeparator + self.environment["PATH"]
            }

            @Suppress("UNCHECKED_CAST")
            exec.environment = self.environment as MutableMap<String, Any>

            exec.isIgnoreExitValue = self.ignoreExitValue
            exec.workingDir = self.workingDir

            if (!exec.workingDir.exists()) {
                exec.workingDir.mkdirs()
            }

            if (suppressOutput) {
                exec.standardOutput = ByteArrayOutputStream()
                exec.errorOutput = ByteArrayOutputStream()
            }

            if (debug) {
                println("\t\tExec: ${exec.executable}")
                println("\t\tWorking Dir: ${exec.workingDir}")
                println("\t\tSuppressing output: $suppressOutput")

                println("\t\tArguments:")
                exec.args.forEach {
                    println("\t\t\t$it")
                }
            }
        }
    }
}
