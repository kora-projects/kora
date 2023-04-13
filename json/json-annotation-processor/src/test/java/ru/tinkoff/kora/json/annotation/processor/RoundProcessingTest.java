package ru.tinkoff.kora.json.annotation.processor;

import com.fasterxml.jackson.core.JsonToken;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class RoundProcessingTest extends AbstractJsonAnnotationProcessorTest {
    @Test
    @SuppressWarnings("unchecked")
    public void testGeneratedDependency() throws IOException {
        var generator = new AbstractProcessor() {
            @Override
            public Set<String> getSupportedAnnotationTypes() {
                return Set.of("ru.tinkoff.kora.json.annotation.processor.packageForRoundProcessingTest.testGeneratedDependency.SomeAnnotation");
            }

            @Override
            public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                if (roundEnv.processingOver() || annotations.isEmpty()) {
                    return false;
                }
                try {
                    var sf = this.processingEnv.getFiler().createSourceFile("ru.tinkoff.kora.json.annotation.processor.packageForRoundProcessingTest.testGeneratedDependency.GeneratedType");
                    try (var w = sf.openWriter()) {
                        w.write("package ru.tinkoff.kora.json.annotation.processor.packageForRoundProcessingTest.testGeneratedDependency;\n");
                        w.write("public record GeneratedType(String value){}\n");
                        w.flush();
                    }
                } catch (Exception e) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
                }
                return false;
            }
        };
        compile(List.of(new JsonAnnotationProcessor(), generator), """
                @Json
                @ru.tinkoff.kora.json.annotation.processor.packageForRoundProcessingTest.testGeneratedDependency.SomeAnnotation
                public record TestDto(ru.tinkoff.kora.json.annotation.processor.packageForRoundProcessingTest.testGeneratedDependency.GeneratedType field) {
                }
                """,
            """
                public @interface SomeAnnotation {}
                """);
        compileResult.assertSuccess();
        var generatedTypeWriter = (JsonWriter<Object>) (g, object) -> {
            try {
                g.writeString((String) object.getClass().getMethod("value").invoke(object));
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        };
        var generatedTypeReader = (JsonReader<Object>) parser -> {
            var t = parser.currentToken();
            if (t != JsonToken.VALUE_STRING) {
                throw new IOException();
            }
            return newObject("GeneratedType", parser.getValueAsString());
        };
        var writer = (JsonWriter<Object>) newObject("$TestDtoJsonWriter", generatedTypeWriter);
        var reader = (JsonReader<?>) newObject("$TestDtoJsonReader", generatedTypeReader);

        var o = newObject("TestDto", newObject("GeneratedType", "test"));
        var json = "{\"field\":\"test\"}";

        assertThat(writer.toByteArray(o)).asString(StandardCharsets.UTF_8).isEqualTo(json);
        assertThat(reader.read(json.getBytes(StandardCharsets.UTF_8))).isEqualTo(o);
    }
}
