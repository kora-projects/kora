package ru.tinkoff.kora.http.server.symbol.processor

import org.intellij.lang.annotations.Language
import ru.tinkoff.kora.http.common.body.HttpBody
import ru.tinkoff.kora.http.common.header.HttpHeaders
import ru.tinkoff.kora.http.server.common.HttpServerRequest
import ru.tinkoff.kora.http.server.common.HttpServerResponse
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper
import ru.tinkoff.kora.http.server.symbol.procesor.HttpControllerProcessorProvider
import ru.tinkoff.kora.http.server.symbol.processor.server.HttpResponseAssert
import ru.tinkoff.kora.http.server.symbol.processor.server.SimpleHttpServerRequest
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletionException

abstract class AbstractHttpControllerTest : AbstractSymbolProcessorTest() {
    override fun commonImports() = super.commonImports() + """
        import ru.tinkoff.kora.common.*;
        import ru.tinkoff.kora.http.server.common.annotation.*;
        import ru.tinkoff.kora.http.common.*;
        import ru.tinkoff.kora.http.common.annotation.*;
        import ru.tinkoff.kora.http.common.body.*;
        import ru.tinkoff.kora.http.common.header.*;
        import ru.tinkoff.kora.http.common.HttpResponseEntity;
        import ru.tinkoff.kora.http.server.common.HttpServerResponse;
        import ru.tinkoff.kora.http.server.common.HttpServerRequest;
        import ru.tinkoff.kora.http.server.common.HttpServerInterceptor;
        import ru.tinkoff.kora.http.common.HttpMethod.*;
        import java.util.concurrent.CompletionStage;
        import java.util.concurrent.CompletableFuture;
        import java.util.UUID;
        import java.math.BigInteger;
    """.trimIndent()


    protected class HttpControllerModule(private val controller: Any, private val moduleInterface: Class<*>, private val moduleObject: Any) {
        private fun getHandlerMethod(name: String): Method? {
            for (method in moduleInterface.getMethods()) {
                if (method.name == name) {
                    return method
                }
            }
            return null
        }

        fun getHandler(methodName: String, vararg args: Any?): HttpServerRequestHandler {
            val fullArgs = arrayOfNulls<Any>(args.size + 1)
            fullArgs[0] = controller
            System.arraycopy(args, 0, fullArgs, 1, args.size)
            val method = getHandlerMethod(methodName)!!
            return try {
                method.invoke(moduleObject, *fullArgs) as HttpServerRequestHandler
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    protected fun compile(@Language("kotlin") vararg sources: String): HttpControllerModule {
        val compileResult = compile0(listOf(HttpControllerProcessorProvider()), *sources)
            .assertSuccess()
        val moduleClass = loadClass("ControllerModule")
        val controllerClass = loadClass("Controller")
        val moduleObject = Proxy.newProxyInstance(compileResult.classLoader, arrayOf(moduleClass)) { proxy: Any, method: Method?, args: Array<Any?> ->
            MethodHandles.privateLookupIn(moduleClass, MethodHandles.lookup())
                .`in`(moduleClass)
                .unreflectSpecial(method, moduleClass)
                .bindTo(proxy)
                .invokeWithArguments(*args)
        }
        val controller = controllerClass.getConstructor().newInstance()
        return HttpControllerModule(controller, moduleClass, moduleObject)
    }

    protected fun strResponseMapper(): HttpServerResponseMapper<String> {
        return HttpServerResponseMapper { request: HttpServerRequest, result: String -> HttpServerResponse.of(200, HttpBody.plaintext(result)) }
    }

    protected fun stringRequestMapper(): HttpServerRequestMapper<String> {
        return HttpServerRequestMapper { request: HttpServerRequest -> String(request.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8) }
    }

    protected fun assertThat(handler: HttpServerRequestHandler, method: String, relativeUrl: String): HttpResponseAssert {
        return assertThat(handler, method, relativeUrl, "")
    }

    protected fun assertThat(handler: HttpServerRequestHandler, method: String, relativeUrl: String, body: String): HttpResponseAssert {
        return assertThat(handler, request(method, relativeUrl, body))
    }

    protected fun assertThat(handler: HttpServerRequestHandler, rq: HttpServerRequest): HttpResponseAssert {
        try {
            return HttpResponseAssert(handler.handle(rq))
        } catch (e: CompletionException) {
            e.cause?.let {
                if (it is HttpServerResponse) {
                    return HttpResponseAssert(it)
                }
                throw it
            }
            throw e
        } catch (e: Throwable) {
            if (e is HttpServerResponse) {
                return HttpResponseAssert(e)
            }
            throw e
        }
    }

    protected fun request(method: String, url: String, body: String): HttpServerRequest {
        return SimpleHttpServerRequest(method, url, body.toByteArray(), HttpHeaders.of(), mutableMapOf())
    }

    protected fun request(method: String, url: String, body: String, headers: HttpHeaders): HttpServerRequest {
        return SimpleHttpServerRequest(method, url, body.toByteArray(), headers, mutableMapOf())
    }

}
