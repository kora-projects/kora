package ru.tinkoff.kora.json.annotation.processor;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJsonAnnotationProcessorTest extends AbstractAnnotationProcessorTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.json.common.annotation.*;
            import java.util.Optional;
            """;
    }

    protected void compile(@Language("java") String... sources) {
        var compileResult = compile(List.of(new JsonAnnotationProcessor()), sources);
        if (compileResult.isFailed()) {
            throw compileResult.compilationException();
        }
    }

    @SuppressWarnings("unchecked")
    protected JsonReader<Object> reader(String forClass, Object... params) {
        try {
            return (JsonReader<Object>) this.compileResult.loadClass("$" + forClass + "JsonReader")
                .getConstructors()[0]
                .newInstance(params);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    protected JsonWriter<Object> writer(String forClass, Object... params) {
        try {
            return (JsonWriter<Object>) this.compileResult.loadClass("$" + forClass + "JsonWriter")
                .getConstructors()[0]
                .newInstance(params);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected ReaderAndWriter<Object> mapper(String forClass) {
        return mapper(forClass, List.of(), List.of());
    }

    protected ReaderAndWriter<Object> mapper(String forClass, List<?> readerParams, List<?> writerParams) {
        var reader = reader(forClass, readerParams.toArray());
        var writer = writer(forClass, writerParams.toArray());
        return new ReaderAndWriter<>(reader, writer);
    }

    protected static class ReaderAndWriter<T> implements JsonReader<T>, JsonWriter<T> {
        private final JsonReader<T> reader;
        private final JsonWriter<T> writer;

        protected ReaderAndWriter(JsonReader<T> reader, JsonWriter<T> writer) {
            this.reader = reader;
            this.writer = writer;
        }

        @Nullable
        @Override
        public T read(JsonParser parser) throws IOException {
            return this.reader.read(parser);
        }

        @Override
        public void write(JsonGenerator generator, @Nullable T object) throws IOException {
            this.writer.write(generator, object);
        }

        public void verify(T expectedObject, String expectedJson) {
            try {
                var json = this.writer.toByteArray(expectedObject);
                assertThat(json).asString(StandardCharsets.UTF_8).isEqualTo(expectedJson);

                var object = this.reader.read(expectedJson.getBytes(StandardCharsets.UTF_8));
                assertThat(object).isEqualTo(expectedObject);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


}
