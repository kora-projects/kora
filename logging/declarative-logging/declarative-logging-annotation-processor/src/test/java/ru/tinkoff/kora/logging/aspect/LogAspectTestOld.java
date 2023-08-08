package ru.tinkoff.kora.logging.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.logging.aspect.target.LogAspectTarget;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Disabled
public class LogAspectTestOld {

    private final JsonFactory jsonFactory = new JsonFactory();

    private LogAspectTarget logAspectTarget;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    public void setUp() throws Exception {
        listAppender = new ListAppender<>();
        listAppender.start();

        ClassLoader classLoader = TestUtils.annotationProcess(LogAspectTarget.class, new AopAnnotationProcessor());
        Class<?> cl = classLoader.loadClass("ru.tinkoff.kora.logging.aspect.target.$LogAspectTarget__AopProxy");

        logAspectTarget = (LogAspectTarget) cl.getConstructor(ILoggerFactory.class).newInstance(LoggerFactory.getILoggerFactory());
    }

    @Test
    void testEmpty() {
        Logger declarativeComponentLogger =
            (Logger) LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".emptyMethod");

        declarativeComponentLogger.addAppender(listAppender);
        declarativeComponentLogger.setLevel(Level.ALL);

        logAspectTarget.emptyMethod();

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(3, logsList.size());
        assertEquals("[DEBUG] >", logsList.get(0).toString());
        assertEquals("[INFO] EmptyMethod called", logsList.get(1).toString());
        assertEquals("[DEBUG] <", logsList.get(2).toString());

        assertNull(logsList.get(0).getMarker());
        assertNull(logsList.get(2).getMarker());
    }

    @Test
    void testMethodWithArgsDebug() throws IOException {
        Logger declarativeComponentLogger =
            (Logger) LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithArgsDebug");

        declarativeComponentLogger.addAppender(listAppender);
        declarativeComponentLogger.setLevel(Level.ALL);

        logAspectTarget.methodWithArgsDebug("strValue", 55);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(3, logsList.size());
        assertEquals("[DEBUG] >", logsList.get(0).toString());
        assertEquals("[INFO] MethodWithArgsDebug called", logsList.get(1).toString());
        assertEquals("[DEBUG] <", logsList.get(2).toString());

        var markerData = logsList.get(0).getMarker();


        if (markerData instanceof StructuredArgument structuredArgument) {
            assertEquals(structuredArgument.fieldName(), "data");

            Map<String, String> markerDataAsMap = getMarkerDataAsMap(structuredArgument);

            assertEquals(2, markerDataAsMap.size());
            assertEquals("55", markerDataAsMap.get("numParam"));
            assertEquals("strValue", markerDataAsMap.get("strParam"));
            assertNull(logsList.get(2).getMarker());
        }
    }

    @Test
    void testMethodWithArgsInfo() {
        Logger declarativeComponentLogger =
            (Logger) LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithArgsInfo");

        declarativeComponentLogger.addAppender(listAppender);
        declarativeComponentLogger.setLevel(Level.ALL);

        logAspectTarget.methodWithArgsInfo("strValue", 55);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(2, logsList.size());
        assertEquals("[INFO] >", logsList.get(0).toString());
        assertEquals("[INFO] MethodWithArgsInfo called", logsList.get(1).toString());

        assertNull(logsList.get(0).getMarker());
    }

    @Test
    void testMethodWithReturn() throws IOException {
        Logger declarativeComponentLogger =
            (Logger) LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithReturn");

        declarativeComponentLogger.addAppender(listAppender);
        declarativeComponentLogger.setLevel(Level.ALL);

        String result = logAspectTarget.methodWithReturn();

        Assertions.assertEquals(LogAspectTarget.TEST_RESULT_VALUE, result);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(3, logsList.size());

        assertEquals("[DEBUG] >", logsList.get(0).toString());
        assertEquals("[INFO] MethodWithReturn called", logsList.get(1).toString());
        assertEquals("[DEBUG] <", logsList.get(2).toString());

        var markerData = (StructuredArgument) logsList.get(2).getMarker();
        assertEquals(markerData.fieldName(), "data");

        Map<String, String> markerDataAsMap = getMarkerDataAsMap(markerData);
        assertEquals(1, markerDataAsMap.size());
        assertEquals("testResult", markerDataAsMap.get("out"));
    }

    @Test
    void testMethodWithReturnOnInfoLevel() {
        Logger declarativeComponentLogger =
            (Logger) LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithReturn");

        declarativeComponentLogger.addAppender(listAppender);
        declarativeComponentLogger.setLevel(Level.INFO);

        String result = logAspectTarget.methodWithReturn();

        Assertions.assertEquals(LogAspectTarget.TEST_RESULT_VALUE, result);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());

        assertEquals("[INFO] MethodWithReturn called", logsList.get(0).toString());
    }

    @Test
    void testMethodWithReturnAndArgs() throws IOException {
        Logger declarativeComponentLogger =
            (Logger) LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithReturnAndArgs");

        declarativeComponentLogger.addAppender(listAppender);
        declarativeComponentLogger.setLevel(Level.ALL);

        String result = logAspectTarget.methodWithReturnAndArgs("strValue", 55);
        Assertions.assertEquals(LogAspectTarget.TEST_RESULT_VALUE, result);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(2, logsList.size());

        assertEquals("[DEBUG] >", logsList.get(0).toString());
        assertEquals("[DEBUG] <", logsList.get(1).toString());

        var markerDataIn = (StructuredArgument) logsList.get(0).getMarker();
        assertEquals(markerDataIn.fieldName(), "data");

        Map<String, String> markerDataInAsMap = getMarkerDataAsMap(markerDataIn);
        assertEquals(2, markerDataInAsMap.size());
        assertEquals("55", markerDataInAsMap.get("numParam"));
        assertEquals("strValue", markerDataInAsMap.get("strParam"));

        var markerDataOut = (StructuredArgument) logsList.get(1).getMarker();
        assertEquals(markerDataIn.fieldName(), "data");

        Map<String, String> markerDataOutAsMap = getMarkerDataAsMap(markerDataOut);
        assertEquals(1, markerDataOutAsMap.size());
        assertEquals("testResult", markerDataOutAsMap.get("out"));
    }

    @Test
    void testMethodWithReturnAndOffArgs() throws IOException {
        Logger declarativeComponentLogger =
            (Logger) LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithReturnAndOffArgs");

        declarativeComponentLogger.addAppender(listAppender);
        declarativeComponentLogger.setLevel(Level.ALL);

        String result = logAspectTarget.methodWithReturnAndOffArgs("strValue", 55);
        Assertions.assertEquals(LogAspectTarget.TEST_RESULT_VALUE, result);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(2, logsList.size());

        assertEquals("[DEBUG] >", logsList.get(0).toString());
        assertEquals("[DEBUG] <", logsList.get(1).toString());

        var markerDataIn = (StructuredArgument) logsList.get(0).getMarker();
        assertEquals(markerDataIn.fieldName(), "data");

        Map<String, String> markerDataInAsMap = getMarkerDataAsMap(markerDataIn);
        assertEquals(1, markerDataInAsMap.size());
        assertEquals("55", markerDataInAsMap.get("numParam"));

        var markerDataOut = (StructuredArgument) logsList.get(1).getMarker();
        assertEquals(markerDataIn.fieldName(), "data");

        Map<String, String> markerDataOutAsMap = getMarkerDataAsMap(markerDataOut);
        assertEquals(1, markerDataOutAsMap.size());
        assertEquals("testResult", markerDataOutAsMap.get("out"));
    }

    @Test
    void testMethodWithReturnAndOnlyLogArgs() throws IOException {
        Logger declarativeComponentLogger =
            (Logger) LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithReturnAndOnlyLogArgs");

        declarativeComponentLogger.addAppender(listAppender);
        declarativeComponentLogger.setLevel(Level.ALL);

        String result = logAspectTarget.methodWithReturnAndOnlyLogArgs("strValue", 55);
        Assertions.assertEquals(LogAspectTarget.TEST_RESULT_VALUE, result);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());

        assertEquals("[DEBUG] >", logsList.get(0).toString());

        var markerDataIn = (StructuredArgument) logsList.get(0).getMarker();
        assertEquals(markerDataIn.fieldName(), "data");

        Map<String, String> markerDataInAsMap = getMarkerDataAsMap(markerDataIn);
        assertEquals(2, markerDataInAsMap.size());
        assertEquals("55", markerDataInAsMap.get("numParam"));
        assertEquals("strValue", markerDataInAsMap.get("strParam"));
    }

    @Test
    void testMethodWithOnlyLogReturnAndArgs() throws IOException {
        Logger declarativeComponentLogger =
            (Logger) LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithOnlyLogReturnAndArgs");

        declarativeComponentLogger.addAppender(listAppender);
        declarativeComponentLogger.setLevel(Level.ALL);

        String result = logAspectTarget.methodWithOnlyLogReturnAndArgs("strValue", 55);
        Assertions.assertEquals(LogAspectTarget.TEST_RESULT_VALUE, result);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());

        assertEquals("[DEBUG] <", logsList.get(0).toString());

        var markerData = (StructuredArgument) logsList.get(0).getMarker();
        assertEquals(markerData.fieldName(), "data");

        Map<String, String> markerDataAsMap = getMarkerDataAsMap(markerData);
        assertEquals(1, markerDataAsMap.size());
        assertEquals("testResult", markerDataAsMap.get("out"));
    }

    @Test
    void testMethodWithReturnAndArgsOnInfoLevel() {
        Logger declarativeComponentLogger =
            (Logger) LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithReturnAndArgsOnInfoLevel");

        declarativeComponentLogger.addAppender(listAppender);
        declarativeComponentLogger.setLevel(Level.ALL);

        String result = logAspectTarget.methodWithReturnAndArgsOnInfoLevel("strValue", 55);
        Assertions.assertEquals(LogAspectTarget.TEST_RESULT_VALUE, result);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(3, logsList.size());

        assertEquals("[INFO] >", logsList.get(0).toString());
        assertEquals("[INFO] MethodWithReturnAndArgsOnInfoLevel called", logsList.get(1).toString());
        assertEquals("[INFO] <", logsList.get(2).toString());

        assertNull(logsList.get(0).getMarker());
        assertNull(logsList.get(2).getMarker());
    }

    @Test
    void testMethodWithReturnAndArgsSeparatelyOnInfoLevel() {
        Logger declarativeComponentLogger =
            (Logger) LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithReturnAndArgsSeparatelyOnInfoLevel");

        declarativeComponentLogger.addAppender(listAppender);
        declarativeComponentLogger.setLevel(Level.ALL);

        String result = logAspectTarget.methodWithReturnAndArgsSeparatelyOnInfoLevel("strValue", 55);
        Assertions.assertEquals(LogAspectTarget.TEST_RESULT_VALUE, result);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(3, logsList.size());

        assertEquals("[INFO] >", logsList.get(0).toString());
        assertEquals("[INFO] MethodWithReturnAndArgsSeparatelyOnInfoLevel called", logsList.get(1).toString());
        assertEquals("[INFO] <", logsList.get(2).toString());

        assertNull(logsList.get(0).getMarker());
        assertNull(logsList.get(2).getMarker());
    }

    @Test
    void testMethodWithDtoInArgs() throws IOException {
        Logger declarativeComponentLogger =
            (Logger) LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithDtoInArgs");

        declarativeComponentLogger.addAppender(listAppender);
        declarativeComponentLogger.setLevel(Level.ALL);

        LogAspectTarget.SimpleDto simpleDto = new LogAspectTarget.SimpleDto("Ivan", 20, true);
        logAspectTarget.methodWithDtoInArgs(simpleDto);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(2, logsList.size());
        assertEquals("[DEBUG] >", logsList.get(0).toString());
        assertEquals("[INFO] methodWithDtoInArgs called", logsList.get(1).toString());

        var markerData = (StructuredArgument) logsList.get(0).getMarker();
        assertEquals(markerData.fieldName(), "data");

        Map<String, String> markerDataAsMap = getMarkerDataAsMap(markerData);
        assertEquals(1, markerDataAsMap.size());
        assertEquals(simpleDto.toString(), markerDataAsMap.get("simpleDto"));
    }

    @Test
    void testMethodWithReturningDto() throws IOException {
        Logger declarativeComponentLogger =
            (Logger) LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithReturningDto");

        declarativeComponentLogger.addAppender(listAppender);
        declarativeComponentLogger.setLevel(Level.ALL);

        LogAspectTarget.SimpleDto simpleDto = new LogAspectTarget.SimpleDto("Ivan", 20, true);
        LogAspectTarget.SimpleDto resultDto = logAspectTarget.methodWithReturningDto();
        assertEquals(simpleDto, resultDto);

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());

        assertEquals("[DEBUG] <", logsList.get(0).toString());

        var markerData = (StructuredArgument) logsList.get(0).getMarker();
        assertEquals(markerData.fieldName(), "data");

        Map<String, String> markerDataAsMap = getMarkerDataAsMap(markerData);
        assertEquals(1, markerDataAsMap.size());
        assertEquals(simpleDto.toString(), markerDataAsMap.get("out"));
    }

    @Test
    void testMethodWithReturningMonoWithDto() throws IOException {
        Logger declarativeComponentLogger =
            (Logger) LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithReturningMonoWithDtoLogOut");

        declarativeComponentLogger.addAppender(listAppender);
        declarativeComponentLogger.setLevel(Level.ALL);

        LogAspectTarget.SimpleDto simpleDto = new LogAspectTarget.SimpleDto("Ivan", 20, true);
        Mono<LogAspectTarget.SimpleDto> resultDto = logAspectTarget.methodWithReturningMonoWithDtoLogOut();

        assertEquals(simpleDto, resultDto.block());

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());

        assertEquals("[DEBUG] <", logsList.get(0).toString());

        var markerData = (StructuredArgument) logsList.get(0).getMarker();
        assertEquals(markerData.fieldName(), "data");

        Map<String, String> markerDataAsMap = getMarkerDataAsMap(markerData);
        assertEquals(1, markerDataAsMap.size());
        assertEquals(simpleDto.toString(), markerDataAsMap.get("out"));
    }

    @Test
    void testMethodWithReturningMonoWithDtoLogInfo() {
        Logger declarativeComponentLogger =
            (Logger) LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithReturningMonoWithDtoLogInfo");

        declarativeComponentLogger.addAppender(listAppender);
        declarativeComponentLogger.setLevel(Level.ALL);

        LogAspectTarget.SimpleDto simpleDto = new LogAspectTarget.SimpleDto("Ivan", 20, true);
        Mono<LogAspectTarget.SimpleDto> resultDto = logAspectTarget.methodWithReturningMonoWithDtoLogInfo();

        assertEquals(simpleDto, resultDto.block());

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());

        assertEquals("[INFO] <", logsList.get(0).toString());

        assertNull(logsList.get(0).getMarker());
    }

    @Nonnull
    private Map<String, String> getMarkerDataAsMap(StructuredArgument markerData) throws IOException {
//        ObjectMapper mapper = new ObjectMapper();
        var markerBytes = markerString(markerData);
//        var parsedMarker = mapper.readTree(markerBytes);
        var resultMap = new HashMap<String, String>();
//        var dataNode = parsedMarker.get("data");
//        dataNode.fieldNames().forEachRemaining(s -> resultMap.put(s, dataNode.get(s).textValue()));

        return resultMap;
    }

    private byte[] markerString(StructuredArgument markerData) throws IOException {
        var out = new ByteArrayBuilder(this.jsonFactory._getBufferRecycler());
        var json = this.jsonFactory.createGenerator(out);
        json.writeStartObject();
        json.writeFieldName(markerData.fieldName());
        markerData.writeTo(json);
        json.writeEndObject();
        json.flush();

        out.append('\n');
        out.flush();

        var result = out.toByteArray();
        out.release();
        return result;
    }

}
