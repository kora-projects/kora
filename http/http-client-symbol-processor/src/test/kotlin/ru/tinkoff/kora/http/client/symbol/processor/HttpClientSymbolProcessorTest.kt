package ru.tinkoff.kora.http.client.symbol.processor

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.devtools.ksp.KspExperimental
import io.opentelemetry.api.trace.TracerProvider
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.asynchttpclient.Dsl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import ru.tinkoff.kora.application.graph.Lifecycle
import ru.tinkoff.kora.common.Context
import ru.tinkoff.kora.http.client.async.AsyncHttpClient
import ru.tinkoff.kora.http.client.common.HttpClient
import ru.tinkoff.kora.http.client.common.declarative.`$HttpClientOperationConfig_ConfigValueExtractor`.HttpClientOperationConfig_Impl
import ru.tinkoff.kora.http.client.common.declarative.HttpClientOperationConfig
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper
import ru.tinkoff.kora.http.client.common.telemetry.DefaultHttpClientTelemetryFactory
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryFactory
import ru.tinkoff.kora.http.client.common.telemetry.Sl4fjHttpClientLoggerFactory
import ru.tinkoff.kora.http.client.common.writer.StringParameterConverter
import ru.tinkoff.kora.http.client.symbol.processor.client.*
import ru.tinkoff.kora.json.jackson.module.http.client.JacksonHttpClientRequestMapper
import ru.tinkoff.kora.json.jackson.module.http.client.JacksonHttpClientResponseMapper
import ru.tinkoff.kora.ksp.common.symbolProcess
import ru.tinkoff.kora.opentelemetry.module.http.client.OpentelemetryHttpClientTracerFactory
import java.time.Duration
import java.time.LocalDate
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.full.cast


@KspExperimental
class HttpClientSymbolProcessorTest {
    private val ctx = LoggerFactory.getILoggerFactory() as LoggerContext
    private val objectMapper = ObjectMapper().findAndRegisterModules().registerKotlinModule()
    private val server = ClientAndServer.startClientAndServer(0)
    private val baseClient: HttpClient = AsyncHttpClient(Dsl.asyncHttpClient())
    private val tracer = TracerProvider.noop()["test"]
    private val telemetryFactory: HttpClientTelemetryFactory = DefaultHttpClientTelemetryFactory(
        Sl4fjHttpClientLoggerFactory(),
        OpentelemetryHttpClientTracerFactory(tracer),
        null
    )

    @BeforeEach
    fun setUp() {
        ctx.getLogger("ROOT").level = Level.OFF
        ctx.getLogger("ru.tinkoff.kora.http.client").level = Level.ALL
        ctx.getLogger(GithubClient::class.java).level = Level.ALL
        if (baseClient is Lifecycle) {
            baseClient.init().block()
        }
    }

    @AfterEach
    fun tearDown() {
        if (baseClient is Lifecycle) {
            baseClient.release().block()
        }
        server.stop()
        Context.clear()
    }

    @Test
    @Throws(java.lang.Exception::class)
    fun testGithubClient() {
        val classInterceptor1 = Mockito.spy(GithubClient.TestInterceptor1::class.java)
        val classInterceptor2 = Mockito.spy(GithubClient.TestInterceptor1::class.java)
        val methodInterceptor1 = Mockito.spy(GithubClient.TestInterceptor2::class.java)
        val methodInterceptor2 = Mockito.spy(GithubClient.TestInterceptor2::class.java)
        val client: GithubClient = this.client(
            GithubClient::class, arrayOf(
                HttpClientOperationConfig_Impl(null),
                HttpClientOperationConfig_Impl(null),
                HttpClientOperationConfig_Impl(null),
            ), arrayOf(
                classInterceptor1,
                classInterceptor2,
                responseMapper(GithubClient.contributorListTypeRef),
                methodInterceptor1,
                methodInterceptor2,
                requestMapper(GithubClient.issueTypeRef),
                unitMono(),
                unitMono(),
            )
        )
        server.`when`(
            HttpRequest.request("/repos/testOwner/testRepo/contributors")
                .withMethod("GET")
        )
            .respond(
                HttpResponse.response(
                    """
                [
                  {"login": "test0", "contributions": 0},
                  {"login": "test1", "contributions": 1},
                  {"login": "test2", "contributions": 2},
                  {"login": "test3", "contributions": 3}
                ]
                """.trimIndent()
                )
            )
        val contributors: List<GithubClient.Contributor> = client.contributors("testOwner", "testRepo")
        Assertions.assertThat(contributors).isEqualTo(
            listOf(
                GithubClient.Contributor("test0", 0),
                GithubClient.Contributor("test1", 1),
                GithubClient.Contributor("test2", 2),
                GithubClient.Contributor("test3", 3)
            )
        )
        val order = Mockito.inOrder(classInterceptor1, classInterceptor2, methodInterceptor1, methodInterceptor2)
        order.verify(classInterceptor1).processRequest(any(), any())
        order.verify(classInterceptor2).processRequest(any(), any())
        order.verify(methodInterceptor1).processRequest(any(), any())
        order.verify(methodInterceptor2).processRequest(any(), any())


        val createIssueRequest: HttpRequest = HttpRequest.request("/repos/testOwner/testRepo/issues")
            .withMethod("POST")
            .withBody(
                """
                {"title":"title","body":"body","assignees":["assignee"],"milestone":1,"labels":["label"]}
                """.trimIndent()
            )
            .withHeader("content-type", "application/json")
        server.`when`(createIssueRequest)
            .respond(HttpResponse.response().withStatusCode(201))
        client.createIssue(
            GithubClient.Issue("title", "body", listOf("assignee"), 1, listOf("label")),
            "testOwner",
            "testRepo"
        )
        server.verify(createIssueRequest)
    }

    @Test
    fun testQuery() {
        val client: ClientWithQueryParams = this.client(
            ClientWithQueryParams::class, arrayOf(
                HttpClientOperationConfig_Impl(null),
                HttpClientOperationConfig_Impl(null),
                HttpClientOperationConfig_Impl(null),
                HttpClientOperationConfig_Impl(null),
                HttpClientOperationConfig_Impl(null),
                HttpClientOperationConfig_Impl(null),
                HttpClientOperationConfig_Impl(null),
                HttpClientOperationConfig_Impl(null),
            ), arrayOf(
                StringParameterConverter<LocalDate> { it.toString() },
                StringParameterConverter<LocalDate> { it.toString() },
                unitMono(),
                unitMono(),
                unitMono(),
                unitMono(),
                unitMono(),
                unitMono(),
                unitMono(),
                unitMono(),
            )
        )
        server.`when`(
            HttpRequest.request("/test1")
                .withMethod("POST")
                .withQueryStringParameter("test", "test")
                .withQueryStringParameter("test1", "test1")
        )
            .respond(HttpResponse.response())
        client.test1("test1")
        server.reset()
        server.`when`(
            HttpRequest.request("/test2")
                .withMethod("POST")
                .withQueryStringParameter("test2", "test2")
        )
            .respond(HttpResponse.response())
        client.test2("test2")
        server.reset()
        server.`when`(
            HttpRequest.request("/test3")
                .withMethod("POST")
                .withQueryStringParameter("test3", "test3")
        )
            .respond(HttpResponse.response())
        client.test3("test3")
        server.reset()
        server.`when`(
            HttpRequest.request("/test4")
                .withMethod("POST")
                .withQueryStringParameter("test4", "test4")
        )
            .respond(HttpResponse.response())
        client.test4("test4", null)
        server.reset()
        server.`when`(
            HttpRequest.request("/test4")
                .withMethod("POST")
                .withQueryStringParameter("test4", "test4")
                .withQueryStringParameter("test", "test")
        )
            .respond(HttpResponse.response())
        client.test4("test4", "test")
        server.reset()

        server.`when`(
            HttpRequest.request("/test6")
                .withMethod("POST")
                .withQueryStringParameter("test62", "test62")
                .withQueryStringParameter("test63", "test63")
        )
            .respond(HttpResponse.response())
        client.test6(null, "test62", "test63")
        server.reset()

        server.`when`(
            HttpRequest.request("/nonStringParams")
                .withMethod("POST")
                .withQueryStringParameter("query1", "1")
                .withQueryStringParameter("query2", "2022-04-04")
                .withQueryStringParameter("query3", "2")
        )
            .respond(HttpResponse.response())
        client.nonStringParams(1, LocalDate.parse("2022-04-04"), 2)
        server.reset()

        server.`when`(
            HttpRequest.request("/nonStringParams")
                .withMethod("POST")
                .withQueryStringParameter("query1", "1")
                .withQueryStringParameter("query2", "2022-04-04")
        )
            .respond(HttpResponse.response())
        client.nonStringParams(1, LocalDate.parse("2022-04-04"), null)
        server.reset()

        server.`when`(
            HttpRequest.request("/multipleParams")
                .withMethod("POST")
                .withQueryStringParameter("query1", "1", "2")
                .withQueryStringParameter("query2", "a", "b")
                .withQueryStringParameter("query3", "2022-04-04", "2022-04-05")
        )
            .respond(HttpResponse.response())
        client.multipleParams(listOf(1, 2), listOf("a", "b"), mutableListOf(LocalDate.parse("2022-04-04"), LocalDate.parse("2022-04-05")))
        server.reset()

        server.`when`(
            HttpRequest.request("/multipleParams")
                .withMethod("POST")
                .withQueryStringParameter("query1", "1", "2")
                .withQueryStringParameter("query3", "2022-04-04")
        )
            .respond(HttpResponse.response())
        client.multipleParams(listOf(1, 2), null, mutableListOf(LocalDate.parse("2022-04-04"), null))
        server.reset()
    }

    @Test
    @Throws(java.lang.Exception::class)
    fun testReactiveClient() {
        val client: GithubClientReactive = this.client(
            GithubClientReactive::class, arrayOf(
                HttpClientOperationConfig_Impl(null),
                HttpClientOperationConfig_Impl(null)
            ), arrayOf(
                responseMapper(GithubClientReactive.contributorListTypeRef),
                requestMapper(GithubClientReactive.issueTypeRef),
                unitMono()
            )
        )
        server.`when`(
            HttpRequest.request("/repos/testOwner/testRepo/contributors")
                .withMethod("GET")
        )
            .respond(HttpResponse.response("""
                [
                  {"login": "test0", "contributions": 0},
                  {"login": "test1", "contributions": 1},
                  {"login": "test2", "contributions": 2},
                  {"login": "test3", "contributions": 3}
                ]
                """.trimIndent()))
        val contributors: List<GithubClientReactive.Contributor> = client.contributors("testOwner", "testRepo").block()
        Assertions.assertThat(contributors).isEqualTo(
            java.util.List.of(
                GithubClientReactive.Contributor("test0", 0),
                GithubClientReactive.Contributor("test1", 1),
                GithubClientReactive.Contributor("test2", 2),
                GithubClientReactive.Contributor("test3", 3)
            )
        )
        val createIssueRequest: HttpRequest = HttpRequest.request("/repos/testOwner/testRepo/issues")
            .withMethod("POST")
            .withBody("""
                {"title":"title","body":"body","assignees":["assignee"],"milestone":1,"labels":["label"]}""".trimIndent())
            .withHeader("content-type", "application/json")
        server.`when`(createIssueRequest)
            .respond(HttpResponse.response().withStatusCode(201))
        client.createIssue(
            GithubClientReactive.Issue("title", "body", java.util.List.of("assignee"), 1, java.util.List.of("label")),
            "testOwner",
            "testRepo"
        ).block()
        server.verify(createIssueRequest)
    }

    @Test
    fun testClientWithTags() {
        val client: ClientWithMappers = this.client(
            ClientWithMappers::class, arrayOf(
                HttpClientOperationConfig_Impl(null),
                HttpClientOperationConfig_Impl(null),
                HttpClientOperationConfig_Impl(null),
            ), arrayOf(
                ClientWithMappers.IssueRequestMapper(),
                unitMono(),
                ClientWithMappers.IssueRequestMapper(),
                unitMono(),
                unitMono(),
            )
        )
        server.`when`(
            HttpRequest.request("/repos/testOwner/testRepo/contributors")
                .withMethod("GET")
        )
            .respond(HttpResponse.response("""
                [
                  {"login": "test0", "contributions": 0},
                  {"login": "test1", "contributions": 1},
                  {"login": "test2", "contributions": 2},
                  {"login": "test3", "contributions": 3}
                ]
                """.trimIndent()))
        val contributors: List<ClientWithMappers.Contributor> = client.contributors("testOwner", "testRepo")
        Assertions.assertThat<ClientWithMappers.Contributor>(contributors).isEqualTo(java.util.List.of<Any>())
        val createIssueRequest = HttpRequest.request("/repos/testOwner/testRepo/issues")
            .withMethod("POST")
            .withBody("TEST")
        server.`when`(createIssueRequest)
            .respond(HttpResponse.response().withStatusCode(418))
        client.createIssue(
            ClientWithMappers.Issue("title", "body", java.util.List.of("assignee"), 1, java.util.List.of("label")),
            "testOwner",
            "testRepo"
        )
        server.verify(createIssueRequest)
    }


    @Test
    @Throws(Exception::class)
    fun testClientKotlin() {
        val client: GithubClientKotlin = this.client(
            GithubClientKotlin::class, arrayOf(
                HttpClientOperationConfig_Impl(null),
                HttpClientOperationConfig_Impl(null)
            ), arrayOf(
                responseMapper(GithubClient.contributorListTypeRef),
                requestMapper(GithubClient.issueTypeRef),
                HttpClientResponseMapper {
                    Mono.just(
                        Unit
                    )
                } as HttpClientResponseMapper<Unit, Mono<Unit>>
            ))
        server.`when`(
            HttpRequest.request("/repos/testOwner/testRepo/contributors")
                .withMethod("GET")
        )
            .respond(
                HttpResponse.response(
                    """
                [
                  {"login": "test0", "contributions": 0},
                  {"login": "test1", "contributions": 1},
                  {"login": "test2", "contributions": 2},
                  {"login": "test3", "contributions": 3}
                ]
                """
                )
            )
        val contributors: List<GithubClient.Contributor?> =
            runBlocking<List<GithubClient.Contributor?>>(EmptyCoroutineContext) {
                client.contributors(
                    "testOwner",
                    "testRepo"
                )
            }
        Assertions.assertThat<GithubClient.Contributor>(contributors).isEqualTo(
            listOf(
                GithubClient.Contributor("test0", 0),
                GithubClient.Contributor("test1", 1),
                GithubClient.Contributor("test2", 2),
                GithubClient.Contributor("test3", 3)
            )
        )
        val createIssueRequest: HttpRequest = HttpRequest.request("/repos/testOwner/testRepo/issues")
            .withMethod("POST")
            .withBody("""{"title":"title","body":"body","assignees":["assignee"],"milestone":1,"labels":["label"]}""")
            .withHeader("content-type", "application/json")
        server.`when`(createIssueRequest)
            .respond(HttpResponse.response().withStatusCode(201))
        val issue: GithubClient.Issue =
            GithubClient.Issue("title", "body", java.util.List.of("assignee"), 1, java.util.List.of("label"))
        runBlocking<Unit>(EmptyCoroutineContext) {
            client.createIssue(
                issue,
                "testOwner",
                "testRepo"
            )
        }
        server.verify(createIssueRequest)
    }

    private fun <T : Any> client(clazz: KClass<T>, configs: Array<HttpClientOperationConfig>, mappers: Array<Any>): T {
        val classLoader: ClassLoader = symbolProcess(clazz, HttpClientSymbolProcessorProvider())
        val clientClass = classLoader.loadClass(clazz.java.packageName + ".$" + clazz.simpleName + "_ClientImpl")
        val configClass = classLoader.loadClass(clazz.java.packageName + ".$" + clazz.simpleName + "_Config")
        val config = this.config(configClass, "http://localhost:" + server.localPort, null, *configs)
        return clazz.cast(client(clientClass, config, mappers))
    }

    private fun <T> client(clazz: Class<T>, config: Any, mappers: Array<Any>): T {
        val params = arrayOf(baseClient, config, telemetryFactory).copyOf(3 + mappers.size)
        System.arraycopy(mappers, 0, params, 3, mappers.size)
        return try {
            clazz.constructors[0].newInstance(*params) as T
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    private fun <T> config(clazz: Class<T>, url: String, requestTimeout: Duration?, vararg configs: HttpClientOperationConfig): T {
        val parameters = arrayOf<Any?>(url, requestTimeout).copyOf(2 + configs.size)
        System.arraycopy(configs, 0, parameters, 2, configs.size)
        return try {
            val constructor = clazz.constructors.first()
            constructor.newInstance(*parameters) as T
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    private fun <T> requestMapper(typeReference: TypeReference<T>): JacksonHttpClientRequestMapper<T> {
        return JacksonHttpClientRequestMapper(objectMapper, typeReference)
    }

    private fun <T> responseMapper(typeReference: TypeReference<T>): JacksonHttpClientResponseMapper<T> {
        return JacksonHttpClientResponseMapper(objectMapper, typeReference)
    }

    private fun unitMono(): HttpClientResponseMapper<Unit, Mono<Unit>> {
        return object: HttpClientResponseMapper<Unit, Mono<Unit>> {
            override fun apply(response: HttpClientResponse?): Mono<Unit> {
                return  Mono.empty()
            }
        }
    }
}
