package ru.tinkoff.kora.http.server.common.telemetry;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class Slf4jHttpServerLoggerTests {

    private static final MutableHttpHeaders HEADERS = HttpHeaders.of("authorization", "auth", "OtherHeader", "val");
    private static final Map<String, ? extends Collection<String>> QUERY_PARAMS = new LinkedHashMap<>() {{
        put("a", List.of("5"));
        put("sessionid", List.of("abc"));
    }};
    private static final String EXCEPTION_MESSAGE = "SomeError";
    private static final Exception EXCEPTION = new RuntimeException(EXCEPTION_MESSAGE);

    private static final String MASKED_HEADERS_STR = "authorization: ***\notherheader: val";
    private static final String MASKED_QUERY_PARAMS_STR = "a=5&sessionid=***";

    private static final Set<String> MASKED_QUERY_PARAMS = Set.of("sessionId");
    private static final Set<String> MASKED_HEADERS = Set.of("authorization");

    private final Appender mockAppender = mock(Appender.class);

    @ParameterizedTest
    @MethodSource("getLogStartTestsData")
    public void logStartTests(Level level, Map<String, ? extends Collection<String>> queryParams, HttpHeaders headers, Boolean pathTemplate,
                     String expectedMessage, Object... expectedArgs) {
        expectLogLevel(level);

        Slf4jHttpServerLogger logger = new Slf4jHttpServerLogger(
            true, MASKED_QUERY_PARAMS, MASKED_HEADERS, "***", pathTemplate);

        logger.logStart("POST", "/path/1", "/path/{id}", queryParams, headers);

        final LoggingEvent event = getLoggedEvent();

        assertThat(event.getLevel().levelInt).isGreaterThanOrEqualTo(level.levelInt); // логируем с текущим уровнем или более высоким
        assertThat(event.getMessage()).isEqualTo(expectedMessage);
        assertThat(event.getArgumentArray()).isEqualTo(expectedArgs);
    }

    @ParameterizedTest
    @MethodSource("getLogEndNoExceptionTestsData")
    public void logEndNoExceptionTests(Level level, Map<String, ? extends Collection<String>> queryParams, HttpHeaders headers, Boolean pathTemplate,
                                       String expectedMessage, Object... expectedArgs) {
        expectLogLevel(level);

        Slf4jHttpServerLogger logger = new Slf4jHttpServerLogger(
            true, MASKED_QUERY_PARAMS, MASKED_HEADERS, "***", pathTemplate);

        logger.logEnd(200, HttpResultCode.SUCCESS, "POST", "/path/1", "/path/{id}", 100,
                      queryParams, headers, null);

        final LoggingEvent event = getLoggedEvent();

        assertThat(event.getLevel().levelInt).isGreaterThanOrEqualTo(level.levelInt); // логируем с текущим уровнем или более высоким
        assertThat(event.getMessage()).isEqualTo(expectedMessage);
        assertThat(event.getArgumentArray()).isEqualTo(expectedArgs);
    }

    @ParameterizedTest
    @MethodSource("getLogEndWithExceptionTestsData")
    public void logEndWithExceptionTests(boolean stacktrace, Map<String, ? extends Collection<String>> queryParams, HttpHeaders headers, Boolean pathTemplate,
                                         String expectedMessage, Object... expectedArgs) {
        expectLogLevel(Level.WARN);

        Slf4jHttpServerLogger logger = new Slf4jHttpServerLogger(
            stacktrace, MASKED_QUERY_PARAMS, MASKED_HEADERS, "***", pathTemplate);

        logger.logEnd(200, HttpResultCode.SUCCESS, "POST", "/path/1", "/path/{id}", 100,
                      queryParams, headers, EXCEPTION);

        final LoggingEvent event = getLoggedEvent();

        assertThat(event.getMessage()).isEqualTo(expectedMessage);
        final Object lastArg = expectedArgs[expectedArgs.length - 1];
        if (lastArg instanceof Throwable) {
            Object[] args = new Object[expectedArgs.length - 1];
            System.arraycopy(expectedArgs, 0, args, 0, expectedArgs.length - 1);
            assertThat(event.getArgumentArray()).isEqualTo(args);
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo(((Throwable) lastArg).getMessage());
        } else {
            assertThat(event.getArgumentArray()).isEqualTo(expectedArgs);
        }
    }

    private LoggingEvent getLoggedEvent() {
        ArgumentCaptor<LoggingEvent> captor = ArgumentCaptor.forClass(LoggingEvent.class);
        verify(mockAppender).doAppend(captor.capture());
        final LoggingEvent event = captor.getValue();
        return event;
    }

    private void expectLogLevel(Level level) {
        Logger root = (Logger) LoggerFactory.getLogger(HttpServer.class);
        root.addAppender(mockAppender);
        root.setLevel(level);
    }

    private static Stream<Arguments> getLogStartTestsData() {
        return Stream.of(
            Arguments.of(Level.DEBUG, QUERY_PARAMS, HEADERS, false,
                         "HttpServer received request for {}?{}\n{}", List.of("POST /path/1", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.DEBUG, QUERY_PARAMS, HEADERS, true,
                         "HttpServer received request for {}?{}\n{}", List.of("POST /path/{id}", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.DEBUG, QUERY_PARAMS, HEADERS, null,
                         "HttpServer received request for {}?{}\n{}", List.of("POST /path/{id}", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.TRACE, QUERY_PARAMS, HEADERS, null,
                         "HttpServer received request for {}?{}\n{}", List.of("POST /path/1", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.INFO, QUERY_PARAMS, HEADERS, false,
                         "HttpServer received request for {}", List.of("POST /path/1").toArray()),
            Arguments.of(Level.INFO, QUERY_PARAMS, HEADERS, true,
                         "HttpServer received request for {}", List.of("POST /path/{id}").toArray()),
            Arguments.of(Level.INFO, QUERY_PARAMS, HEADERS, null,
                         "HttpServer received request for {}", List.of("POST /path/{id}").toArray()),
            Arguments.of(Level.DEBUG, Map.of(), HEADERS, true,
                         "HttpServer received request for {}\n{}", List.of("POST /path/{id}", MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.DEBUG, QUERY_PARAMS, null, true,
                         "HttpServer received request for {}?{}", List.of("POST /path/{id}", MASKED_QUERY_PARAMS_STR).toArray()),
            Arguments.of(Level.DEBUG, Map.of(), null, true,
                         "HttpServer received request for {}", List.of("POST /path/{id}").toArray())
        );
    }

    private static Stream<Arguments> getLogEndNoExceptionTestsData() {
        return Stream.of(
            Arguments.of(Level.DEBUG, QUERY_PARAMS, HEADERS, false,
                         "HttpServer responded {} for {}?{}\n{}", List.of(200, "POST /path/1", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.DEBUG, QUERY_PARAMS, HEADERS, true,
                         "HttpServer responded {} for {}?{}\n{}", List.of(200, "POST /path/{id}", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.DEBUG, QUERY_PARAMS, HEADERS, null,
                         "HttpServer responded {} for {}?{}\n{}", List.of(200, "POST /path/{id}", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.TRACE, QUERY_PARAMS, HEADERS, null,
                         "HttpServer responded {} for {}?{}\n{}", List.of(200, "POST /path/1", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.INFO, QUERY_PARAMS, HEADERS, false,
                         "HttpServer responded {} for {}", List.of(200, "POST /path/1").toArray()),
            Arguments.of(Level.INFO, QUERY_PARAMS, HEADERS, true,
                         "HttpServer responded {} for {}", List.of(200, "POST /path/{id}").toArray()),
            Arguments.of(Level.INFO, QUERY_PARAMS, HEADERS, null,
                         "HttpServer responded {} for {}", List.of(200, "POST /path/{id}").toArray()),
            Arguments.of(Level.DEBUG, Map.of(), HEADERS, null,
                         "HttpServer responded {} for {}\n{}", List.of(200, "POST /path/{id}", MASKED_HEADERS_STR).toArray()),
            Arguments.of(Level.DEBUG, QUERY_PARAMS, null, null,
                         "HttpServer responded {} for {}?{}", List.of(200, "POST /path/{id}", MASKED_QUERY_PARAMS_STR).toArray()),
            Arguments.of(Level.DEBUG, Map.of(), null, null,
                         "HttpServer responded {} for {}", List.of(200, "POST /path/{id}").toArray())
        );
    }

    private static Stream<Arguments> getLogEndWithExceptionTestsData() {
        return Stream.of(
            Arguments.of(false, QUERY_PARAMS, HEADERS, false,
                         "HttpServer responded error {} for {}?{} due to: {}\n{}", List.of(200, "POST /path/1", MASKED_QUERY_PARAMS_STR, EXCEPTION_MESSAGE, MASKED_HEADERS_STR).toArray()),
            Arguments.of(false, QUERY_PARAMS, HEADERS, true,
                         "HttpServer responded error {} for {}?{} due to: {}\n{}", List.of(200, "POST /path/{id}", MASKED_QUERY_PARAMS_STR, EXCEPTION_MESSAGE, MASKED_HEADERS_STR).toArray()),
            Arguments.of(false, QUERY_PARAMS, HEADERS, null,
                         "HttpServer responded error {} for {}?{} due to: {}\n{}", List.of(200, "POST /path/{id}", MASKED_QUERY_PARAMS_STR, EXCEPTION_MESSAGE, MASKED_HEADERS_STR).toArray()),
            Arguments.of(false, Map.of(), HEADERS, null,
                         "HttpServer responded error {} for {} due to: {}\n{}", List.of(200, "POST /path/{id}", EXCEPTION_MESSAGE, MASKED_HEADERS_STR).toArray()),
            Arguments.of(false, QUERY_PARAMS, null, null,
                         "HttpServer responded error {} for {}?{} due to: {}", List.of(200, "POST /path/{id}", MASKED_QUERY_PARAMS_STR, EXCEPTION_MESSAGE).toArray()),
            Arguments.of(false, Map.of(), null, null,
                         "HttpServer responded error {} for {} due to: {}", List.of(200, "POST /path/{id}", EXCEPTION_MESSAGE).toArray()),
            Arguments.of(true, QUERY_PARAMS, HEADERS, false,
                         "HttpServer responded error {} for {}?{}\n{}", List.of(200, "POST /path/1", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR, EXCEPTION).toArray()),
            Arguments.of(true, QUERY_PARAMS, HEADERS, true,
                         "HttpServer responded error {} for {}?{}\n{}", List.of(200, "POST /path/{id}", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR, EXCEPTION).toArray()),
            Arguments.of(true, QUERY_PARAMS, HEADERS, null,
                         "HttpServer responded error {} for {}?{}\n{}", List.of(200, "POST /path/{id}", MASKED_QUERY_PARAMS_STR, MASKED_HEADERS_STR, EXCEPTION).toArray()),
            Arguments.of(true, Map.of(), HEADERS, null,
                         "HttpServer responded error {} for {}\n{}", List.of(200, "POST /path/{id}", MASKED_HEADERS_STR, EXCEPTION).toArray()),
            Arguments.of(true, QUERY_PARAMS, null, null,
                         "HttpServer responded error {} for {}?{}", List.of(200, "POST /path/{id}", MASKED_QUERY_PARAMS_STR, EXCEPTION).toArray()),
            Arguments.of(true, Map.of(), null, null,
                         "HttpServer responded error {} for {}", List.of(200, "POST /path/{id}", EXCEPTION).toArray())
        );
    }
}
