package ru.tinkoff.kora.http.server.common.router;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.http.server.common.$HttpServerConfig_ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandlerImpl;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

class PublicApiHandlerTest {

    @Test
    void diffMethodSameRouteTemplateAndPathSuccess() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}/baz"),
            handler("GET", "/foo/bar/{otherVariable}/baz")
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, List.of(), null, config);
    }

    @Test
    void sameMethodSameRouteTemplateAndPathFail() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}/baz"),
            handler("POST", "/foo/bar/{otherVariable}/baz")
        );
        var config = config(false);
        Assertions.assertThatThrownBy(() -> new PublicApiHandler(handlers, List.of(), null, config));
    }


    @Test
    void diffMethodSameRouteTemplateSuccess() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}"),
            handler("GET", "/foo/bar/{otherVariable}")
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, List.of(), null, config);
    }

    @Test
    void sameMethodSameRouteTemplateFail() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}"),
            handler("POST", "/foo/bar/{otherVariable}")
        );
        var config = config(false);
        Assertions.assertThatThrownBy(() -> new PublicApiHandler(handlers, List.of(), null, config));
    }

    @Test
    void sameMethodSameRouteTemplateTrailingSlashSuccess() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}"),
            handler("POST", "/foo/bar/{otherVariable}/")
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, List.of(), null, config);
    }

    @Test
    void sameMethodSameRouteTemplateTrailingSlashWhenIgnoreTrailingSlashFail() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}"),
            handler("POST", "/foo/bar/{otherVariable}/")
        );
        var config = config(true);
        var handler = new PublicApiHandler(handlers, List.of(), null, config);
    }

    @Test
    void sameMethodSameRouteTemplateButTrailingSlashSuccess() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}/baz/"),
            handler("POST", "/foo/bar/{otherVariable}/baz")
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, List.of(), null, config);
    }

    @Test
    void sameMethodSameRouteTemplateButTrailingSlashWhenIgnoreTrailingSlashFail() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}/baz/"),
            handler("POST", "/foo/bar/{otherVariable}/baz")
        );
        var config = config(true);
        var handler = new PublicApiHandler(handlers, List.of(), null, config);
    }

    @Test
    void diffMethodSameRouteSuccess() {
        var handlers = List.of(
            handler("POST", "/foo/bar"),
            handler("GET", "/foo/bar")
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, List.of(), null, config);
    }

    @Test
    void sameMethodSameRouteFail() {
        var handlers = List.of(
            handler("POST", "/foo/bar"),
            handler("POST", "/foo/bar")
        );
        var config = config(false);
        Assertions.assertThatThrownBy(() -> new PublicApiHandler(handlers, List.of(), null, config));
    }

    @Test
    void sameMethodSameRouteTrailingSlashSuccess() {
        // given
        var handlers = List.of(
            handler("POST", "/foo/bar"),
            handler("POST", "/foo/bar/")
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, List.of(), null, config);
    }

    @Test
    void sameMethodSameRouteTrailingSlashWhenIgnoreTrailingSlashFail() {
        // given
        var handlers = List.of(
            handler("POST", "/foo/bar"),
            handler("POST", "/foo/bar/")
        );
        var config = config(true);
        var handler = new PublicApiHandler(handlers, List.of(), null, config);
    }

    private HttpServerConfig config(boolean ignoreTrailingSlash) {
        return new $HttpServerConfig_ConfigValueExtractor.HttpServerConfig_Impl(0, 0, "/metrics", "/system/readiness", "/system/liveness", ignoreTrailingSlash, 1, 10, Duration.ofMillis(1));
    }

    private HttpServerRequestHandler handler(String method, String route) {
        return new HttpServerRequestHandlerImpl(method, route, (ctx, httpServerRequest) -> CompletableFuture.completedFuture(HttpServerResponse.of(200)));
    }
}
