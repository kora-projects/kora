package ru.tinkoff.kora.http.server.common.router;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.server.common.$HttpServerConfig_ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandlerImpl;

import java.time.Duration;

class PublicApiHandlerTest {

    @Test
    void diffMethodSameRouteTemplateAndPathSuccess() {
        var handlers = All.of(
            valueOf(handler("POST", "/foo/bar/{variable}/baz")),
            valueOf(handler("GET", "/foo/bar/{otherVariable}/baz"))
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, All.of(), null, valueOf(config));
    }

    @Test
    void sameMethodSameRouteTemplateAndPathFail() {
        var handlers = All.of(
            valueOf(handler("POST", "/foo/bar/{variable}/baz")),
            valueOf(handler("POST", "/foo/bar/{otherVariable}/baz"))
        );
        var config = config(false);
        Assertions.assertThatThrownBy(() -> new PublicApiHandler(handlers, All.of(), null, valueOf(config)));
    }


    @Test
    void diffMethodSameRouteTemplateSuccess() {
        var handlers = All.of(
            valueOf(handler("POST", "/foo/bar/{variable}")),
            valueOf(handler("GET", "/foo/bar/{otherVariable}"))
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, All.of(), null, valueOf(config));
    }

    @Test
    void sameMethodSameRouteTemplateFail() {
        var handlers = All.of(
            valueOf(handler("POST", "/foo/bar/{variable}")),
            valueOf(handler("POST", "/foo/bar/{otherVariable}"))
        );
        var config = config(false);
        Assertions.assertThatThrownBy(() -> new PublicApiHandler(handlers, All.of(), null, valueOf(config)));
    }

    @Test
    void sameMethodSameRouteTemplateTrailingSlashSuccess() {
        var handlers = All.of(
            valueOf(handler("POST", "/foo/bar/{variable}")),
            valueOf(handler("POST", "/foo/bar/{otherVariable}/"))
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, All.of(), null, valueOf(config));
    }

    @Test
    void sameMethodSameRouteTemplateTrailingSlashWhenIgnoreTrailingSlashFail() {
        var handlers = All.of(
            valueOf(handler("POST", "/foo/bar/{variable}")),
            valueOf(handler("POST", "/foo/bar/{otherVariable}/"))
        );
        var config = config(true);
        var handler = new PublicApiHandler(handlers, All.of(), null, valueOf(config));
    }

    @Test
    void sameMethodSameRouteTemplateButTrailingSlashSuccess() {
        var handlers = All.of(
            valueOf(handler("POST", "/foo/bar/{variable}/baz/")),
            valueOf(handler("POST", "/foo/bar/{otherVariable}/baz"))
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, All.of(), null, valueOf(config));
    }

    @Test
    void sameMethodSameRouteTemplateButTrailingSlashWhenIgnoreTrailingSlashFail() {
        var handlers = All.of(
            valueOf(handler("POST", "/foo/bar/{variable}/baz/")),
            valueOf(handler("POST", "/foo/bar/{otherVariable}/baz"))
        );
        var config = config(true);
        var handler = new PublicApiHandler(handlers, All.of(), null, valueOf(config));
    }

    @Test
    void diffMethodSameRouteSuccess() {
        var handlers = All.of(
            valueOf(handler("POST", "/foo/bar")),
            valueOf(handler("GET", "/foo/bar"))
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, All.of(), null, valueOf(config));
    }

    @Test
    void sameMethodSameRouteFail() {
        var handlers = All.of(
            valueOf(handler("POST", "/foo/bar")),
            valueOf(handler("POST", "/foo/bar"))
        );
        var config = config(false);
        Assertions.assertThatThrownBy(() -> new PublicApiHandler(handlers, All.of(), null, valueOf(config)));
    }

    @Test
    void sameMethodSameRouteTrailingSlashSuccess() {
        // given
        var handlers = All.of(
            valueOf(handler("POST", "/foo/bar")),
            valueOf(handler("POST", "/foo/bar/"))
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, All.of(), null, valueOf(config));
    }

    @Test
    void sameMethodSameRouteTrailingSlashWhenIgnoreTrailingSlashFail() {
        // given
        var handlers = All.of(
            valueOf(handler("POST", "/foo/bar")),
            valueOf(handler("POST", "/foo/bar/"))
        );
        var config = config(true);
        var handler = new PublicApiHandler(handlers, All.of(), null, valueOf(config));
    }

    private HttpServerConfig config(boolean ignoreTrailingSlash) {
        return new $HttpServerConfig_ConfigValueExtractor.HttpServerConfig_Impl(0, 0, "/metrics", "/system/readiness", "/system/liveness", ignoreTrailingSlash, 1, 10, Duration.ofMillis(1));
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
