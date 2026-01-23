package ru.tinkoff.kora.http.server.common.router;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.common.util.Size;
import ru.tinkoff.kora.http.server.common.$HttpServerConfig_ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandlerImpl;
import ru.tinkoff.kora.http.server.common.telemetry.*;

import java.time.Duration;
import java.util.List;

class HttpServerHandlerTest {
    private HttpServerTelemetryFactory telemetryFactory = Mockito.mock(HttpServerTelemetryFactory.class);

    @Test
    void diffMethodSameRouteTemplateAndPathSuccess() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}/baz"),
            handler("GET", "/foo/bar/{otherVariable}/baz")
        );
        var config = config(false);
        var handler = new HttpServerHandler(handlers, List.of(), config);
    }

    @Test
    void sameMethodSameRouteTemplateAndPathFail() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}/baz"),
            handler("POST", "/foo/bar/{otherVariable}/baz")
        );
        var config = config(false);
        Assertions.assertThatThrownBy(() -> new HttpServerHandler(handlers, List.of(), config));
    }


    @Test
    void diffMethodSameRouteTemplateSuccess() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}"),
            handler("GET", "/foo/bar/{otherVariable}")
        );
        var config = config(false);
        var handler = new HttpServerHandler(handlers, List.of(), config);
    }

    @Test
    void sameMethodSameRouteTemplateFail() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}"),
            handler("POST", "/foo/bar/{otherVariable}")
        );
        var config = config(false);
        Assertions.assertThatThrownBy(() -> new HttpServerHandler(handlers, List.of(), config));
    }

    @Test
    void sameMethodSameRouteTemplateTrailingSlashSuccess() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}"),
            handler("POST", "/foo/bar/{otherVariable}/")
        );
        var config = config(false);
        var handler = new HttpServerHandler(handlers, List.of(), config);
    }

    @Test
    void sameMethodSameRouteTemplateTrailingSlashWhenIgnoreTrailingSlashFail() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}"),
            handler("POST", "/foo/bar/{otherVariable}/")
        );
        var config = config(true);
        Assertions.assertThatThrownBy(() -> new HttpServerHandler(handlers, List.of(), config));
    }

    @Test
    void sameMethodSameRouteTemplateButTrailingSlashSuccess() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}/baz/"),
            handler("POST", "/foo/bar/{otherVariable}/baz")
        );
        var config = config(false);
        var handler = new HttpServerHandler(handlers, List.of(), config);
    }

    @Test
    void sameMethodSameRouteTemplateButTrailingSlashWhenIgnoreTrailingSlashFail() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}/baz/"),
            handler("POST", "/foo/bar/{otherVariable}/baz")
        );
        var config = config(true);
        Assertions.assertThatThrownBy(() -> new HttpServerHandler(handlers, List.of(), config));
    }

    @Test
    void diffMethodSameRouteSuccess() {
        var handlers = List.of(
            handler("POST", "/foo/bar"),
            handler("GET", "/foo/bar")
        );
        var config = config(false);
        var handler = new HttpServerHandler(handlers, List.of(), config);
    }

    @Test
    void sameMethodSameRouteFail() {
        var handlers = List.of(
            handler("POST", "/foo/bar"),
            handler("POST", "/foo/bar")
        );
        var config = config(false);
        Assertions.assertThatThrownBy(() -> new HttpServerHandler(handlers, List.of(), config));
    }

    @Test
    void sameMethodSameRouteTrailingSlashSuccess() {
        // given
        var handlers = List.of(
            handler("POST", "/foo/bar"),
            handler("POST", "/foo/bar/")
        );
        var config = config(false);
        var handler = new HttpServerHandler(handlers, List.of(), config);
    }

    @Test
    void sameMethodSameRouteTrailingSlashWhenIgnoreTrailingSlashFail() {
        // given
        var handlers = List.of(
            handler("POST", "/foo/bar"),
            handler("POST", "/foo/bar/")
        );
        var config = config(true);
        Assertions.assertThatThrownBy(() -> new HttpServerHandler(handlers, List.of(), config));
    }

    private HttpServerConfig config(boolean ignoreTrailingSlash) {
        return new $HttpServerConfig_ConfigValueExtractor.HttpServerConfig_Impl(
            0,
            ignoreTrailingSlash,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            false,
            Duration.ofMillis(1),
            new $HttpServerTelemetryConfig_ConfigValueExtractor.HttpServerTelemetryConfig_Impl(
                new $HttpServerTelemetryConfig_HttpServerLoggingConfig_ConfigValueExtractor.HttpServerLoggingConfig_Defaults(),
                new $HttpServerTelemetryConfig_HttpServerMetricsConfig_ConfigValueExtractor.HttpServerMetricsConfig_Defaults(),
                new $HttpServerTelemetryConfig_HttpServerTracingConfig_ConfigValueExtractor.HttpServerTracingConfig_Defaults()
            ),
            Size.ofBytesBinary(1024)
        );
    }

    private HttpServerRequestHandler handler(String method, String route) {
        return new HttpServerRequestHandlerImpl(method, route, (httpServerRequest) -> HttpServerResponse.of(200));
    }
}
