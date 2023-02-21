package ru.tinkoff.kora.http.client.symbol.processor

import org.intellij.lang.annotations.Language
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.tinkoff.kora.http.client.common.HttpClient
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetry
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryFactory
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer
import java.util.concurrent.Future

abstract class AbstractHttpClientTest : AbstractSymbolProcessorTest() {
    val httpResponse = mock<HttpClientResponse>().also {
        whenever(it.close()).thenReturn(Mono.empty())
        whenever(it.code()).thenReturn(200)
        whenever(it.body()).thenReturn(Flux.just(ByteBuffer.allocate(0)))
    }
    val httpClient = mock<HttpClient>().also {
        whenever(it.execute(any())).thenReturn(Mono.just(httpResponse))
    }
    val telemetry = mock<HttpClientTelemetry>()
    val telemetryFactory = mock<HttpClientTelemetryFactory>()
    lateinit var client: TestClient

    class TestClient(private val clientClass: Class<*>, private val clientInstance: Any) {
        operator fun invoke(method: String, vararg args: Any?): Any? {
            for (repositoryClassMethod in clientClass.methods) {
                if (repositoryClassMethod.name == method && repositoryClassMethod.parameters.size == args.size) {
                    val result = try {
                        repositoryClassMethod.invoke(clientInstance, *args)
                    } catch (e: InvocationTargetException) {
                        throw e.targetException
                    }
                    return when (result) {
                        is Mono<*> -> result.block()
                        is Future<*> -> result.get()
                        else -> result
                    }
                }
            }
            throw IllegalArgumentException()
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
            import reactor.core.publisher.Mono
            import reactor.core.publisher.Flux

            """.trimIndent()
    }

    protected fun compile(arguments: List<Any?>, @Language("kotlin") vararg sources: String): TestClient {
        val compileResult = compile(listOf(HttpClientSymbolProcessorProvider()), *sources)
        if (compileResult.isFailed()) {
            throw compileResult.compilationException()
        }

        val repositoryClass = compileResult.loadClass("\$TestClient_ClientImpl")
        val realArgs = arrayOfNulls<Any>(arguments.size + 3)
        realArgs[0] = httpClient
        realArgs[1] = new("\$TestClient_Config", *Array(compileResult.loadClass("\$TestClient_Config").constructors[0].parameterCount) {
            if (it == 0) {
                "http://test-url:8080"
            } else {
                null
            }
        })
        realArgs[2] = telemetryFactory
        System.arraycopy(arguments.toTypedArray(), 0, realArgs, 3, arguments.size)
        for ((i, value) in realArgs.withIndex()) {
            if (value is GeneratedObject<*>) {
                realArgs[i] = value.invoke()
            }
        }
        val instance = repositoryClass.constructors[0].newInstance(*realArgs)
        client = TestClient(repositoryClass, instance)
        return client
    }
}
