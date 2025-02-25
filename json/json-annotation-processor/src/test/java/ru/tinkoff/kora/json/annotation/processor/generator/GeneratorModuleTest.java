package ru.tinkoff.kora.json.annotation.processor.generator;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.json.annotation.processor.GeneratorModuleAnnotationProcessor;

import java.util.List;

public class GeneratorModuleTest extends AbstractAnnotationProcessorTest {
    @Test
    public void test() {
        compile(List.of(new GeneratorModuleAnnotationProcessor()), """
            public record ExternalRecord1(String key){}
            """, """
            public record ExternalRecord2(ExternalRecord1 key){}
            """, """
            @ru.tinkoff.kora.common.annotation.GeneratorModule(
                generator = ru.tinkoff.kora.json.common.annotation.Json.class,
                types = {ExternalRecord2.class, ExternalRecord1.class}
            )
            public interface JsonGenerator {}
            """);
        assertSuccess();
    }
}
