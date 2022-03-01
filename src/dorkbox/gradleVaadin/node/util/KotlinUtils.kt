package dorkbox.gradleVaadin.node.util

/**
 * Transforms the receiver with the given [transform] if the provided [predicate] evaluates to `true`. Otherwise, the receiver is returned.
 */
internal inline fun <T : Any?> T.mapIf(predicate: (T) -> Boolean, transform: (T) -> T): T = if (predicate(this)) transform(this) else this
