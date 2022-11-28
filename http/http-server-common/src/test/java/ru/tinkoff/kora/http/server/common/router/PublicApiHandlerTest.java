package ru.tinkoff.kora.http.server.common.router;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.http.server.common.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandlerImpl;

class PublicApiHandlerTest {

    @Test
    void testEqualTemplatesWithDifferentMethods() {
        var handlers = All.of(
            handler("POST", "/some/path/{variable}/test"),
            handler("GET", "/some/path/{otherVariable}/test")
        );
        var handler = new PublicApiHandler(handlers, All.of(), null);

    }

    @Test
    void testSameRouteHandlers() {
        var handlers = All.of(
            handler("POST", "/some/path/{variable}/test"),
            handler("POST", "/some/path/{otherVariable}/test")
        );
        Assertions.assertThatThrownBy(() -> new PublicApiHandler(handlers, All.of(), null));

    }

    private ValueOf<HttpServerRequestHandler> handler(String method, String route) {
        return new ValueOf<>() {
            @Override
            public HttpServerRequestHandler get() {
                return new HttpServerRequestHandlerImpl(method, route, null);
            }

            @Override
            public Mono<Void> refresh() {
                return Mono.empty();
            }
        };
    }
}
