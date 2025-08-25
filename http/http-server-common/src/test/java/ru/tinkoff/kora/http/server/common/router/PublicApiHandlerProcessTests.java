package ru.tinkoff.kora.http.server.common.router;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.$HttpServerConfig_ConfigValueExtractor.HttpServerConfig_Impl;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandlerImpl;
import ru.tinkoff.kora.http.server.common.telemetry.$HttpServerLoggerConfig_ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.telemetry.$HttpServerTelemetryConfig_ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetry;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryFactory;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_MetricsConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_TracingConfig_ConfigValueExtractor;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
    void processRequestWhenDefault(String method, String route, String path, int responseCode, int expectedCode) {
        // given
        var handlers = List.of(handler(method, route));
        var telemetry = Mockito.mock(HttpServerTelemetry.class);
        when(telemetry.get(any(), anyString())).thenReturn(mock(HttpServerTelemetry.HttpServerTelemetryContext.class));
        var telemetryFactory = Mockito.mock(HttpServerTelemetryFactory.class);
        when(telemetryFactory.get(any())).thenReturn(telemetry);
        var config = config(false);
        var handler = new PublicApiHandler(handlers, All.of(), telemetryFactory, config);

        // when
        var request = new PublicApiRequestImpl(method, path, "foo", "http", HttpHeaders.of(), Map.of(), HttpBody.empty());

        // then
        var rs = handler.process(Context.clear(), request);
        var httpRs = rs.response();
        assertThat(httpRs.code()).isEqualTo(expectedCode);
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
    void processRequestWhenIgnoreTrailingSlash(String method, String route, String path, int responseCode, int expectedCode) {
        // given
        var handlers = List.of(handler(method, route));
        var telemetry = Mockito.mock(HttpServerTelemetry.class);
        when(telemetry.get(any(), anyString())).thenReturn(mock(HttpServerTelemetry.HttpServerTelemetryContext.class));
        var telemetryFactory = Mockito.mock(HttpServerTelemetryFactory.class);
        when(telemetryFactory.get(any())).thenReturn(telemetry);
        var config = config(true);
        var handler = new PublicApiHandler(handlers, All.of(), telemetryFactory, config);

        // when
        var request = new PublicApiRequestImpl(method, path, "foo", "http", HttpHeaders.of(), Map.of(), HttpBody.empty());

        // then
        var rs = handler.process(Context.clear(), request);
        var httpRs = rs.response();
        assertThat(httpRs.code()).isEqualTo(expectedCode);
    }

    @Test
    void testWildcard() {
        var handlers = All.of(
            handler("GET", "/baz"),
            handler("POST", "/*")
        );
        var telemetry = Mockito.mock(HttpServerTelemetry.class);
        when(telemetry.get(any(), anyString())).thenReturn(mock(HttpServerTelemetry.HttpServerTelemetryContext.class));
        var config = config(false);
        var telemetryFactory = Mockito.mock(HttpServerTelemetryFactory.class);
        when(telemetryFactory.get(any())).thenReturn(telemetry);
        var handler = new PublicApiHandler(handlers, All.of(), telemetryFactory, config);

        var request = new PublicApiRequestImpl("POST", "/baz", "test", "http", HttpHeaders.of(), Map.of(), HttpBody.empty());
        var rs = handler.process(Context.clear(), request);
        var httpRs = rs.response();

        assertThat(httpRs.code()).isEqualTo(200);
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
                new $HttpServerLoggerConfig_ConfigValueExtractor.HttpServerLoggerConfig_Defaults(),
                new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true),
                new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(null, null)
            )
        );
    }

    private HttpServerRequestHandler handler(String method, String route) {
        return new HttpServerRequestHandlerImpl(method, route, (ctx, httpServerRequest) -> HttpServerResponse.of(200));
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
    }
}
