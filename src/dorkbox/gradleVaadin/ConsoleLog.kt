package dorkbox.gradleVaadin

import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.helpers.FormattingTuple
import org.slf4j.helpers.MessageFormatter
import java.io.PrintStream

/**
 *
 */
class ConsoleLog(val name_: String = "",
                 val currentLogLevel: Int = Level.TRACE,
                 val targetStream: PrintStream = System.out,
                 val messagePreface: String = ""): Logger {
    object Level {
        // these constants should be in EventConstants. However, in order to preserve binary backward compatibility
        // we keep these constants here. {@link EventConstants} redefines these constants using the values  below.
        val TRACE = 0
        val DEBUG = 10
        val INFO = 20
        val WARN = 30
        val ERROR = 40
    }

    /**
     * Is the given log level currently enabled?
     *
     * @param logLevel is this level enabled?
     * @return whether the logger is enabled for the given level
     */
    protected fun isLevelEnabled(logLevel: Int): Boolean {
        // log level are numerically ordered so can use simple numeric
        // comparison
        return logLevel >= currentLogLevel
    }

    /** Are `trace` messages currently enabled?  */
    override fun isTraceEnabled(): Boolean {
        return isLevelEnabled(Level.TRACE)
    }

    /** Are `debug` messages currently enabled?  */
    override fun isDebugEnabled(): Boolean {
        return isLevelEnabled(Level.DEBUG)
    }

    /** Are `info` messages currently enabled?  */
    override fun isInfoEnabled(): Boolean {
        return isLevelEnabled(Level.INFO)
    }

    /** Are `warn` messages currently enabled?  */
    override fun isWarnEnabled(): Boolean {
        return isLevelEnabled(Level.WARN)
    }

    /** Are `error` messages currently enabled?  */
    override fun isErrorEnabled(): Boolean {
        return isLevelEnabled(Level.ERROR)
    }

    override fun getName(): String {
        return name_
    }

    override fun trace(msg: String) {
        if (isTraceEnabled) {
            handle_0ArgsCall(Level.TRACE, null, msg, null)
        }
    }

    override fun trace(format: String, arg: Any) {
        if (isTraceEnabled) {
            handle_1ArgsCall(Level.TRACE, null, format, arg)
        }
    }

    override fun trace(format: String, arg1: Any, arg2: Any) {
        if (isTraceEnabled) {
            handle2ArgsCall(Level.TRACE, null, format, arg1, arg2)
        }
    }

    override fun trace(format: String, vararg arguments: Any) {
        if (isTraceEnabled) {
            handleArgArrayCall(Level.TRACE, null, format, arguments as Array<Any>)
        }
    }

    override fun trace(msg: String, t: Throwable?) {
        if (isTraceEnabled) {
            handle_0ArgsCall(Level.TRACE, null, msg, t)
        }
    }

    override fun trace(marker: Marker?, msg: String) {
        if (isTraceEnabled(marker)) {
            handle_0ArgsCall(Level.TRACE, marker, msg, null)
        }
    }

    override fun trace(marker: Marker?, format: String, arg: Any) {
        if (isTraceEnabled(marker)) {
            handle_1ArgsCall(Level.TRACE, marker, format, arg)
        }
    }

    override fun trace(marker: Marker?, format: String, arg1: Any, arg2: Any) {
        if (isTraceEnabled(marker)) {
            handle2ArgsCall(Level.TRACE, marker, format, arg1, arg2)
        }
    }

    override fun trace(marker: Marker?, format: String, vararg argArray: Any) {
        if (isTraceEnabled(marker)) {
            handleArgArrayCall(Level.TRACE, marker, format, argArray as Array<Any>)
        }
    }

    override fun trace(marker: Marker?, msg: String, t: Throwable?) {
        if (isTraceEnabled(marker)) {
            handle_0ArgsCall(Level.TRACE, marker, msg, t)
        }
    }

    override fun debug(msg: String) {
        if (isDebugEnabled) {
            handle_0ArgsCall(Level.DEBUG, null, msg, null)
        }
    }

    override fun debug(format: String, arg: Any) {
        if (isDebugEnabled) {
            handle_1ArgsCall(Level.DEBUG, null, format, arg)
        }
    }

    override fun debug(format: String, arg1: Any, arg2: Any) {
        if (isDebugEnabled) {
            handle2ArgsCall(Level.DEBUG, null, format, arg1, arg2)
        }
    }

    override fun debug(format: String, vararg arguments: Any) {
        if (isDebugEnabled) {
            handleArgArrayCall(Level.DEBUG, null, format, arguments as Array<Any>)
        }
    }

    override fun debug(msg: String, t: Throwable?) {
        if (isDebugEnabled) {
            handle_0ArgsCall(Level.DEBUG, null, msg, t)
        }
    }

    override fun debug(marker: Marker?, msg: String) {
        if (isDebugEnabled(marker)) {
            handle_0ArgsCall(Level.DEBUG, marker, msg, null)
        }
    }

    override fun debug(marker: Marker?, format: String, arg: Any) {
        if (isDebugEnabled(marker)) {
            handle_1ArgsCall(Level.DEBUG, marker, format, arg)
        }
    }

    override fun debug(marker: Marker?, format: String, arg1: Any, arg2: Any) {
        if (isDebugEnabled(marker)) {
            handle2ArgsCall(Level.DEBUG, marker, format, arg1, arg2)
        }
    }

    override fun debug(marker: Marker?, format: String, vararg arguments: Any) {
        if (isDebugEnabled(marker)) {
            handleArgArrayCall(Level.DEBUG, marker, format, arguments as Array<Any>)
        }
    }

    override fun debug(marker: Marker?, msg: String, t: Throwable?) {
        if (isDebugEnabled(marker)) {
            handle_0ArgsCall(Level.DEBUG, marker, msg, t)
        }
    }

    override fun info(msg: String) {
        if (isInfoEnabled) {
            handle_0ArgsCall(Level.INFO, null, msg, null)
        }
    }

    override fun info(format: String, arg: Any) {
        if (isInfoEnabled) {
            handle_1ArgsCall(Level.INFO, null, format, arg)
        }
    }

    override fun info(format: String, arg1: Any, arg2: Any) {
        if (isInfoEnabled) {
            handle2ArgsCall(Level.INFO, null, format, arg1, arg2)
        }
    }

    override fun info(format: String, vararg arguments: Any) {
        if (isInfoEnabled) {
            handleArgArrayCall(Level.INFO, null, format, arguments as Array<Any>)
        }
    }

    override fun info(msg: String, t: Throwable?) {
        if (isInfoEnabled) {
            handle_0ArgsCall(Level.INFO, null, msg, t)
        }
    }

    override fun info(marker: Marker?, msg: String) {
        if (isInfoEnabled(marker)) {
            handle_0ArgsCall(Level.INFO, marker, msg, null)
        }
    }

    override fun info(marker: Marker?, format: String, arg: Any) {
        if (isInfoEnabled(marker)) {
            handle_1ArgsCall(Level.INFO, marker, format, arg)
        }
    }

    override fun info(marker: Marker?, format: String, arg1: Any, arg2: Any) {
        if (isInfoEnabled(marker)) {
            handle2ArgsCall(Level.INFO, marker, format, arg1, arg2)
        }
    }

    override fun info(marker: Marker?, format: String, vararg arguments: Any) {
        if (isInfoEnabled(marker)) {
            handleArgArrayCall(Level.INFO, marker, format, arguments as Array<Any>)
        }
    }

    override fun info(marker: Marker?, msg: String, t: Throwable?) {
        if (isInfoEnabled(marker)) {
            handle_0ArgsCall(Level.INFO, marker, msg, t)
        }
    }

    override fun warn(msg: String) {
        if (isWarnEnabled) {
            handle_0ArgsCall(Level.WARN, null, msg, null)
        }
    }

    override fun warn(format: String, arg: Any) {
        if (isWarnEnabled) {
            handle_1ArgsCall(Level.WARN, null, format, arg)
        }
    }

    override fun warn(format: String, arg1: Any, arg2: Any) {
        if (isWarnEnabled) {
            handle2ArgsCall(Level.WARN, null, format, arg1, arg2)
        }
    }

    override fun warn(format: String, vararg arguments: Any) {
        if (isWarnEnabled) {
            handleArgArrayCall(Level.WARN, null, format, arguments as Array<Any>)
        }
    }

    override fun warn(msg: String, t: Throwable?) {
        if (isWarnEnabled) {
            handle_0ArgsCall(Level.WARN, null, msg, t)
        }
    }

    override fun warn(marker: Marker?, msg: String) {
        if (isWarnEnabled(marker)) {
            handle_0ArgsCall(Level.WARN, marker, msg, null)
        }
    }

    override fun warn(marker: Marker?, format: String, arg: Any) {
        if (isWarnEnabled(marker)) {
            handle_1ArgsCall(Level.WARN, marker, format, arg)
        }
    }

    override fun warn(marker: Marker?, format: String, arg1: Any, arg2: Any) {
        if (isWarnEnabled(marker)) {
            handle2ArgsCall(Level.WARN, marker, format, arg1, arg2)
        }
    }

    override fun warn(marker: Marker?, format: String, vararg arguments: Any) {
        if (isWarnEnabled(marker)) {
            handleArgArrayCall(Level.WARN, marker, format, arguments as Array<Any>)
        }
    }

    override fun warn(marker: Marker?, msg: String, t: Throwable?) {
        if (isWarnEnabled(marker)) {
            handle_0ArgsCall(Level.WARN, marker, msg, t)
        }
    }

    override fun error(msg: String) {
        if (isErrorEnabled) {
            handle_0ArgsCall(Level.ERROR, null, msg, null)
        }
    }

    override fun error(format: String, arg: Any) {
        if (isErrorEnabled) {
            handle_1ArgsCall(Level.ERROR, null, format, arg)
        }
    }

    override fun error(format: String, arg1: Any, arg2: Any) {
        if (isErrorEnabled) {
            handle2ArgsCall(Level.ERROR, null, format, arg1, arg2)
        }
    }

    override fun error(format: String, vararg arguments: Any) {
        if (isErrorEnabled) {
            handleArgArrayCall(Level.ERROR, null, format, arguments as Array<Any>)
        }
    }

    override fun error(msg: String, t: Throwable?) {
        if (isErrorEnabled) {
            handle_0ArgsCall(Level.ERROR, null, msg, t)
        }
    }

    override fun error(marker: Marker?, msg: String) {
        if (isErrorEnabled(marker)) {
            handle_0ArgsCall(Level.ERROR, marker, msg, null)
        }
    }

    override fun error(marker: Marker?, format: String, arg: Any) {
        if (isErrorEnabled(marker)) {
            handle_1ArgsCall(Level.ERROR, marker, format, arg)
        }
    }

    override fun error(marker: Marker?, format: String, arg1: Any, arg2: Any) {
        if (isErrorEnabled(marker)) {
            handle2ArgsCall(Level.ERROR, marker, format, arg1, arg2)
        }
    }

    override fun error(marker: Marker?, format: String, vararg arguments: Any) {
        if (isErrorEnabled(marker)) {
            handleArgArrayCall(Level.ERROR, marker, format, arguments as Array<Any>)
        }
    }

    override fun error(marker: Marker?, msg: String, t: Throwable?) {
        if (isErrorEnabled(marker)) {
            handle_0ArgsCall(Level.ERROR, marker, msg, t)
        }
    }

    private fun handle_0ArgsCall(level: Int, marker: Marker?, msg: String, t: Throwable?) {
        handleNormalizedLoggingCall(level, marker, msg, null, t)
    }

    private fun handle_1ArgsCall(level: Int, marker: Marker?, msg: String, arg1: Any) {
        handleNormalizedLoggingCall(level, marker, msg, arrayOf(arg1), null)
    }

    private fun handle2ArgsCall(level: Int, marker: Marker?, msg: String, arg1: Any, arg2: Any) {
        if (arg2 is Throwable) {
            handleNormalizedLoggingCall(level, marker, msg, arrayOf(arg1), arg2 as Throwable)
        } else {
            handleNormalizedLoggingCall(level, marker, msg, arrayOf(arg1, arg2), null)
        }
    }

    private fun handleArgArrayCall(level: Int, marker: Marker?, msg: String, args: Array<Any>) {
        val throwableCandidate: Throwable? = MessageFormatter.getThrowableCandidate(args)
        if (throwableCandidate != null) {
            val trimmedCopy: Array<Any> = MessageFormatter.trimmedCopy(args)
            handleNormalizedLoggingCall(level, marker, msg, trimmedCopy, throwableCandidate)
        } else {
            handleNormalizedLoggingCall(level, marker, msg, args, null)
        }
    }

    override fun isTraceEnabled(marker: Marker?): Boolean {
        return isTraceEnabled
    }

    override fun isDebugEnabled(marker: Marker?): Boolean {
        return isDebugEnabled
    }

    override fun isInfoEnabled(marker: Marker?): Boolean {
        return isInfoEnabled
    }

    override fun isWarnEnabled(marker: Marker?): Boolean {
        return isWarnEnabled
    }

    override fun isErrorEnabled(marker: Marker?): Boolean {
        return isErrorEnabled
    }


    /**
     * This is our internal implementation for logging regular (non-parameterized)
     * log messages.
     *
     * @param level   One of the LOG_LEVEL_XXX constants defining the log level
     * @param message The message itself
     * @param t       The exception whose stack trace should be logged
     */
    protected fun handleNormalizedLoggingCall(
        level: Int,
        marker: Marker?,
        messagePattern: String,
        arguments: Array<Any>?,
        t: Throwable?
    ) {
        var markers: MutableList<Marker>? = null

        if (marker != null) {
            markers = ArrayList()
            markers.add(marker)
        }

        innerHandleNormalizedLoggingCall(level, markers, messagePattern, arguments, t)
    }

    val CONFIG_PARAMS = object: Any() {}

    /**
     * To avoid intermingling of log messages and associated stack traces, the two
     * operations are done in a synchronized block.
     *
     * @param buf
     * @param t
     */
    fun write(buf: java.lang.StringBuilder, t: Throwable?) {
        synchronized(CONFIG_PARAMS) {
            targetStream.println(buf.toString())
            writeThrowable(t, targetStream)
            targetStream.flush()
        }
    }

    protected fun writeThrowable(t: Throwable?, targetStream: PrintStream?) {
        t?.printStackTrace(targetStream)
    }

    private fun computeShortName(): String? {
        return name!!.substring(name!!.lastIndexOf(".") + 1)
    }

    var shortLogName: String? = null

    val START_TIME = System.currentTimeMillis()

    private fun innerHandleNormalizedLoggingCall(
        level: Int,
        markers: List<Marker>?,
        messagePattern: String,
        arguments: Array<Any>?,
        t: Throwable?
    ) {
        val buf = StringBuilder(32)
        buf.append(messagePreface)

//        buf.append(System.currentTimeMillis() - START_TIME)
//        buf.append(' ')

        //                // Append current thread name if so configured
        //                if (CONFIG_PARAMS.showThreadName) {
        //                    buf.append('[')
        //                    buf.append(Thread.currentThread().name)
        //                    buf.append("] ")
        //                }
        //                if (CONFIG_PARAMS.levelInBrackets) buf.append('[')

        // Append a readable representation of the log level
        //                val levelStr: String = level.name()
        //                buf.append(levelStr)
        //                if (CONFIG_PARAMS.levelInBrackets) buf.append(']')
        //                buf.append(' ')

        // Append the name of the log instance if so configured
        //                if (CONFIG_PARAMS.showShortLogName) {
//        if (shortLogName == null) shortLogName = computeShortName()
//        buf.append(java.lang.String.valueOf(shortLogName)).append(" - ")
        //                } else if (CONFIG_PARAMS.showLogName) {
        //                    buf.append(java.lang.String.valueOf(name)).append(" - ")
        //                }

        //                if (markers != null) {
        //                    buf.append(SP)
        //                    for (marker in markers) {
        //                        buf.append(marker.name).append(SP)
        //                    }
        //                }

        val tp: FormattingTuple = MessageFormatter.arrayFormat(messagePattern, arguments)

        //                val formattedMessage: String = MessageFormatter.basicArrayFormat(messagePattern, arguments)

        // Append the message
        buf.append(tp.message)
        write(buf, t)
    }
}
