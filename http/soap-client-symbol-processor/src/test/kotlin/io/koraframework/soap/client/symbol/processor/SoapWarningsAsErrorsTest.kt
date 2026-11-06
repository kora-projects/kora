package io.koraframework.soap.client.symbol.processor

import io.koraframework.ksp.common.KotlinCompilation
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.io.path.walk

class SoapWarningsAsErrorsTest {

    @ParameterizedTest
    @ValueSource(strings = [
        "build/generated/wsdl-jakarta-simple-service/",
        "build/generated/wsdl-jakarta-service-with-multipart-response/",
        "build/generated/wsdl-jakarta-service-with-rpc/",
        "build/generated/wsdl-jakarta-service-with-hyphenated-operation/",
        "build/generated/wsdl-jakarta-service-with-holders/",
        "build/generated/wsdl-jakarta-service-with-soap-header/",
        "build/generated/wsdl-jakarta-service-with-inout-header/",
    ])
    fun generatedClientCompilesWithAllWarningsAsErrors(targetDir: String) {
        val javaFiles = Paths.get(targetDir).walk().filter { it.name.endsWith(".java") }.toList()
        KotlinCompilation()
            .withProcessor(WebServiceClientSymbolProcessorProvider())
            .withJavaSrcs(javaFiles)
            .apply { allWarningsAsErrors = true }
            .compile()
    }
}
