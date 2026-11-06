package io.koraframework.soap.client.symbol.processor

import org.junit.jupiter.api.Test
import io.koraframework.ksp.common.KotlinCompilation
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.io.path.walk

class WebServiceClientSymbolProcessorTest {

    @Test
    fun testGenerate() {
        compileKotlin("build/generated/wsdl-jakarta-simple-service/")
        compileKotlin("build/generated/wsdl-jakarta-service-with-multipart-response/")
        compileKotlin("build/generated/wsdl-jakarta-service-with-rpc/")
    }

    @Test
    fun testOperationNameIsNotKotlinIdentifier() {
        compileKotlin("build/generated/wsdl-jakarta-service-with-hyphenated-operation/")
    }

    @Test
    fun testWrappedOperationWithHolders() {
        compileKotlin("build/generated/wsdl-jakarta-service-with-holders/")
    }

    @Test
    fun testBareOperationWithSoapHeader() {
        compileKotlin("build/generated/wsdl-jakarta-service-with-soap-header/")
    }

    private fun compileKotlin(targetDir: String) {
        val javaFiles = Paths.get(targetDir)
            .walk()
            .filter { it.name.endsWith(".java") }
            .toList()
        KotlinCompilation()
            .withProcessor(WebServiceClientSymbolProcessorProvider())
            .withJavaSrcs(javaFiles)
            .compile()
    }

}
