package ru.tinkoff.kora.http.server.common.router;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.$HttpServerConfig_ConfigValueExtractor.HttpServerConfig_Impl;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandlerImpl;
import ru.tinkoff.kora.http.server.common.telemetry.*;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

class PublicApiHandlerProcessTests {

    static Stream<Arguments> dataWhenDefault() {
        return Stream.of(
            Arguments.of("POST", "/foo/bar", "/foo/bar", 200, 200),
            Arguments.of("POST", "/foo/bar", "/baz/foo", 200, 404),
            Arguments.of("POST", "/foo/bar", "/foo/bar/baz", 200, 404),
            Arguments.of("POST", "/foo/bar/", "/foo/bar/", 200, 200),
            Arguments.of("POST", "/foo/bar/", "/foo/bar", 200, 404),
            Arguments.of("POST", "/foo/bar/", "/foo/bar/baz", 200, 404),
            Arguments.of("POST", "/foo/bar", "/foo/bar/", 200, 404),
            Arguments.of("POST", "/foo/bar", "/foo/bar/baz", 200, 404),
            Arguments.of("POST", "/foo/bar/{baz}", "/foo/bar", 200, 404),
            Arguments.of("POST", "/foo/bar/{baz}", "/foo/bar/", 200, 404),
            Arguments.of("POST", "/foo/bar/{baz}", "/foo/bar/baz", 200, 200),
            Arguments.of("POST", "/foo/bar/{baz}/", "/foo/bar/baz/", 200, 200),
            Arguments.of("POST", "/foo/bar/{baz}/", "/foo/bar/baz", 200, 404),
            Arguments.of("POST", "/foo/bar/{baz}", "/foo/bar/baz/", 200, 404),
            Arguments.of("POST", "/foo/{bar}/baz", "/foo/bar/baz", 200, 200),
            Arguments.of("POST", "/foo/{bar}/baz/", "/foo/bar/baz/", 200, 200),
            Arguments.of("POST", "/foo/{bar}/baz", "/foo/bar/baz/", 200, 404),
            Arguments.of("POST", "/foo/{bar}/baz/", "/foo/bar/baz", 200, 404),
            Arguments.of("POST", "/foo/{bar}/baz/", "/foo/bar/baz/baz", 200, 404),
            Arguments.of("POST", "/foo/{bar}/baz", "/foo/bar/baz/baz", 200, 404),
            Arguments.of("POST", "/foo/{bar}/baz/baz", "/foo/bar/baz", 200, 404),
            Arguments.of("POST", "/foo/{bar}/baz/baz", "/foo/bar/baz/", 200, 404),
            Arguments.of("POST", "/foo/{bar}", "/foo/b%20ar", 200, 200), // %20 - пробел
            Arguments.of("POST", "/foo/{bar}", "/foo/b%3Far", 200, 200), // %3F - ?
            Arguments.of("POST", "/foo/{bar}/baz", "/foo/b%20ar/baz", 200, 200), // %20 - пробел
            Arguments.of("POST", "/foo/{bar}/baz", "/foo/b%3Far/baz", 200, 200) // %3F - ?
        );
    }

    @ParameterizedTest
    @MethodSource("dataWhenDefault")
    void processRequestWhenDefault(String method, String route, String path, int responseCode, int expectedCode) throws Exception {
        // given
        var handlers = List.of(handler(method, route));
        var telemetry = NoopHttpServerTelemetry.INSTANCE;
        var telemetryFactory = Mockito.mock(HttpServerTelemetryFactory.class);
        when(telemetryFactory.get(any())).thenReturn(telemetry);
        var config = config(false);
        var handler = new PublicApiHandler(handlers, All.of(), config);

        // when
        var request = new PublicApiRequestImpl(method, path, "foo", "http", HttpHeaders.of(), Map.of(), HttpBody.empty());

        // then
        var routedRq = handler.route(request);
        var rs = routedRq.proceed(routedRq.request);
        assertThat(rs.code()).isEqualTo(expectedCode);
    }

    static Stream<Arguments> dataWhenIgnoreTrailingSlash() {
        return Stream.of(
            Arguments.of("POST", "/foo/bar", "/baz/foo", 200, 404),
            Arguments.of("POST", "/foo/bar", "/foo/bar", 200, 200),
            Arguments.of("POST", "/foo/bar", "/foo/bar/baz", 200, 404),
            Arguments.of("POST", "/foo/bar/", "/foo/bar/baz", 200, 404),
            Arguments.of("POST", "/foo/bar/", "/foo/bar/baz", 200, 404),
            Arguments.of("POST", "/foo/bar/", "/foo/bar/", 200, 200),
            Arguments.of("POST", "/foo/bar/", "/foo/bar", 200, 200),
            Arguments.of("POST", "/foo/bar", "/foo/bar/", 200, 200),
            Arguments.of("POST", "/foo/bar/{baz}", "/foo/bar", 200, 404),
            Arguments.of("POST", "/foo/bar/{baz}", "/foo/bar/", 200, 404),
            Arguments.of("POST", "/foo/bar/{baz}", "/foo/bar/baz", 200, 200),
            Arguments.of("POST", "/foo/bar/{baz}/", "/foo/bar/baz/", 200, 200),
            Arguments.of("POST", "/foo/bar/{baz}/", "/foo/bar/baz", 200, 200),
            Arguments.of("POST", "/foo/bar/{baz}", "/foo/bar/baz/", 200, 200),
            Arguments.of("POST", "/foo/{bar}/baz", "/foo/bar/baz", 200, 200),
            Arguments.of("POST", "/foo/{bar}/baz/", "/foo/bar/baz/", 200, 200),
            Arguments.of("POST", "/foo/{bar}/baz", "/foo/bar/baz/", 200, 200),
            Arguments.of("POST", "/foo/{bar}/baz", "/foo/bar/baz/", 200, 200),
            Arguments.of("POST", "/foo/{bar}/baz/", "/foo/bar/baz", 200, 200),
            Arguments.of("POST", "/foo/{bar}/baz/", "/foo/bar/baz/baz", 200, 404),
            Arguments.of("POST", "/foo/{bar}/baz", "/foo/bar/baz/baz", 200, 404),
            Arguments.of("POST", "/foo/{bar}/baz/baz", "/foo/bar/baz", 200, 404),
            Arguments.of("POST", "/foo/{bar}/baz/baz", "/foo/bar/baz/", 200, 404)
        );
    }

    @ParameterizedTest
    @MethodSource("dataWhenIgnoreTrailingSlash")
    void processRequestWhenIgnoreTrailingSlash(String method, String route, String path, int responseCode, int expectedCode) throws Exception {
        // given
        var handlers = List.of(handler(method, route));
        var telemetry = NoopHttpServerTelemetry.INSTANCE;
        var telemetryFactory = Mockito.mock(HttpServerTelemetryFactory.class);
        when(telemetryFactory.get(any())).thenReturn(telemetry);
        var config = config(true);
        var handler = new PublicApiHandler(handlers, All.of(), config);

        // when
        var request = new PublicApiRequestImpl(method, path, "foo", "http", HttpHeaders.of(), Map.of(), HttpBody.empty());

        // then
        var routedRq = handler.route(request);
        var rs = routedRq.proceed(routedRq.request);
        assertThat(rs.code()).isEqualTo(expectedCode);
    }

    @Test
    void testWildcard() throws Exception {
        var handlers = All.of(
            handler("GET", "/baz"),
            handler("POST", "/*")
        );
        var config = config(false);
        var telemetryFactory = Mockito.mock(HttpServerTelemetryFactory.class);
        when(telemetryFactory.get(any())).thenReturn(NoopHttpServerTelemetry.INSTANCE);
        var handler = new PublicApiHandler(handlers, All.of(), config);

        var request = new PublicApiRequestImpl("POST", "/baz", "test", "http", HttpHeaders.of(), Map.of(), HttpBody.empty());
        var routedRq = handler.route(request);
        var rs = routedRq.proceed(routedRq.request);
        assertThat(rs.code()).isEqualTo(200);
    }

    private HttpServerConfig config(boolean ignoreTrailingSlash) {
        return new HttpServerConfig_Impl(
            8080,
            8085,
            "/metrics",
            "/system/readiness",
            "/system/liveness",
            ignoreTrailingSlash,
            10,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            false,
            Duration.ofMillis(100),
            new $HttpServerTelemetryConfig_ConfigValueExtractor.HttpServerTelemetryConfig_Impl(
                new $HttpServerTelemetryConfig_HttpServerLoggingConfig_ConfigValueExtractor.HttpServerLoggingConfig_Defaults(),
                new $HttpServerTelemetryConfig_HttpServerMetricsConfig_ConfigValueExtractor.HttpServerMetricsConfig_Defaults(),
                new $HttpServerTelemetryConfig_HttpServerTracingConfig_ConfigValueExtractor.HttpServerTracingConfig_Defaults()
            )
        );
    }

    private HttpServerRequestHandler handler(String method, String route) {
        return new HttpServerRequestHandlerImpl(method, route, (httpServerRequest) -> HttpServerResponse.of(200));
    }

    private record PublicApiRequestImpl(
        String method,
        String path,
        String hostName,
        String scheme,
        HttpHeaders headers,
        Map<String, ? extends Collection<String>> queryParams,
        HttpBodyInput body
    ) implements PublicApiRequest {
        @Override
        public long requestStartTime() {
            return 0;
        }
    }
}
