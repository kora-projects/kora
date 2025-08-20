package ru.tinkoff.kora.http.client.symbol.processor

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import ru.tinkoff.kora.http.client.common.interceptor.HttpClientInterceptor
import java.util.concurrent.atomic.AtomicReference

class HttpClientInterceptorsTest : AbstractHttpClientTest() {
    @Test
    @SuppressWarnings("unchecked")
    fun testInterceptors() {
        whenever(httpClient.with(ArgumentMatchers.any())).thenCallRealMethod()
        val clientLevelInterceptor = AtomicReference<HttpClientInterceptor>()
        val clientLevelInterceptorFactory = object : GeneratedObject<HttpClientInterceptor> {
            override fun invoke(): HttpClientInterceptor {
                val clazz = loadClass("ClientLevelInterceptor") as Class<out HttpClientInterceptor>
                clientLevelInterceptor.set(Mockito.mock(clazz))
                return clientLevelInterceptor.get()
            }
        }
        val methodLevelInterceptor = AtomicReference<HttpClientInterceptor>()
        val methodLevelInterceptorFactory = object : GeneratedObject<HttpClientInterceptor> {
            override fun invoke(): HttpClientInterceptor {
                val clazz = loadClass("MethodLevelInterceptor") as Class<out HttpClientInterceptor>
                methodLevelInterceptor.set(Mockito.mock(clazz))
                return methodLevelInterceptor.get()
            }
        }
        val client = compile(listOf<Any>(clientLevelInterceptorFactory, methodLevelInterceptorFactory), """
            @HttpClient
            @InterceptWith(ClientLevelInterceptor::class)
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test/{pathParam}")
              @InterceptWith(MethodLevelInterceptor::class)
              fun request(@Path pathParam: String)
            }
            
            """.trimIndent(), """
             open class ClientLevelInterceptor : HttpClientInterceptor {
                 override fun processRequest(ctx: Context, chain: InterceptChain, request: HttpClientRequest) : HttpClientResponse{
                     return chain.process(ctx, request)
                 }
             }
            
            """.trimIndent(), """
             open class MethodLevelInterceptor : HttpClientInterceptor {
                 override fun processRequest(ctx: Context, chain: InterceptChain, request: HttpClientRequest) : HttpClientResponse{
                     return chain.process(ctx, request)
                 }
             }
            
            """.trimIndent())
        whenever(clientLevelInterceptor.get().processRequest(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenCallRealMethod()
        whenever(methodLevelInterceptor.get().processRequest(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenCallRealMethod()
        onRequest("POST", "http://test-url:8080/test/test1") { rs -> rs.withCode(200) }
        client.invoke<Any>("request", "test1")
        val order = Mockito.inOrder(clientLevelInterceptor.get(), methodLevelInterceptor.get())
        order.verify(clientLevelInterceptor.get()).processRequest(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        order.verify(methodLevelInterceptor.get()).processRequest(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        order.verifyNoMoreInteractions()
    }
}
