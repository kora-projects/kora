package ru.tinkoff.kora.json.common.annotation;

import kotlin.annotation.AnnotationTarget;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * <b>Русский</b>: Аннотаций позволяет указать какое имя ключа в JSON должно соответствовать аннотированному полю, а также какой читатель и писатель использовать для поля
 * <hr>
 * <b>English</b>: Annotations allows you to specify which key name in JSON should correspond to the annotated field, as well as which reader and writer to use for the field
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Json
 * record Example(@JsonField("val") String movie) { }
 * }
 * </pre>
 * →
 * <pre>{@code
 * { "val": "Movies" }
 * }</pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@kotlin.annotation.Target(allowedTargets = {AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonField {

    /**
     * @return <b>Русский</b>: Имя ключа в JSON соответсвующее полю класса, по умолчанию равно имени поля
     * <hr>
     * <b>English</b>: Key name in JSON corresponding to the class field, by default equal to the field name
     */
    String value() default "";

    /**
     * @return <b>Русский</b>: Запись поля JSON, используемая для сериализации этого поля из JSON
     * <hr>
     * <b>English</b>: JSON field writer used for this field serialization from JSON
     */
    Class<? extends JsonWriter<?>> writer() default DefaultWriter.class;

    /**
     * @return <b>Русский</b>: Устройство чтения полей JSON, используемое для десериализации этого поля в JSON
     * <hr>
     * <b>English</b>: JSON field reader used for this field deserialization in JSON
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
