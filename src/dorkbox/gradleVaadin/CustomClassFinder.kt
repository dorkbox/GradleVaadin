package dorkbox.gradleVaadin

import java.io.File
import java.net.URL

class CustomClassFinder(classPath: List<File>) : com.vaadin.flow.server.frontend.scanner.ClassFinder {
    // Find everything annotated with the following
    //  Route.class, NpmPackage.class, NpmPackage.Container.class, WebComponentExporter.class, UIInitListener.class, VaadinServiceInitListener.class

    // scans compiled files (NOT SOURCE FILES!)
    // make sure to close this!
    private val classPathScanResult = io.github.classgraph.ClassGraph()
            .overrideClasspath(classPath)
//            .enableSystemJarsAndModules()
            .enableInterClassDependencies()
            .enableExternalClasses()
            .enableAllInfo()
            .scan()


    override fun <T : Any?> loadClass(name: String): Class<T>? {
        // println("load class: $name")
        return loadClass(name, classPathScanResult)
    }

    private fun <T : Any?> loadClass(name: String, scanResult: io.github.classgraph.ScanResult): Class<T>? {
        @Suppress("UNCHECKED_CAST")
        return scanResult.loadClass(name, true) as Class<T>?
    }

    override fun <T : Any?> getSubTypesOf(type: Class<T>): MutableSet<Class<out T>> {
        // println("get subtype of: $type")

        val classes = mutableSetOf<Class<out T>>()
        getSubTypesOf(type, classPathScanResult, classes)

        return classes
    }

    fun <T : Any?> getSubTypesOf(type: Class<T>, scanResult: io.github.classgraph.ScanResult, classes: MutableSet<Class<out T>>) {
        //  only those in direct relationship, not subclasses of THOSE subclasses
        scanResult.getSubclasses(type.canonicalName).forEach {
            // load this class into the current classloader
            @Suppress("UNCHECKED_CAST")
            classes.add(it.loadClass() as Class<out T>)
        }
    }

    override fun getResource(name: String): URL? {
//         println("Get resource $name")

        val results = getResource(name, classPathScanResult)

        return if (results.isEmpty()) {
            Thread.currentThread().contextClassLoader.getResource(name)
        } else {
            results.firstOrNull()?.url
        }
    }

    private fun getResource(name: String, scanResult: io.github.classgraph.ScanResult): io.github.classgraph.ResourceList {
        var results = scanResult.getResourcesWithPath(name)
        if (results == null || results.isEmpty()) {
            results = scanResult.getResourcesWithLeafName(name)
        }

        return results
    }

    override fun getAnnotatedClasses(clazz: Class<out Annotation>): MutableSet<Class<*>> {
        // println("getting classes for annotation: '$clazz'")

        val classes = mutableSetOf<Class<*>>()
        getAnnotatedClasses(clazz, classPathScanResult, classes)

        return classes
    }

    private fun getAnnotatedClasses(clazz: Class<out Annotation>, scanResult: io.github.classgraph.ScanResult, classes: MutableSet<Class<*>>) {
//        println("\tSearching....")

        scanResult.getClassesWithAnnotation(clazz.name).forEach {
//            println("\t\t${it.name}")

            // load this class into the current classloader
            classes.add(it.loadClass())
        }
    }

    /**
     * Call this when done!
     */
    fun finish() {
        classPathScanResult.close()
    }
}
