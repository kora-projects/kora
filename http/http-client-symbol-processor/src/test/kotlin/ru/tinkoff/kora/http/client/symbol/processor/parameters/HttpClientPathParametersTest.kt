package ru.tinkoff.kora.http.client.symbol.processor.parameters

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import ru.tinkoff.kora.http.client.symbol.processor.AbstractHttpClientTest

class HttpClientPathParametersTest : AbstractHttpClientTest() {
    @Test
    fun testPathParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test/{pathParam}")
              fun request(@Path pathParam: String)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test/test1") { rs -> rs.withCode(200) }
        client.invoke<Unit>("request", "test1")

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test/test2") { rs -> rs.withCode(201) }
        client.invoke<Unit>("request", "test2")
    }

}
