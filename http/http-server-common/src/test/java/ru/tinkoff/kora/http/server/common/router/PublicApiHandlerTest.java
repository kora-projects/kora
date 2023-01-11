package ru.tinkoff.kora.http.server.common.router;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.HttpServerResponseSender;
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandlerImpl;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetry;

import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class PublicApiHandlerTest {

    @Test
    void testEqualTemplatesWithDifferentMethods() {
        var handlers = All.of(
            valueOf(handler("POST", "/some/path/{variable}/test")),
            valueOf(handler("GET", "/some/path/{otherVariable}/test"))
        );
        var handler = new PublicApiHandler(handlers, All.of(), null);
    }

    @Test
    void testSameRouteHandlers() {
        var handlers = All.of(
            valueOf(handler("POST", "/some/path/{variable}/test")),
            valueOf(handler("POST", "/some/path/{otherVariable}/test"))
        );
        Assertions.assertThatThrownBy(() -> new PublicApiHandler(handlers, All.of(), null));
    }

    @Test
    void testWildcard() {
        var handlers = All.of(
            valueOf(handler("GET", "/test")),
            valueOf(handler("POST", "/*"))
        );
        var telemetry = Mockito.mock(HttpServerTelemetry.class);
        when(telemetry.get(any(), anyString())).thenReturn(mock(HttpServerTelemetry.HttpServerTelemetryContext.class));
        var handler = new PublicApiHandler(handlers, All.of(), valueOf(telemetry));

        var responseSender = mock(HttpServerResponseSender.class);
        when(responseSender.send(any())).thenReturn(Mono.just(new HttpServerResponseSender.Success(200)));

        var request = new PublicApiHandler.PublicApiRequest("POST", "/test", "test", "http", HttpHeaders.EMPTY, Map.of(), Flux.empty());
        handler.process(request, responseSender);

        verify(responseSender).send(argThat(argument -> argument.code() == 200));
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
