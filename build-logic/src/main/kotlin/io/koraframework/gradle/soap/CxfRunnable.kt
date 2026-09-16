package io.koraframework.gradle.soap

import org.gradle.workers.WorkAction

abstract class CxfRunnable : WorkAction<CxfWorkParameters> {
    override fun execute() {
        val wsdlToJavaClass = Class.forName("org.apache.cxf.tools.wsdlto.WSDLToJava")
        val constructor = wsdlToJavaClass.getConstructor(Array<String>::class.java)

        val toolContextClass = Class.forName("org.apache.cxf.tools.common.ToolContext")
        val runMethod = wsdlToJavaClass.getMethod("run", toolContextClass)

        val busFactoryClass = Class.forName("org.apache.cxf.BusFactory")
        val setThreadDefaultBusMethod = busFactoryClass.getMethod("setThreadDefaultBus", Class.forName("org.apache.cxf.Bus"))

        val originalClassLoader = Thread.currentThread().contextClassLoader
        val originalLoaderProperty = System.getProperty("org.apache.velocity.resource.loader.class.class")
        val originalExitProperty = System.getProperty("exitOnFinish")

        try {
            Thread.currentThread().contextClassLoader = wsdlToJavaClass.classLoader

            System.setProperty(
                "org.apache.velocity.resource.loader.class.class",
                "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader"
            )

            System.setProperty("exitOnFinish", "false")

            val argsArray = parameters.args.get().toTypedArray()
            val w2jInstance = constructor.newInstance(argsArray)
            val toolContextInstance = toolContextClass.getDeclaredConstructor().newInstance()

            runMethod.invoke(w2jInstance, toolContextInstance)
        } finally {
            try {
                setThreadDefaultBusMethod.invoke(null, null)
            } catch (_: Exception) {
            }

            Thread.currentThread().contextClassLoader = originalClassLoader

            if (originalLoaderProperty != null) {
                System.setProperty("org.apache.velocity.resource.loader.class.class", originalLoaderProperty)
            } else {
                System.clearProperty("org.apache.velocity.resource.loader.class.class")
            }

            if (originalExitProperty != null) {
                System.setProperty("exitOnFinish", originalExitProperty)
            } else {
                System.clearProperty("exitOnFinish")
            }
        }
    }
}
