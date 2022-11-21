package ru.tinkoff.kora.http.server.symbol.processor.server

import com.google.devtools.ksp.KspExperimental
import org.mockito.Mockito
import reactor.core.publisher.Mono
import ru.tinkoff.kora.http.common.HttpHeaders
import ru.tinkoff.kora.http.server.common.HttpServerRequestHandler
import ru.tinkoff.kora.http.server.common.HttpServerResponse
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse
import ru.tinkoff.kora.http.server.symbol.procesor.HttpControllerProcessorProvider
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

class TestHttpServer<T>(private val requestHandlers: List<HttpServerRequestHandler>, val controller: T) {
    @SafeVarargs
    operator fun invoke(method: String, path: String, body: ByteArray, vararg headers: Map.Entry<String, String>): HttpResponseAssert {
        for (requestHandler in requestHandlers) {
            if (requestHandler.method() != method) {
                continue
            }
            val routeParams = extractRouteParams(requestHandler.routeTemplate(), path)
            if (routeParams != null) {
                val response = Mono.from(Mono.defer {
                    Mono.from(
                        requestHandler.handle(SimpleHttpServerRequest(method, path, body, headers, routeParams))
                    )
                })
                    .onErrorResume(
                        { e: Throwable? -> e is HttpServerResponse }
                    ) { e: Throwable? ->
                        Mono.just(
                            e as HttpServerResponse?
                        )
                    }
                    .switchIfEmpty(Mono.error(RuntimeException("[eq")))
                    .block()
                return HttpResponseAssert(response)
            }
        }
        return HttpResponseAssert(SimpleHttpServerResponse(404, "text/plain", HttpHeaders.of(), null))
    }

    private fun extractRouteParams(routeTemplate: String, path: String): Map<String, String>? {
        var path = path
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf('?'))
        }
        val routeSegments = routeTemplate.split("/".toRegex()).toTypedArray()
        val pathSegments = path.split("/".toRegex()).toTypedArray()
        if (routeSegments.size != pathSegments.size) {
            return null
        }
        val parameters = HashMap<String, String>()
        for (i in routeSegments.indices) {
            val routeSegment = routeSegments[i]
            val pathSegment = pathSegments[i]
            if (routeSegment.startsWith("{") && routeSegment.endsWith("}")) {
                val paramName = routeSegment.substring(1, routeSegment.length - 1)
                parameters[paramName] = pathSegment
            } else {
                if (routeSegment != pathSegment) {
                    return null
                }
            }
        }
        return parameters
    }

    companion object {
        @KspExperimental
        fun <T : Any> fromController(controller: KClass<T>): TestHttpServer<T> {
            return try {
                val classLoader = symbolProcess(controller, HttpControllerProcessorProvider())
                val module =  classLoader.loadClass(controller.qualifiedName + "Module")
                val p = Proxy.newProxyInstance(
                    classLoader, arrayOf(module)
                ) { proxy: Any?, method: Method?, args: Array<Any?> ->
                    MethodHandles.privateLookupIn(module, MethodHandles.lookup())
                        .`in`(module)
                        .unreflectSpecial(method, module)
                        .bindTo(proxy)
                        .invokeWithArguments(*args)
                }
                val mock = Mockito.mock(controller.java)
                val handlers = ArrayList<HttpServerRequestHandler>()
                for (method in module.methods) {
                    if (!method.isDefault){
                        continue
                    }
                    if (method.returnType != HttpServerRequestHandler::class.java) {
                        continue
                    }
                    val parameters = method.genericParameterTypes
                    if (parameters.size < 2) {
                        continue
                    }
                    if (parameters[0] != controller.java) {
                        continue
                    }
                    val callParameters = arrayOfNulls<Any>(parameters.size + 1)
                    var mt = MethodType.methodType(HttpServerRequestHandler::class.java, controller.java)
                    callParameters[0] = p
                    callParameters[1] = mock
                    for (i in 1 until parameters.size) {
                        val lookup = Mappers.lookupParameter(parameters[i])
                        mt = mt.appendParameterTypes(lookup.t1)
                        callParameters[i + 1] = lookup.t2
                    }
                    handlers.add(
                        MethodHandles.lookup()
                            .`in`(module)
                            .findVirtual(module, method.name, mt)
                            .invokeWithArguments(*callParameters) as HttpServerRequestHandler
                    )
                }
                TestHttpServer(handlers, mock)
            } catch (e: RuntimeException) {
                throw e
            } catch (e: Throwable) {
                throw RuntimeException(e)
            }
        }
    }
}
