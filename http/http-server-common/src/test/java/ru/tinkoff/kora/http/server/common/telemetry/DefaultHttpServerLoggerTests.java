package ru.tinkoff.kora.http.server.common.telemetry;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.core.JsonGenerator;
import com.typesafe.config.ConfigFactory;
import jakarta.annotation.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.config.common.extractor.SetConfigValueExtractor;
import ru.tinkoff.kora.config.common.extractor.StringConfigValueExtractor;
import ru.tinkoff.kora.config.common.origin.SimpleConfigOrigin;
import ru.tinkoff.kora.config.hocon.HoconConfigFactory;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.cookie.Cookie;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.telemetry.impl.DefaultHttpServerLogger;
import ru.tinkoff.kora.logging.common.arg.StructuredArgumentWriter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class DefaultHttpServerLoggerTests {

    private static final MutableHttpHeaders HEADERS = HttpHeaders.of("authorization", "auth", "OtherHeader", "val");
    private static final Map<String, ? extends Collection<String>> QUERY_PARAMS = new LinkedHashMap<>() {{
        put("a", List.of("5"));
        put("sessionid", List.of("abc"));
    }};
    private static final String EXCEPTION_MESSAGE = "SomeError";
    private static final Exception EXCEPTION = new RuntimeException(EXCEPTION_MESSAGE);

    private static final String MASKED_HEADERS_STR = "authorization: <test-mask>\notherheader: val";
    private static final String MASKED_QUERY_PARAMS_STR = "a=5&sessionid=<test-mask>";

    @SuppressWarnings("unchecked")
    private final Appender<ILoggingEvent> mockAppender = mock(Appender.class);

    private static final ConfigValueExtractor<HttpServerTelemetryConfig.HttpServerLoggingConfig> logConfigExtractor = new $HttpServerTelemetryConfig_HttpServerLoggingConfig_ConfigValueExtractor(
        new SetConfigValueExtractor<>(new StringConfigValueExtractor())
    );

    private static HttpServerTelemetryConfig.HttpServerLoggingConfig config(String hocon) {
        var config = ConfigFactory.parseString("""
            maskQueries = ["sessionId"]
            maskHeaders = ["authorization"]
            mask = "<test-mask>"
            """ + hocon);
        return logConfigExtractor.extract(HoconConfigFactory.fromHocon(new SimpleConfigOrigin("test"), config).root());
    }

    private static Stream<Arguments> getLogStartTestsData() {
        return Stream.of(
            Arguments.of(Level.DEBUG, QUERY_PARAMS, HEADERS, false,
                List.of("POST /path/1", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.DEBUG, QUERY_PARAMS, HEADERS, true,
                List.of("POST /path/{id}", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.DEBUG, QUERY_PARAMS, HEADERS, null,
                List.of("POST /path/{id}", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.TRACE, QUERY_PARAMS, HEADERS, null,
                List.of("POST /path/1", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.INFO, QUERY_PARAMS, HEADERS, false,
                List.of("POST /path/1").toArray()),
            Arguments.of(Level.INFO, QUERY_PARAMS, HEADERS, true,
                List.of("POST /path/{id}").toArray()),
            Arguments.of(Level.INFO, QUERY_PARAMS, HEADERS, null,
                List.of("POST /path/{id}").toArray()),
            Arguments.of(Level.DEBUG, Map.of(), HEADERS, true,
                Arrays.asList("POST /path/{id}", null, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.DEBUG, QUERY_PARAMS, null, true,
                List.of("POST /path/{id}", MASKED_QUERY_PARAMS_STR).toArray()),
            Arguments.of(Level.DEBUG, Map.of(), null, true,
                List.of("POST /path/{id}").toArray())
        );
    }

    @ParameterizedTest
    @MethodSource("getLogStartTestsData")
    public void logStartTests(Level level, Map<String, ? extends Collection<String>> queryParams, HttpHeaders headers, Boolean pathTemplate, Object... expectedArgs) throws IOException {
        expectLogLevel(level);
        var config = pathTemplate == null ? config("") : config("pathTemplate = " + pathTemplate);
        var logger = new DefaultHttpServerLogger(config);

        logger.logStart(request("POST", "/path/1", "/path/{id}", queryParams, headers));

        var event = getLoggedEvent();

        assertThat(event.getLevel().levelInt).isGreaterThanOrEqualTo(level.levelInt); // логируем с текущим уровнем или более высоким
        assertThat(event.getMessage()).isEqualTo("HttpServer received request");
        assertThat(event.getKeyValuePairs().getFirst().key).isEqualTo("httpRequest");
        var httpRequest = (StructuredArgumentWriter) event.getKeyValuePairs().getFirst().value;
        var gen = Mockito.mock(JsonGenerator.class);
        httpRequest.writeTo(gen);
        verify(gen).writeStringField("operation", expectedArgs[0].toString());
        if (expectedArgs.length > 1 && expectedArgs[1] != null) {
            verify(gen).writeStringField("queryParams", expectedArgs[1].toString());
        }
        if (expectedArgs.length > 2 && expectedArgs[2] != null) {
            verify(gen).writeStringField("headers", expectedArgs[2].toString());
        }
    }

    private static Stream<Arguments> getLogEndNoExceptionTestsData() {
        return Stream.of(
            Arguments.of(Level.DEBUG, HEADERS, false, "POST /path/1", MASKED_HEADERS_STR),
            Arguments.of(Level.DEBUG, HEADERS, true, "POST /path/{id}", MASKED_HEADERS_STR),
            Arguments.of(Level.DEBUG, HEADERS, null, "POST /path/{id}", MASKED_HEADERS_STR),
            Arguments.of(Level.TRACE, HEADERS, null, "POST /path/1", MASKED_HEADERS_STR),
            Arguments.of(Level.INFO, HEADERS, false, "POST /path/1", null),
            Arguments.of(Level.INFO, HEADERS, true, "POST /path/{id}", null),
            Arguments.of(Level.INFO, HEADERS, null, "POST /path/{id}", null),
            Arguments.of(Level.DEBUG, HEADERS, null, "POST /path/{id}", MASKED_HEADERS_STR),
            Arguments.of(Level.DEBUG, null, null, "POST /path/{id}", null)
        );
    }

    @ParameterizedTest
    @MethodSource("getLogEndNoExceptionTestsData")
    public void logEndNoExceptionTests(Level level, HttpHeaders headers, Boolean pathTemplate, String expectedOperation, @Nullable String expectedHeaders) throws IOException {
        expectLogLevel(level);

        var config = pathTemplate == null ? config("") : config("pathTemplate = " + pathTemplate);
        var logger = new DefaultHttpServerLogger(config);

        logger.logEnd(request("POST", "/path/1", "/path/{id}", Map.of(), HttpHeaders.empty()), 200, HttpResultCode.SUCCESS, 100, headers, null);

        var event = getLoggedEvent();

        assertThat(event.getLevel().levelInt).isGreaterThanOrEqualTo(level.levelInt); // логируем с текущим уровнем или более высоким
        assertThat(event.getMessage()).isEqualTo("HttpServer responded");

        assertThat(event.getKeyValuePairs().getFirst().key).isEqualTo("httpResponse");
        var httpResponse = (StructuredArgumentWriter) event.getKeyValuePairs().getFirst().value;
        var gen = Mockito.mock(JsonGenerator.class);
        httpResponse.writeTo(gen);
        verify(gen).writeNumberField("statusCode", 200);
        verify(gen).writeStringField("operation", expectedOperation);
        if (expectedHeaders == null) {
            verify(gen, never()).writeStringField(eq("headers"), any());
        } else {
            verify(gen).writeStringField(eq("headers"), eq(expectedHeaders));
        }

    }

    private static Stream<Arguments> getLogEndWithExceptionTestsData() {
        return Stream.of(
            Arguments.of(false, HEADERS, false, "POST /path/1", MASKED_HEADERS_STR),
            Arguments.of(false, HEADERS, true, "POST /path/{id}", MASKED_HEADERS_STR),
            Arguments.of(false, HEADERS, null, "POST /path/{id}", MASKED_HEADERS_STR),
            Arguments.of(false, null, null, "POST /path/{id}", null),
            Arguments.of(true, HEADERS, false, "POST /path/1", MASKED_HEADERS_STR),
            Arguments.of(true, HEADERS, true, "POST /path/{id}", MASKED_HEADERS_STR),
            Arguments.of(true, HEADERS, null, "POST /path/{id}", MASKED_HEADERS_STR),
            Arguments.of(true, null, null, "POST /path/{id}", null)
        );
    }

    @ParameterizedTest
    @MethodSource("getLogEndWithExceptionTestsData")
    public void logEndWithExceptionTests(
        boolean stacktrace,
        HttpHeaders rsHeaders,
        Boolean pathTemplate,
        String expectedOperation,
        @Nullable String expectedHeaders
    ) throws IOException {
        expectLogLevel(Level.DEBUG);

        var configStr = "stacktrace = " + stacktrace + "\n";
        var config = pathTemplate == null ? config(configStr) : config(configStr + "pathTemplate = " + pathTemplate);
        var logger = new DefaultHttpServerLogger(config);

        logger.logEnd(request("POST", "/path/1", "/path/{id}", Map.of(), HttpHeaders.empty()), 200, HttpResultCode.SUCCESS, 100, rsHeaders, EXCEPTION);

        var event = getLoggedEvent();
        if (stacktrace) {
            assertThat(event.getMessage()).isEqualTo("HttpServer responded error");
            assertThat(event.getThrowableProxy()).isNotNull();
        } else {
            assertThat(event.getMessage()).isEqualTo("HttpServer responded error due to: {}");
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getArgumentArray()[0]).isEqualTo(EXCEPTION_MESSAGE);
        }
        assertThat(event.getKeyValuePairs().getFirst().key).isEqualTo("httpResponse");
        var httpResponse = (StructuredArgumentWriter) event.getKeyValuePairs().getFirst().value;
        var gen = Mockito.mock(JsonGenerator.class);
        httpResponse.writeTo(gen);
        verify(gen).writeNumberField("statusCode", 200);
        verify(gen).writeStringField("operation", expectedOperation);
        if (expectedHeaders == null) {
            verify(gen, never()).writeStringField(eq("headers"), any());
        } else {
            verify(gen).writeStringField(eq("headers"), eq(expectedHeaders));
        }
    }

    private LoggingEvent getLoggedEvent() {
        ArgumentCaptor<LoggingEvent> captor = ArgumentCaptor.forClass(LoggingEvent.class);
        verify(mockAppender).doAppend(captor.capture());
        final LoggingEvent event = captor.getValue();
        return event;
    }

    private void expectLogLevel(Level level) {
        var root = (Logger) LoggerFactory.getLogger(HttpServer.class);
        root.addAppender(mockAppender);
        root.setLevel(level);
    }

    private HttpServerRequest request(String method, String path, String route, Map<String, ? extends Collection<String>> queryParams, HttpHeaders headers) {
        return new HttpServerRequest() {
            @Override
            public String method() {
                return method;
            }

            @Override
            public String path() {
                return path;
            }

            @Override
            public String route() {
                return route;
            }

            @Override
            public HttpHeaders headers() {
                return headers;
            }

            @Override
            public List<Cookie> cookies() {
                return List.of();
            }

            @Override
            public Map<String, ? extends Collection<String>> queryParams() {
                return queryParams;
            }

            @Override
            public Map<String, String> pathParams() {
                return Map.of();
            }

            @Override
            public HttpBodyInput body() {
                return HttpBody.empty();
            }
        };
    }
}
