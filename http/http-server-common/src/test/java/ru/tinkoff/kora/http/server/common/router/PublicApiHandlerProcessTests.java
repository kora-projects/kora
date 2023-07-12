package ru.tinkoff.kora.http.server.common.router;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.HttpServerResponseSender;
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandlerImpl;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetry;

import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.argThat;
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
            Arguments.of("POST", "/foo/{bar}/baz/baz", "/foo/bar/baz/", 200, 404)
        );
    }

    @ParameterizedTest
    @MethodSource("dataWhenDefault")
    void processRequestWhenDefault(String method, String route, String path, int responseCode, int expectedCode) {
        // given
        var handlers = All.of(valueOf(handler(method, route)));
        var telemetry = Mockito.mock(HttpServerTelemetry.class);
        when(telemetry.get(any(), anyString())).thenReturn(mock(HttpServerTelemetry.HttpServerTelemetryContext.class));
        var config = config(false);
        var handler = new PublicApiHandler(handlers, All.of(), valueOf(telemetry), valueOf(config));

        // when
        var request = new PublicApiHandler.PublicApiRequest(method, path, "foo", "http", HttpHeaders.EMPTY, Map.of(), Flux.empty());
        var responseSender = mock(HttpServerResponseSender.class);
        when(responseSender.send(any())).thenReturn(Mono.just(new HttpServerResponseSender.Success(responseCode)));

        // then
        handler.process(request, responseSender);
        verify(responseSender).send(argThat(argument -> argument.code() == expectedCode));
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
        var handlers = All.of(valueOf(handler(method, route)));
        var telemetry = Mockito.mock(HttpServerTelemetry.class);
        when(telemetry.get(any(), anyString())).thenReturn(mock(HttpServerTelemetry.HttpServerTelemetryContext.class));
        var config = config(true);
        var handler = new PublicApiHandler(handlers, All.of(), valueOf(telemetry), valueOf(config));

        // when
        var request = new PublicApiHandler.PublicApiRequest(method, path, "foo", "http", HttpHeaders.EMPTY, Map.of(), Flux.empty());
        var responseSender = mock(HttpServerResponseSender.class);
        when(responseSender.send(any())).thenReturn(Mono.just(new HttpServerResponseSender.Success(responseCode)));

        // then
        handler.process(request, responseSender);
        verify(responseSender).send(argThat(argument -> argument.code() == expectedCode));
    }

    @Test
    void testWildcard() {
        var handlers = All.of(
            valueOf(handler("GET", "/baz")),
            valueOf(handler("POST", "/*"))
        );
        var telemetry = Mockito.mock(HttpServerTelemetry.class);
        when(telemetry.get(any(), anyString())).thenReturn(mock(HttpServerTelemetry.HttpServerTelemetryContext.class));
        var config = config(false);
        var handler = new PublicApiHandler(handlers, All.of(), valueOf(telemetry), valueOf(config));

        var responseSender = mock(HttpServerResponseSender.class);
        when(responseSender.send(any())).thenReturn(Mono.just(new HttpServerResponseSender.Success(200)));

        var request = new PublicApiHandler.PublicApiRequest("POST", "/baz", "test", "http", HttpHeaders.EMPTY, Map.of(), Flux.empty());
        handler.process(request, responseSender);

        verify(responseSender).send(argThat(argument -> argument.code() == 200));
    }

    private HttpServerConfig config(boolean ignoreTrailingSlash) {
        return new HttpServerConfig(null, null, null, null, null, ignoreTrailingSlash, null, null, null);
    }

    private HttpServerRequestHandler handler(String method, String route) {
        return new HttpServerRequestHandlerImpl(method, route, httpServerRequest -> Mono.just(new SimpleHttpServerResponse(200, "application/octet-stream", HttpHeaders.EMPTY, null)));
    }

    private <T> ValueOf<T> valueOf(T object) {
        return new ValueOf<>() {
            @Override
            public T get() {
                return object;
            }

            @Override
            public Mono<Void> refresh() {
                return Mono.empty();
            }
        };
    }
}
