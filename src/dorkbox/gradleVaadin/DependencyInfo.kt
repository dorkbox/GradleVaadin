package dorkbox.gradleVaadin

import java.io.File

data class DependencyInfo(val group: String, val name: String, val version: String, val file: File)
