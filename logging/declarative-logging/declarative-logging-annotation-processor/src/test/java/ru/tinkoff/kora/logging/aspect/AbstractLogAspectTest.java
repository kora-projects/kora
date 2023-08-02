package ru.tinkoff.kora.logging.aspect;

import com.fasterxml.jackson.core.JsonGenerator;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;
import ru.tinkoff.kora.logging.common.arg.StructuredArgumentWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.slf4j.event.Level.*;

public abstract class AbstractLogAspectTest extends AbstractAnnotationProcessorTest {
    protected Map<String, Logger> loggers = new HashMap<>();
    protected ILoggerFactory factory = Mockito.mock(ILoggerFactory.class);
    protected ArgumentCaptor<Marker> inData = ArgumentCaptor.forClass(Marker.class);
    protected ArgumentCaptor<Marker> outData = ArgumentCaptor.forClass(Marker.class);

    @BeforeEach
    void setUp() {
        Mockito.when(factory.getLogger(Mockito.any())).then(invocation -> loggers.computeIfAbsent(invocation.getArgument(0, String.class), k -> Mockito.mock(Logger.class)));
    }

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.logging.common.annotation.Log;
            import static org.slf4j.event.Level.*;
            """;
    }

    protected TestObject compile(@Language("java") String... sources) {
        var result = super.compile(List.of(new AopAnnotationProcessor()), sources);
        result.assertSuccess();
        try {
            var generatedClass = result.loadClass("$Target__AopProxy");
            var object = generatedClass.getConstructor(ILoggerFactory.class).newInstance(factory);
            return new TestObject(generatedClass, object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    protected void verifyData(ArgumentCaptor<Marker> captor, Map<String, String> expectedData) {
        Assertions.assertThat(captor.getValue())
            .isNotNull()
            .asInstanceOf(InstanceOfAssertFactories.type(StructuredArgument.class))
            .extracting(StructuredArgument::fieldName)
            .isEqualTo("data");

        var writer = (StructuredArgumentWriter) captor.getValue();
        var mockGen = Mockito.mock(JsonGenerator.class);
        var data = new HashMap<String, String>();
        try {
            doAnswer(invocation -> data.put(invocation.getArgument(0, String.class), invocation.getArgument(1, String.class)))
                .when(mockGen).writeStringField(anyString(), anyString());
            writer.writeTo(mockGen);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Assertions.assertThat(data).containsExactlyInAnyOrderEntriesOf(expectedData);
    }

    protected void verifyInData(Map<String, String> expectedData) {
        verifyData(inData, expectedData);
    }

    protected void verifyOutData(Map<String, String> expectedData) {
        verifyData(outData, expectedData);
    }

    protected void reset(Logger log, Level level) {
        Mockito.reset(log);
        if (level == null) {
            return;
        }
        switch (level) {
            case TRACE:
                when(log.isTraceEnabled()).thenReturn(true);
                when(log.isEnabledForLevel(TRACE)).thenReturn(true);
            case DEBUG:
                when(log.isDebugEnabled()).thenReturn(true);
                when(log.isEnabledForLevel(DEBUG)).thenReturn(true);
            case INFO:
                when(log.isInfoEnabled()).thenReturn(true);
                when(log.isEnabledForLevel(INFO)).thenReturn(true);
            case WARN:
                when(log.isWarnEnabled()).thenReturn(true);
                when(log.isEnabledForLevel(WARN)).thenReturn(true);
            case ERROR:
                when(log.isErrorEnabled()).thenReturn(true);
                when(log.isEnabledForLevel(ERROR)).thenReturn(true);
        }
    }
}
