package ru.tinkoff.kora.http.client.common.telemetry;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.spi.DefaultLoggingEventBuilder;
import org.slf4j.spi.LoggingEventBuilder;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.telemetry.impl.DefaultHttpClientLogger;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;
import ru.tinkoff.kora.logging.common.arg.StructuredArgumentWriter;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;

public class DefaultHttpClientLoggerTests {

    private static final MutableHttpHeaders HEADERS = HttpHeaders.of("authorization", "auth", "OtherHeader", "val");
    private static final String QUERY_PARAMS_STR = "a=5&sessionid=abc";
    private static final String BODY = "body";

    private static final String MASKED_HEADERS_STR = "authorization: ***\notherheader: val";
    private static final String MASKED_QUERY_PARAMS_STR = "a=5&sessionid=***";

    private static final Set<String> MASKED_QUERY_PARAMS = Set.of("sessionId");
    private static final Set<String> MASKED_HEADERS = Set.of("authorization");

    private final Logger requestLogger = mock(Logger.class);
    private final Logger responseLogger = mock(Logger.class);

    private final LoggingEventBuilder eventBuilder = mock(DefaultLoggingEventBuilder.class, Mockito.RETURNS_SELF);

    @ParameterizedTest
    @MethodSource("getLogRequestTestsData")
    public void logRequestTests(Level level, String queryParams, HttpHeaders headers, String body, Boolean pathTemplate, Object... expectedArgs) throws IOException {
        var logger = new DefaultHttpClientLogger(
            requestLogger, responseLogger, new $HttpClientTelemetryConfig_HttpClientLoggerConfig_ConfigValueExtractor.HttpClientLoggerConfig_Impl(
            MASKED_QUERY_PARAMS, MASKED_HEADERS, "***", pathTemplate, true
        ));

        expectLogLevel(requestLogger, level);

        var rq = HttpClientRequest.of("POST", URI.create("http://test/path/1?" + queryParams), "/path/{id}", headers.toMutable(), HttpBody.plaintext(body), Duration.ofMillis(100));
        logger.logRequest(rq, body);

        var writerCaptor = ArgumentCaptor.forClass(StructuredArgumentWriter.class);
        verify(eventBuilder).addKeyValue(eq("httpRequest"), writerCaptor.capture());
        var writer = writerCaptor.getValue();
        var gen = Mockito.mock(JsonGenerator.class);
        writer.writeTo(gen);

        verify(gen).writeStringProperty("operation", (String) expectedArgs[0]);
        if (expectedArgs.length > 1) {
            verify(gen).writeStringProperty("queryParams", (String) expectedArgs[1]);
        }
        if (expectedArgs.length > 2) {
            verify(gen).writeStringProperty("headers", (String) expectedArgs[2]);
        }
        if (expectedArgs.length > 3) {
            verify(gen).writeStringProperty("body", (String) expectedArgs[3]);
        }

        verify(eventBuilder).log("HttpServer received request");
    }

    @ParameterizedTest
    @MethodSource("getLogResponseTestsData")
    public void logResponseTests(Level level, HttpHeaders headers, String body, Boolean pathTemplate, Object... expectedArgs) throws IOException {
        var logger = new DefaultHttpClientLogger(
            requestLogger, responseLogger, new $HttpClientTelemetryConfig_HttpClientLoggerConfig_ConfigValueExtractor.HttpClientLoggerConfig_Impl(
            MASKED_QUERY_PARAMS, MASKED_HEADERS, "***", pathTemplate, true
        ));

        expectLogLevel(responseLogger, level);

        var rq = HttpClientRequest.of("POST", URI.create("/path/1"), "/path/{id}", headers.toMutable(), HttpBody.plaintext(body), Duration.ofMillis(100));
        var rs = new HttpClientResponse.Default(200, headers, HttpBody.plaintext(body), null);

        logger.logResponse(
            rq,
            rs,
            100,
            body
        );

        var writerCaptor = ArgumentCaptor.forClass(StructuredArgumentWriter.class);
        verify(eventBuilder).addKeyValue(eq("httpResponse"), writerCaptor.capture());
        var writer = writerCaptor.getValue();
        var gen = Mockito.mock(JsonGenerator.class);
        writer.writeTo(gen);

        verify(eventBuilder).log("HttpClient received response");
        verify(gen).writeNumberProperty("statusCode", (Integer) expectedArgs[0]);
        if (expectedArgs.length > 1) {
            verify(gen).writeStringProperty("operation", (String) expectedArgs[1]);
        }
        if (expectedArgs.length > 2) {
            verify(gen).writeStringProperty("headers", (String) expectedArgs[2]);
        }
        if (expectedArgs.length > 3) {
            verify(gen).writeStringProperty("body", (String) expectedArgs[3]);
        }
    }

    private static Stream<Arguments> getLogRequestTestsData() {
        return Stream.of(
            Arguments.of(Level.TRACE, QUERY_PARAMS_STR, HEADERS, BODY, false,
                List.of("POST /path/1", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR, BODY).toArray()),
            Arguments.of(Level.TRACE, QUERY_PARAMS_STR, HEADERS, BODY, true,
                List.of("POST /path/{id}", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR, BODY).toArray()),
            Arguments.of(Level.DEBUG, QUERY_PARAMS_STR, HEADERS, BODY, false,
                List.of("POST /path/1", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.DEBUG, QUERY_PARAMS_STR, HEADERS, BODY, true,
                List.of("POST /path/{id}", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.INFO, QUERY_PARAMS_STR, HEADERS, BODY, true,
                List.of("POST /path/{id}").toArray())
        );
    }

    private static Stream<Arguments> getLogResponseTestsData() {
        return Stream.of(
            Arguments.of(Level.TRACE, HEADERS, BODY, false,
                List.of(200, "POST /path/1", MASKED_HEADERS_STR, BODY).toArray()),
            Arguments.of(Level.TRACE, HEADERS, BODY, true,
                List.of(200, "POST /path/{id}", MASKED_HEADERS_STR, BODY).toArray()),
            Arguments.of(Level.DEBUG, HEADERS, BODY, false,
                List.of(200, "POST /path/1", MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.DEBUG, HEADERS, BODY, true,
                List.of(200, "POST /path/{id}", MASKED_HEADERS_STR).toArray())
        );
    }

    private void expectLogLevel(Logger responseLogger, Level level) {
        switch (level) {
            case TRACE:
                when(responseLogger.isTraceEnabled()).thenReturn(true);
            case DEBUG:
                when(responseLogger.isDebugEnabled()).thenReturn(true);
            case INFO:
                when(responseLogger.isInfoEnabled()).thenReturn(true);
            case WARN:
                when(responseLogger.isWarnEnabled()).thenReturn(true);
            case ERROR:
                when(responseLogger.isErrorEnabled()).thenReturn(true);
        }
        when(responseLogger.atLevel(level)).thenReturn(eventBuilder);
    }
}
