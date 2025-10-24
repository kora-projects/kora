package ru.tinkoff.kora.soap.client.symbol.processor

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.ksp.common.KotlinCompilation
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.io.path.walk

class WebServiceClientSymbolProcessorTest {

    @Test
    fun testGenerate() {
        compileKotlin("build/generated/wsdl-jakarta-simple-service/")
        compileKotlin("build/generated/wsdl-javax-simple-service/")
        compileKotlin("build/generated/wsdl-jakarta-service-with-multipart-response/")
        compileKotlin("build/generated/wsdl-javax-service-with-multipart-response/")
        compileKotlin("build/generated/wsdl-jakarta-service-with-rpc/")
        compileKotlin("build/generated/wsdl-javax-service-with-rpc/")
    }

    private fun compileKotlin(targetDir: String) {
        val javaFiles = Paths.get(targetDir)
            .walk()
            .filter { it.name.endsWith(".java") }
            .toList()
        KotlinCompilation()
            .withPartialClasspath()
            .withProcessor(WebServiceClientSymbolProcessorProvider())
            .withJavaSrcs(javaFiles)
            .compile()
    }

}
