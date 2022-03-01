package dorkbox.gradleVaadin.node.util

import com.dorkbox.version.Version
import com.vaadin.flow.server.frontend.FrontendUtils
import com.vaadin.flow.server.frontend.FrontendVersion
import dorkbox.executor.Executor
import java.io.IOException
import java.util.*
import java.util.stream.Stream

open class PlatformHelper constructor(private val props: Properties = System.getProperties()) {
    open val osName: String by lazy {
        val name = property("os.name").lowercase(Locale.getDefault())
        when {
            name.contains("windows") -> "win"
            name.contains("mac") -> "darwin"
            name.contains("linux") -> "linux"
            name.contains("freebsd") -> "linux"
            name.contains("sunos") -> "sunos"
            else -> error("Unsupported OS: $name")
        }
    }

    open val osArch: String by lazy {
        val arch = property("os.arch").lowercase(Locale.getDefault())
        when {
            /*
             * As Java just returns "arm" on all ARM variants, we need a system call to determine the exact arch. Unfortunately some JVMs say aarch32/64, so we need an additional
             * conditional. Additionally, the node binaries for 'armv8l' are called 'arm64', so we need to distinguish here.
             */
            arch == "arm" || arch.startsWith("aarch") -> property("uname").mapIf({ it == "armv8l" || it == "aarch64" }) { "arm64" }
            arch == "ppc64le" -> "ppc64le"
            arch.contains("64") -> "x64"
            else -> "x86"
        }
    }

    open val isWindows: Boolean by lazy { osName == "win" }

    private fun property(name: String): String {
        val value = props.getProperty(name)
        return value ?: System.getProperty(name) ?:
        // Added so that we can test osArch on Windows and on non-arm systems
        if (name == "uname") Executor.run("uname", "-m")
        else error("Unable to find a value for property [$name].")
    }

    companion object {
        var INSTANCE = PlatformHelper()
        val regex = "\n".toRegex()
        val regex1 = "^[ ]*$".toRegex()

        @Throws(IOException::class)
        fun parseVersionString(output: String): String {
            val lastOutput = Stream.of(*output.split(regex).toTypedArray())
                .filter { line: String -> !line.matches(regex1) }
                .reduce { _: String, second: String -> second }

            return lastOutput
                .map { line: String ->
                    var trimmed = line
                    while (trimmed.isNotEmpty() && !trimmed.first().isDigit()) {
                        trimmed = trimmed.removeRange(0, 1)
                    }

                    trimmed.trim()
                }
                .orElseThrow { IOException("No output") }
        }

        fun validateToolVersion(tool: String, toolVersion: Version, supported: Version, silent: Boolean): Boolean {
            if ("true".equals(System.getProperty(FrontendUtils.PARAM_IGNORE_VERSION_CHECKS), ignoreCase = true)) {
                return true
            }
            if (toolVersion.greaterThanOrEqualTo(supported)) {
                return true
            }

            if (silent) {
                return false
            }

            throw IllegalStateException(
                buildTooOldString(
                    tool, toolVersion.toString(),
                    supported.majorVersion.toInt(),
                    supported.minorVersion.toInt()
                )
            )
        }

        fun isVersionAtLeast(toolVersion: FrontendVersion, required: FrontendVersion): Boolean {
            val major = toolVersion.majorVersion
            val minor = toolVersion.minorVersion
            return (major > required.majorVersion || major == required.majorVersion && minor >= required.minorVersion)
        }

        private fun buildTooOldString(tool: String, version: String,supportedMajor: Int, supportedMinor: Int): String {
            return String.format(
                TOO_OLD, tool, version, supportedMajor, supportedMinor,
                FrontendUtils.PARAM_IGNORE_VERSION_CHECKS
            )
        }

        private const val TOO_OLD =
            ("%n%n======================================================================================================"
                    + "%nYour installed '%s' version (%s) is too old. Supported versions are %d.%d+" //
                    + "%nPlease install a new one either:"
                    + "%n  - by following the https://nodejs.org/en/download/ guide to install it globally"
                    + "%n  - or by running the frontend-maven-plugin goal to install it in this project:"
                    + FrontendUtils.INSTALL_NODE_LOCALLY
                    + "%n" //
                    + FrontendUtils.DISABLE_CHECK //
                    + "%n======================================================================================================%n")
    }
}
