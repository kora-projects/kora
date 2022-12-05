package ru.tinkoff.kora.json.common.annotation;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates how JSON field must be associated with DTO field.
 *
 * <pre>{@code
 * @Json
 * record Example(@JsonField("val") String movie){}
 * }</pre>
 * <p>
 * With corresponding JSON:
 * <pre>{@code
 * {
 *   "val": "Movies"
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER, ElementType.FIELD})
public @interface JsonField {

    /**
     * @return JSON field name associated with annotated field
     */
    String value() default "";

    /**
     * @return JSON field writer used for this field serialization from JSON
     */
    Class<? extends JsonWriter<?>> writer() default DefaultWriter.class;

    /**
     * @return JSON field reader used for this field deserialization in JSON
     */
    Class<? extends JsonReader<?>> reader() default DefaultReader.class;

    final class DefaultWriter implements JsonWriter<Object> {
        @Override
        public void write(JsonGenerator gen, Object object) {

        }
    }

    final class DefaultReader implements JsonReader<Object> {
        @Override
        public Object read(JsonParser gen) {
            return null;
        }
    }
}
