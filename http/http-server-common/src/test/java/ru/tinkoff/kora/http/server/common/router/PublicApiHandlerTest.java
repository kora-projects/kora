package ru.tinkoff.kora.http.server.common.router;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.http.server.common.$HttpServerConfig_ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandlerImpl;
import ru.tinkoff.kora.http.server.common.telemetry.$HttpServerLoggerConfig_ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.telemetry.$HttpServerTelemetryConfig_ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryFactory;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_MetricsConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_TracingConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class PublicApiHandlerTest {
    private HttpServerTelemetryFactory telemetryFactory = Mockito.mock(HttpServerTelemetryFactory.class);

    @Test
    void diffMethodSameRouteTemplateAndPathSuccess() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}/baz"),
            handler("GET", "/foo/bar/{otherVariable}/baz")
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, List.of(), telemetryFactory, config);
    }

    @Test
    void sameMethodSameRouteTemplateAndPathFail() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}/baz"),
            handler("POST", "/foo/bar/{otherVariable}/baz")
        );
        var config = config(false);
        Assertions.assertThatThrownBy(() -> new PublicApiHandler(handlers, List.of(), telemetryFactory, config));
    }


    @Test
    void diffMethodSameRouteTemplateSuccess() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}"),
            handler("GET", "/foo/bar/{otherVariable}")
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, List.of(), telemetryFactory, config);
    }

    @Test
    void sameMethodSameRouteTemplateFail() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}"),
            handler("POST", "/foo/bar/{otherVariable}")
        );
        var config = config(false);
        Assertions.assertThatThrownBy(() -> new PublicApiHandler(handlers, List.of(), telemetryFactory, config));
    }

    @Test
    void sameMethodSameRouteTemplateTrailingSlashSuccess() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}"),
            handler("POST", "/foo/bar/{otherVariable}/")
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, List.of(), telemetryFactory, config);
    }

    @Test
    void sameMethodSameRouteTemplateTrailingSlashWhenIgnoreTrailingSlashFail() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}"),
            handler("POST", "/foo/bar/{otherVariable}/")
        );
        var config = config(true);
        Assertions.assertThatThrownBy(() -> new PublicApiHandler(handlers, List.of(), telemetryFactory, config));
    }

    @Test
    void sameMethodSameRouteTemplateButTrailingSlashSuccess() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}/baz/"),
            handler("POST", "/foo/bar/{otherVariable}/baz")
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, List.of(), telemetryFactory, config);
    }

    @Test
    void sameMethodSameRouteTemplateButTrailingSlashWhenIgnoreTrailingSlashFail() {
        var handlers = List.of(
            handler("POST", "/foo/bar/{variable}/baz/"),
            handler("POST", "/foo/bar/{otherVariable}/baz")
        );
        var config = config(true);
        Assertions.assertThatThrownBy(() -> new PublicApiHandler(handlers, List.of(), telemetryFactory, config));
    }

    @Test
    void diffMethodSameRouteSuccess() {
        var handlers = List.of(
            handler("POST", "/foo/bar"),
            handler("GET", "/foo/bar")
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, List.of(), telemetryFactory, config);
    }

    @Test
    void sameMethodSameRouteFail() {
        var handlers = List.of(
            handler("POST", "/foo/bar"),
            handler("POST", "/foo/bar")
        );
        var config = config(false);
        Assertions.assertThatThrownBy(() -> new PublicApiHandler(handlers, List.of(), telemetryFactory, config));
    }

    @Test
    void sameMethodSameRouteTrailingSlashSuccess() {
        // given
        var handlers = List.of(
            handler("POST", "/foo/bar"),
            handler("POST", "/foo/bar/")
        );
        var config = config(false);
        var handler = new PublicApiHandler(handlers, List.of(), telemetryFactory, config);
    }

    @Test
    void sameMethodSameRouteTrailingSlashWhenIgnoreTrailingSlashFail() {
        // given
        var handlers = List.of(
            handler("POST", "/foo/bar"),
            handler("POST", "/foo/bar/")
        );
        var config = config(true);
        Assertions.assertThatThrownBy(() -> new PublicApiHandler(handlers, List.of(), telemetryFactory, config));
    }

    private HttpServerConfig config(boolean ignoreTrailingSlash) {
        return new $HttpServerConfig_ConfigValueExtractor.HttpServerConfig_Impl(
            0,
            0,
            "/metrics",
            "/system/readiness",
            "/system/liveness",
            ignoreTrailingSlash,
            1,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            false,
            Duration.ofMillis(1),
            new $HttpServerTelemetryConfig_ConfigValueExtractor.HttpServerTelemetryConfig_Impl(
                new $HttpServerLoggerConfig_ConfigValueExtractor.HttpServerLoggerConfig_Impl(true, Collections.emptySet(), Collections.emptySet(), "***", true, false),
                new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true, Map.of()),
                new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(true, TelemetryConfig.MetricsConfig.DEFAULT_SLO, Map.of())
            ));
    }

    private HttpServerRequestHandler handler(String method, String route) {
        return new HttpServerRequestHandlerImpl(method, route, (ctx, httpServerRequest) -> HttpServerResponse.of(200));
    }
}
