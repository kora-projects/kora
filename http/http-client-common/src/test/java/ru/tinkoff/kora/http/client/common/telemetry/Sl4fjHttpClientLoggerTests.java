package ru.tinkoff.kora.http.client.common.telemetry;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.spi.DefaultLoggingEventBuilder;
import org.slf4j.spi.LoggingEventBuilder;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Sl4fjHttpClientLoggerTests {

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
    public void logRequestTests(Level level, String queryParams, HttpHeaders headers, String body, Boolean pathTemplate, String expectedMessage, Object... expectedArgs) {
        Sl4fjHttpClientLogger logger = new Sl4fjHttpClientLogger(
            requestLogger, responseLogger, MASKED_QUERY_PARAMS, MASKED_HEADERS, "***", pathTemplate);

        expectLogLevel(requestLogger, level);

        logger.logRequest("postMethod", "POST", "/path/1",
                          "/path/{id}", "", queryParams, headers, body);

        verify(eventBuilder).addMarker(any());
        if (expectedArgs.length == 1) {
            verify(eventBuilder).log(expectedMessage, expectedArgs[0]);
        } else if (expectedArgs.length == 2) {
            verify(eventBuilder).log(expectedMessage, expectedArgs[0], expectedArgs[1]);
        } else {
            verify(eventBuilder).log(expectedMessage, expectedArgs);
        }
    }

    @ParameterizedTest
    @MethodSource("getLogResponseTestsData")
    public void logResponseTests(Level level, HttpHeaders headers, String body, Boolean pathTemplate, String expectedMessage, Object... expectedArgs) {
        Sl4fjHttpClientLogger logger = new Sl4fjHttpClientLogger(
            requestLogger, responseLogger, MASKED_QUERY_PARAMS, MASKED_HEADERS, "***", pathTemplate);

        expectLogLevel(responseLogger, level);

        logger.logResponse(200, HttpResultCode.SUCCESS, "postMethod", "POST", "/path/1",
                           "/path/{id}", 100,
                           headers, body, null);

        verify(eventBuilder).addMarker(any());
        if (expectedArgs.length > 2) {
            verify(eventBuilder).log(expectedMessage, expectedArgs);
        } else {
            verify(eventBuilder).log(expectedMessage, expectedArgs[0], expectedArgs[1]);
        }
    }

    private static Stream<Arguments> getLogRequestTestsData() {
        return Stream.of(
            Arguments.of(Level.TRACE, QUERY_PARAMS_STR, HEADERS, BODY, false,
                         "HttpClient requesting {}?{}\n{}\n{}", List.of("POST /path/1", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR, BODY).toArray()),
            Arguments.of(Level.TRACE, QUERY_PARAMS_STR, HEADERS, BODY, true,
                         "HttpClient requesting {}?{}\n{}\n{}", List.of("POST /path/{id}", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR, BODY).toArray()),
            Arguments.of(Level.TRACE, QUERY_PARAMS_STR, HEADERS, BODY, null,
                         "HttpClient requesting {}?{}\n{}\n{}", List.of("POST /path/1", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR, BODY).toArray()),
            Arguments.of(Level.DEBUG, QUERY_PARAMS_STR, HEADERS, BODY, false,
                         "HttpClient requesting {}?{}\n{}", List.of("POST /path/1", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.DEBUG, QUERY_PARAMS_STR, HEADERS, BODY, true,
                         "HttpClient requesting {}?{}\n{}", List.of("POST /path/{id}", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.DEBUG, QUERY_PARAMS_STR, HEADERS, BODY, null,
                         "HttpClient requesting {}?{}\n{}", List.of("POST /path/{id}", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.INFO, QUERY_PARAMS_STR, HEADERS, BODY, null,
                         "HttpClient requesting {}", List.of("POST /path/{id}").toArray()),
            Arguments.of(Level.TRACE, "", HEADERS, BODY, null,
                         "HttpClient requesting {}\n{}\n{}", List.of("POST /path/1", MASKED_HEADERS_STR, BODY).toArray()),
            Arguments.of(Level.TRACE, QUERY_PARAMS_STR, null, BODY, null,
                         "HttpClient requesting {}?{}\n{}", List.of("POST /path/1", MASKED_QUERY_PARAMS_STR, BODY).toArray()),
            Arguments.of(Level.TRACE, QUERY_PARAMS_STR, HEADERS, null, null,
                         "HttpClient requesting {}?{}\n{}", List.of("POST /path/1", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.TRACE, "", null, null, null,
                         "HttpClient requesting {}", List.of("POST /path/1").toArray())
        );
    }

    private static Stream<Arguments> getLogResponseTestsData() {
        return Stream.of(
            Arguments.of(Level.TRACE, HEADERS, BODY, false,
                         "HttpClient received {} from {}\n{}\n{}", List.of(200, "POST /path/1", MASKED_HEADERS_STR, BODY).toArray()),
            Arguments.of(Level.TRACE, HEADERS, BODY, true,
                         "HttpClient received {} from {}\n{}\n{}", List.of(200, "POST /path/{id}", MASKED_HEADERS_STR, BODY).toArray()),
            Arguments.of(Level.TRACE, HEADERS, BODY, null,
                         "HttpClient received {} from {}\n{}\n{}", List.of(200, "POST /path/1", MASKED_HEADERS_STR, BODY).toArray()),
            Arguments.of(Level.DEBUG, HEADERS, BODY, false,
                         "HttpClient received {} from {}\n{}", List.of(200, "POST /path/1", MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.DEBUG, HEADERS, BODY, true,
                         "HttpClient received {} from {}\n{}", List.of(200, "POST /path/{id}", MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.DEBUG, HEADERS, BODY, null,
                         "HttpClient received {} from {}\n{}", List.of(200, "POST /path/{id}", MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.INFO, HEADERS, BODY, null,
                         "HttpClient received {} from {}", List.of(200, "POST /path/{id}").toArray()),
            Arguments.of(Level.DEBUG, null, BODY, null,
                         "HttpClient received {} from {}", List.of(200, "POST /path/{id}").toArray()),
            Arguments.of(Level.TRACE, null, null, null,
                         "HttpClient received {} from {}", List.of(200, "POST /path/1").toArray()),
            Arguments.of(Level.TRACE, null, BODY, null,
                         "HttpClient received {} from {}\n{}", List.of(200, "POST /path/1", BODY).toArray())
        );
    }

    private void expectLogLevel(Logger responseLogger, Level level) {
        switch (level) {
            case TRACE -> when(responseLogger.isTraceEnabled()).thenReturn(true);
            case DEBUG -> when(responseLogger.isDebugEnabled()).thenReturn(true);
        }
        when(responseLogger.atLevel(level)).thenReturn(eventBuilder);
    }
}
