package io.koraframework.http.client.symbol.processor

import io.koraframework.config.common.mapper.*
import io.koraframework.config.common.util.ConfigMappingUtils
import io.koraframework.config.ksp.processor.ConfigParserSymbolProcessorProvider
import io.koraframework.config.ksp.processor.ConfigSourceSymbolProcessorProvider
import io.koraframework.http.client.common.HttpClient
import io.koraframework.http.client.common.declarative.*
import io.koraframework.http.client.common.request.HttpClientRequest
import io.koraframework.http.client.common.response.HttpClientResponse
import io.koraframework.http.client.common.telemetry.*
import io.koraframework.http.common.body.HttpBody
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import org.intellij.lang.annotations.Language
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever


abstract class AbstractHttpClientTest : AbstractSymbolProcessorTest() {
    val httpResponse = mock<HttpClientResponse>().also {
        whenever(it.code()).thenReturn(200)
        whenever(it.body()).thenReturn(HttpBody.octetStream(ByteArray(0)))
    }
    val httpClient = mock<HttpClient>().also {
        whenever(it.execute(any())).thenReturn(httpResponse)
    }
    val telemetryFactory = mock<HttpClientTelemetryFactory>()
    lateinit var client: TestObject

    protected fun onRequest(method: String, path: String, responseConsumer: (TestHttpClientResponse) -> TestHttpClientResponse) {
        whenever(httpClient.execute(Mockito.argThat { arg: HttpClientRequest -> arg.method().equals(method, ignoreCase = true) && arg.uri().toString() == path }))
            .thenAnswer { invocation ->
                invocation.getArgument(0, HttpClientRequest::class.java).body().close()
                responseConsumer(TestHttpClientResponse(code = 200))
            }
    }


    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.http.client.common.annotation.*
            import io.koraframework.http.client.common.request.*
            import io.koraframework.http.client.common.response.*
            import io.koraframework.http.client.common.*
            import io.koraframework.http.common.annotation.*
            import io.koraframework.http.client.common.annotation.HttpClient
            import io.koraframework.http.common.header.HttpHeaders
            import java.util.concurrent.CompletionStage
            import java.util.concurrent.CompletableFuture
            import io.koraframework.http.client.common.interceptor.HttpClientInterceptor
            import io.koraframework.http.client.common.interceptor.HttpClientInterceptor.InterceptChain

            """.trimIndent()
    }

    protected fun compile(arguments: List<Any?>, @Language("kotlin") vararg sources: String): TestObject {
        compile0(
            listOf(
                HttpClientSymbolProcessorProvider(),
                ConfigSourceSymbolProcessorProvider(),
                ConfigParserSymbolProcessorProvider()
            ), *sources
        )
            .assertSuccess()

        val clientClass = loadClass("\$TestClient_ClientImpl")
        val durationCVE = DurationConfigValueMapper()
        val telemetryCVE = `$HttpClientTelemetryConfig_ConfigValueMapper`(
            `$HttpClientTelemetryConfig_HttpClientLoggingConfig_ConfigValueMapper`(
                SetConfigValueMapper(
                    StringConfigValueMapper()
                ), SizeConfigValueMapper()
            ),
            `$HttpClientTelemetryConfig_HttpClientMetricsConfig_ConfigValueMapper`(
                DurationArrayConfigValueMapper(DurationConfigValueMapper()),
                MapConfigValueMapper(StringConfigValueMapper())
            ),
            `$HttpClientTelemetryConfig_HttpClientTracingConfig_ConfigValueMapper`(
                MapConfigValueMapper(
                    StringConfigValueMapper()
                )
            )
        )
        val operationTelemetryCVE = `$HttpClientOperationConfig_OperationTelemetryConfig_ConfigValueMapper`(
            `$HttpClientOperationConfig_OperationTelemetryConfig_LoggingConfig_ConfigValueMapper`(
                SetConfigValueMapper(StringConfigValueMapper()),
                SizeConfigValueMapper()
            ),
            `$HttpClientOperationConfig_OperationTelemetryConfig_TracingConfig_ConfigValueMapper`(
                MapConfigValueMapper(StringConfigValueMapper())
            ),
            `$HttpClientOperationConfig_OperationTelemetryConfig_MetricsConfig_ConfigValueMapper`(
                DurationArrayConfigValueMapper(DurationConfigValueMapper()),
                MapConfigValueMapper(StringConfigValueMapper())
            )
        )
        val operationConfigCVE = `$HttpClientOperationConfig_ConfigValueMapper`(durationCVE, operationTelemetryCVE)

        val configValueMapper = new("\$TestClient_Config_ConfigValueMapper", telemetryCVE, operationConfigCVE, durationCVE) as ConfigValueMapper<*>
        val config = configValueMapper.map(
            ConfigMappingUtils.fromMap(
                mapOf(
                    "url" to "http://test-url:8080"
                )
            ).root()
        )


        val realArgs = arrayOfNulls<Any>(arguments.size + 3)
        realArgs[0] = httpClient
        realArgs[1] = config
        realArgs[2] = telemetryFactory
        System.arraycopy(arguments.toTypedArray(), 0, realArgs, 3, arguments.size)
        for ((i, value) in realArgs.withIndex()) {
            if (value is GeneratedObject<*>) {
                realArgs[i] = value.invoke()
            }
        }
        val instance = clientClass.constructors[0].newInstance(*realArgs)
        client = TestObject(clientClass.kotlin, instance)
        return client
    }
}
