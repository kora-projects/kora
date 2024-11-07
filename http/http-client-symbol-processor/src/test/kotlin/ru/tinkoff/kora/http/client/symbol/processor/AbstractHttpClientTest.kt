package ru.tinkoff.kora.http.client.symbol.processor

import org.intellij.lang.annotations.Language
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ru.tinkoff.kora.config.common.extractor.BooleanConfigValueExtractor
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor
import ru.tinkoff.kora.config.common.extractor.DoubleArrayConfigValueExtractor
import ru.tinkoff.kora.config.common.extractor.DurationConfigValueExtractor
import ru.tinkoff.kora.config.common.extractor.SetConfigValueExtractor
import ru.tinkoff.kora.config.common.extractor.StringConfigValueExtractor
import ru.tinkoff.kora.config.common.factory.MapConfigFactory
import ru.tinkoff.kora.http.client.common.HttpClient
import ru.tinkoff.kora.http.client.common.declarative.`$HttpClientOperationConfig_ConfigValueExtractor`
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse
import ru.tinkoff.kora.http.client.common.telemetry.`$HttpClientLoggerConfig_ConfigValueExtractor`
import ru.tinkoff.kora.http.client.common.telemetry.`$HttpClientTelemetryConfig_ConfigValueExtractor`
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryFactory
import ru.tinkoff.kora.http.common.body.HttpBody
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_MetricsConfig_ConfigValueExtractor`
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_TracingConfig_ConfigValueExtractor`
import ru.tinkoff.kora.telemetry.common.TelemetryConfig
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow

abstract class AbstractHttpClientTest : AbstractSymbolProcessorTest() {
    val httpResponse = mock<HttpClientResponse>().also {
        whenever(it.code()).thenReturn(200)
        whenever(it.body()).thenReturn(HttpBody.octetStream(ByteArray(0)))
    }
    val httpClient = mock<HttpClient>().also {
        whenever(it.execute(any())).thenReturn(CompletableFuture.completedFuture(httpResponse))
    }
    val telemetryFactory = mock<HttpClientTelemetryFactory>()
    lateinit var client: TestObject

    protected fun onRequest(method: String, path: String, responseConsumer: (TestHttpClientResponse) -> TestHttpClientResponse) {
        whenever(httpClient.execute(Mockito.argThat { arg: HttpClientRequest -> arg.method().equals(method, ignoreCase = true) && arg.uri().toString() == path }))
            .thenAnswer { invocation ->
                val f = CompletableFuture<Void?>()
                invocation.getArgument(0, HttpClientRequest::class.java).body().subscribe(object : Flow.Subscriber<ByteBuffer?> {
                    override fun onSubscribe(subscription: Flow.Subscription) = subscription.request(Long.MAX_VALUE)

                    override fun onNext(item: ByteBuffer?) {}
                    override fun onError(throwable: Throwable) {
                        f.completeExceptionally(throwable)
                    }

                    override fun onComplete() {
                        f.complete(null)
                    }
                })
                f.get()
                CompletableFuture.completedFuture(responseConsumer(TestHttpClientResponse(code = 200)))
            }
    }


    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.http.client.common.annotation.*
            import ru.tinkoff.kora.http.client.common.request.*
            import ru.tinkoff.kora.http.client.common.response.*
            import ru.tinkoff.kora.http.client.common.*
            import ru.tinkoff.kora.http.common.annotation.*
            import ru.tinkoff.kora.http.client.common.annotation.HttpClient
            import ru.tinkoff.kora.http.common.header.HttpHeaders
            import java.util.concurrent.CompletionStage
            import java.util.concurrent.CompletableFuture
            import ru.tinkoff.kora.http.client.common.interceptor.HttpClientInterceptor
            import ru.tinkoff.kora.http.client.common.interceptor.HttpClientInterceptor.InterceptChain

            """.trimIndent()
    }

    protected fun compile(arguments: List<Any?>, @Language("kotlin") vararg sources: String): TestObject {
        val compileResult = compile0(*sources)
        if (compileResult.isFailed()) {
            throw compileResult.compilationException()
        }

        val clientClass = compileResult.loadClass("\$TestClient_ClientImpl")
        val durationCVE = DurationConfigValueExtractor()
        val telemetryCVE = `$HttpClientTelemetryConfig_ConfigValueExtractor`(
            `$HttpClientLoggerConfig_ConfigValueExtractor`(SetConfigValueExtractor(StringConfigValueExtractor()), BooleanConfigValueExtractor()),
            `$TelemetryConfig_TracingConfig_ConfigValueExtractor`(BooleanConfigValueExtractor()),
            `$TelemetryConfig_MetricsConfig_ConfigValueExtractor`(BooleanConfigValueExtractor(), DoubleArrayConfigValueExtractor { it.asNumber()!!.toDouble() })
        ) as ConfigValueExtractor<TelemetryConfig>
        val configCVE = `$HttpClientOperationConfig_ConfigValueExtractor`(durationCVE, telemetryCVE)


        val configValueExtractor = new("\$\$TestClient_Config_ConfigValueExtractor", telemetryCVE, configCVE, durationCVE) as ConfigValueExtractor<*>
        val config = configValueExtractor.extract(MapConfigFactory.fromMap(mapOf(
            "url" to "http://test-url:8080"
        )).root())


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
