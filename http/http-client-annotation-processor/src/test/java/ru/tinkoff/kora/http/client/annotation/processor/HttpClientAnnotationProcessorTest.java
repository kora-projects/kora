package ru.tinkoff.kora.http.client.annotation.processor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.assertj.core.api.Assertions;
import org.asynchttpclient.Dsl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpResponse;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.annotation.processor.client.ClientWithMappers;
import ru.tinkoff.kora.http.client.annotation.processor.client.ClientWithQueryParams;
import ru.tinkoff.kora.http.client.annotation.processor.client.GithubClient;
import ru.tinkoff.kora.http.client.annotation.processor.client.GithubClientReactive;
import ru.tinkoff.kora.http.client.async.AsyncHttpClient;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.HttpClientTimeoutException;
import ru.tinkoff.kora.http.client.common.declarative.HttpClientOperationConfig;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.http.client.common.telemetry.DefaultHttpClientTelemetryFactory;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryFactory;
import ru.tinkoff.kora.http.client.common.telemetry.Sl4fjHttpClientLoggerFactory;
import ru.tinkoff.kora.http.client.common.writer.StringParameterConverter;
import ru.tinkoff.kora.json.jackson.module.http.client.JacksonHttpClientRequestMapper;
import ru.tinkoff.kora.json.jackson.module.http.client.JacksonHttpClientResponseMapper;
import ru.tinkoff.kora.opentelemetry.module.http.client.OpentelemetryHttpClientTracerFactory;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockserver.model.HttpRequest.request;

class HttpClientAnnotationProcessorTest {
    private static final LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ClientAndServer server = ClientAndServer.startClientAndServer(0);
    private final HttpClient baseClient = new AsyncHttpClient(Dsl.asyncHttpClient());
    private final Tracer tracer = TracerProvider.noop().get("test");
    private final HttpClientTelemetryFactory telemetryFactory = new DefaultHttpClientTelemetryFactory(
        new Sl4fjHttpClientLoggerFactory(),
        new OpentelemetryHttpClientTracerFactory(tracer),
        null
    );

    @BeforeEach
    void setUp() {
        ctx.getLogger("ROOT").setLevel(Level.OFF);
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.ALL);
        ctx.getLogger(GithubClient.class).setLevel(Level.ALL);
        if (this.baseClient instanceof Lifecycle lifecycle) {
            lifecycle.init().block();
        }
    }

    @AfterEach
    void tearDown() {
        if (this.baseClient instanceof Lifecycle lifecycle) {
            lifecycle.release().block();
        }
        server.stop();
        Context.clear();
    }

    @Test
    void testGithubClient() throws Exception {
        var classInterceptor1 = Mockito.spy(GithubClient.TestInterceptor1.class);
        var classInterceptor2 = Mockito.spy(GithubClient.TestInterceptor1.class);
        var methodInterceptor1 = Mockito.spy(GithubClient.TestInterceptor2.class);
        var methodInterceptor2 = Mockito.spy(GithubClient.TestInterceptor2.class);
        var client = this.client(
            GithubClient.class,
            new HttpClientOperationConfig[]{
                new HttpClientOperationConfig(null),
                new HttpClientOperationConfig(null),
            },
            new Object[]{
                classInterceptor1,
                classInterceptor2,
                responseMapper(GithubClient.contributorListTypeRef),
                methodInterceptor1,
                methodInterceptor2,
                requestMapper(GithubClient.issueTypeRef),
                voidMono()
            }
        );
        server.when(request("/repos/testOwner/testRepo/contributors")
                .withMethod("GET"))
            .respond(HttpResponse.response("""
                [
                  {"login": "test0", "contributions": 0},
                  {"login": "test1", "contributions": 1},
                  {"login": "test2", "contributions": 2},
                  {"login": "test3", "contributions": 3}
                ]
                """));

        var contributors = client.contributors("testOwner", "testRepo");
        assertThat(contributors).isEqualTo(List.of(
            new GithubClient.Contributor("test0", 0),
            new GithubClient.Contributor("test1", 1),
            new GithubClient.Contributor("test2", 2),
            new GithubClient.Contributor("test3", 3)
        ));
        var order = Mockito.inOrder(classInterceptor1, classInterceptor2, methodInterceptor1, methodInterceptor2);
        order.verify(classInterceptor1).processRequest(any(), any());
        order.verify(classInterceptor2).processRequest(any(), any());
        order.verify(methodInterceptor1).processRequest(any(), any());
        order.verify(methodInterceptor2).processRequest(any(), any());

        var createIssueRequest = request("/repos/testOwner/testRepo/issues")
            .withMethod("POST")
            .withBody("""
                {"title":"title","body":"body","assignees":["assignee"],"milestone":1,"labels":["label"]}""")
            .withHeader("content-type", "application/json");

        server.when(createIssueRequest)
            .respond(HttpResponse.response().withStatusCode(201));
        client.createIssue(new GithubClient.Issue("title", "body", List.of("assignee"), 1, List.of("label")), "testOwner", "testRepo");
        server.verify(createIssueRequest);
    }

    @Test
    void testQuery() throws Exception {
        var client = this.client(
            ClientWithQueryParams.class,
            new HttpClientOperationConfig[]{
                new HttpClientOperationConfig(null),
                new HttpClientOperationConfig(null),
                new HttpClientOperationConfig(null),
                new HttpClientOperationConfig(null),
                new HttpClientOperationConfig(null),
                new HttpClientOperationConfig(null),
                new HttpClientOperationConfig(null),
                new HttpClientOperationConfig(null),
            },
            new Object[]{
                (StringParameterConverter<Integer>) Object::toString,
                (StringParameterConverter<Integer>) Object::toString,
                voidMono(),
                voidMono(),
                voidMono(),
                voidMono(),
                voidMono(),
                voidMono(),
                voidMono(),
                voidMono(),
            }
        );

        server.when(request("/test1")
                .withMethod("POST")
                .withQueryStringParameter("test", "test")
                .withQueryStringParameter("test1", "test1"))
            .respond(HttpResponse.response());
        client.test1("test1");
        server.reset();

        server.when(request("/test2")
                .withMethod("POST")
                .withQueryStringParameter("test2", "test2"))
            .respond(HttpResponse.response());
        client.test2("test2");
        server.reset();


        server.when(request("/test3")
                .withMethod("POST")
                .withQueryStringParameter("test3", "test3"))
            .respond(HttpResponse.response());
        client.test3("test3");
        server.reset();

        server.when(request("/test4")
                .withMethod("POST")
                .withQueryStringParameter("test4", "test4"))
            .respond(HttpResponse.response());
        client.test4("test4", null);
        server.reset();

        server.when(request("/test4")
                .withMethod("POST")
                .withQueryStringParameter("test4", "test4")
                .withQueryStringParameter("test", "test"))
            .respond(HttpResponse.response());
        client.test4("test4", "test");
        server.reset();


        server.when(request("/test6")
                .withMethod("POST")
                .withQueryStringParameter("test62", "test62")
                .withQueryStringParameter("test63", "test63"))
            .respond(HttpResponse.response());
        client.test6(null, "test62", "test63");
        server.reset();


        server.when(request("/nonStringParams")
                .withMethod("POST")
                .withQueryStringParameter("query1", "123")
                .withQueryStringParameter("query2", "456"))
            .respond(HttpResponse.response());
        client.nonStringParams(123, 456);
        server.reset();


        server.when(request("/multipleParams")
                .withMethod("POST")
                .withQueryStringParameter("query1", "foo", "bar")
                .withQueryStringParameter("query2", "1", "2", "3"))
            .respond(HttpResponse.response());
        client.multipleParams(List.of("foo", "bar"), List.of(1,2,3));
        server.reset();


        server.when(request("/multipleParams")
                .withMethod("POST")
                .withQueryStringParameter("query1", "foo", "bar"))
            .respond(HttpResponse.response());
        client.multipleParams(List.of("foo", "bar"), List.of());
        server.reset();

        server.when(request("/multipleParams")
                .withMethod("POST")
                .withQueryStringParameter("query1", "foo", "bar"))
            .respond(HttpResponse.response());
        client.multipleParams(List.of("foo", "bar"), null);
        server.reset();
    }

    @Test
    void testReactiveClient() throws Exception {
        var client = this.client(
            GithubClientReactive.class,
            new HttpClientOperationConfig[]{
                new HttpClientOperationConfig(null),
                new HttpClientOperationConfig(null)
            },
            new Object[]{
                responseMapper(GithubClientReactive.contributorListTypeRef),
                requestMapper(GithubClientReactive.issueTypeRef),
                voidMono()
            }
        );
        server.when(request("/repos/testOwner/testRepo/contributors")
                .withMethod("GET"))
            .respond(HttpResponse.response("""
                [
                  {"login": "test0", "contributions": 0},
                  {"login": "test1", "contributions": 1},
                  {"login": "test2", "contributions": 2},
                  {"login": "test3", "contributions": 3}
                ]
                """));

        var contributors = client.contributors("testOwner", "testRepo").block();
        assertThat(contributors).isEqualTo(List.of(
            new GithubClientReactive.Contributor("test0", 0),
            new GithubClientReactive.Contributor("test1", 1),
            new GithubClientReactive.Contributor("test2", 2),
            new GithubClientReactive.Contributor("test3", 3)
        ));

        var createIssueRequest = request("/repos/testOwner/testRepo/issues")
            .withMethod("POST")
            .withBody("""
                {"title":"title","body":"body","assignees":["assignee"],"milestone":1,"labels":["label"]}""")
            .withHeader("content-type", "application/json");
        server.when(createIssueRequest)
            .respond(HttpResponse.response().withStatusCode(201));
        client.createIssue(new GithubClientReactive.Issue("title", "body", List.of("assignee"), 1, List.of("label")), "testOwner", "testRepo").block();
        server.verify(createIssueRequest);
    }

    @Test
    void testClientWithTags() throws Exception {
        var client = this.client(
            ClientWithMappers.class,
            new HttpClientOperationConfig[]{
                new HttpClientOperationConfig(null),
                new HttpClientOperationConfig(null),
                new HttpClientOperationConfig(null),
            },
            new Object[]{
                new ClientWithMappers.ContributorListMapper(),
                new ClientWithMappers.IssueRequestMapper(),
                new ClientWithMappers.CustomVoidMapper(),
                voidMono(),
                new ClientWithMappers.CustomVoidMapper(),
                new ClientWithMappers.IssueRequestMapper(),
                voidMono(),
                voidMono(),
            }
        );

        server.when(request("/repos/testOwner/testRepo/contributors")
                .withMethod("GET"))
            .respond(HttpResponse.response("""
                [
                  {"login": "test0", "contributions": 0},
                  {"login": "test1", "contributions": 1},
                  {"login": "test2", "contributions": 2},
                  {"login": "test3", "contributions": 3}
                ]
                """));

        var contributors = client.contributors("testOwner", "testRepo");
        assertThat(contributors).isEqualTo(List.of());

        var createIssueRequest = request("/repos/testOwner/testRepo/issues")
            .withMethod("POST")
            .withBody("TEST");
        server.when(createIssueRequest)
            .respond(HttpResponse.response().withStatusCode(418));
        client.createIssue(new ClientWithMappers.Issue("title", "body", List.of("assignee"), 1, List.of("label")), "testOwner", "testRepo");
        server.verify(createIssueRequest);
    }

    @Test
    void testClientTimeout() throws Exception {
        var classLoader = TestUtils.annotationProcess(GithubClient.class, new HttpClientAnnotationProcessor());
        var clientClass = classLoader.loadClass(GithubClient.class.getPackageName() + ".$" + GithubClient.class.getSimpleName() + "_ClientImpl");
        var configClass = classLoader.loadClass(GithubClient.class.getPackageName() + ".$" + GithubClient.class.getSimpleName() + "_Config");
        var configModuleClass = classLoader.loadClass(GithubClient.class.getPackageName() + ".$" + GithubClient.class.getSimpleName() + "_Module");
        var classInterceptor1 = Mockito.spy(GithubClient.TestInterceptor1.class);
        var classInterceptor2 = Mockito.spy(GithubClient.TestInterceptor1.class);
        var methodInterceptor1 = Mockito.spy(GithubClient.TestInterceptor2.class);
        var methodInterceptor2 = Mockito.spy(GithubClient.TestInterceptor2.class);
        var configs = new HttpClientOperationConfig[]{
            new HttpClientOperationConfig(10),
            new HttpClientOperationConfig(null),
        };
        var mappers = new Object[]{
            classInterceptor1,
            classInterceptor2,
            responseMapper(GithubClient.contributorListTypeRef),
            methodInterceptor1,
            methodInterceptor2,
            requestMapper(GithubClient.issueTypeRef),
            voidMono()
        };

        var config = this.config(configClass, "http://localhost:" + this.server.getLocalPort(), 100, configs);
        var client = GithubClient.class.cast(this.client(clientClass, config, mappers));

        server.when(request("/repos/testOwner/testRepo/contributors"))
            .respond(HttpResponse.response().withDelay(TimeUnit.MILLISECONDS, 1000));

        server.when(request("/repos/testOwner/testRepo/issues"))
            .respond(HttpResponse.response().withDelay(TimeUnit.MILLISECONDS, 1000).withStatusCode(201));


        Assertions.assertThatThrownBy(() -> client.contributors("testOwner", "testRepo"))
            .isInstanceOf(HttpClientTimeoutException.class);
        Assertions.assertThatThrownBy(() -> client.createIssue(new GithubClient.Issue("title", "body", List.of("assignee"), 1, List.of("label")), "testOwner", "testRepo"))
            .isInstanceOf(HttpClientTimeoutException.class);
    }

    private <T> T client(Class<T> clazz, HttpClientOperationConfig[] configs, Object[] mappers) throws Exception {
        var classLoader = TestUtils.annotationProcess(clazz, new HttpClientAnnotationProcessor());
        var clientClass = classLoader.loadClass(clazz.getPackageName() + ".$" + clazz.getSimpleName() + "_ClientImpl");
        var configClass = classLoader.loadClass(clazz.getPackageName() + ".$" + clazz.getSimpleName() + "_Config");

        var config = this.config(configClass, "http://localhost:" + this.server.getLocalPort(), null, configs);
        return clazz.cast(this.client(clientClass, config, mappers));
    }

    private <T> T config(Class<T> clazz, String url, @Nullable Integer requestTimeout, HttpClientOperationConfig... configs) {
        var types = Arrays.copyOf(new Class<?>[]{String.class, Integer.class}, 2 + configs.length);
        Arrays.fill(types, 2, types.length, HttpClientOperationConfig.class);
        var parameters = Arrays.copyOf(new Object[]{url, requestTimeout}, 2 + configs.length);
        System.arraycopy(configs, 0, parameters, 2, configs.length);

        try {
            return clazz.getConstructor(types).newInstance(parameters);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T client(Class<T> clazz, Object config, Object[] mappers) {
        var params = Arrays.copyOf(new Object[]{this.baseClient, config, this.telemetryFactory}, 3 + mappers.length);
        System.arraycopy(mappers, 0, params, 3, mappers.length);

        try {
            return (T) clazz.getConstructors()[0].newInstance(params);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HttpClientResponseMapper<Void, Mono<Void>> voidMono() {
        return response -> Mono.empty();
    }

    private <T> JacksonHttpClientRequestMapper<T> requestMapper(TypeReference<T> typeReference) {
        return new JacksonHttpClientRequestMapper<>(this.objectMapper, typeReference);
    }

    private <T> JacksonHttpClientResponseMapper<T> responseMapper(TypeReference<T> typeReference) {
        return new JacksonHttpClientResponseMapper<>(this.objectMapper, typeReference);
    }
}
