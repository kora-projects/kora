package ru.tinkoff.kora.json.annotation.processor.generator;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.json.annotation.processor.GeneratorModuleAnnotationProcessor;

import java.lang.reflect.ParameterizedType;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

        var reader0 = loadClass("$JsonGenerator_GeneratorModule_0_JsonReader");
        loadClass("$JsonGenerator_GeneratorModule_1_JsonReader");
        loadClass("$JsonGenerator_GeneratorModule_0_JsonWriter");
        loadClass("$JsonGenerator_GeneratorModule_1_JsonWriter");

        var reader0Interface = (ParameterizedType) reader0.getGenericInterfaces()[0];

        assertThat(reader0Interface.getActualTypeArguments()[0])
            .isEqualTo(loadClass("ExternalRecord2"));
    }
}
