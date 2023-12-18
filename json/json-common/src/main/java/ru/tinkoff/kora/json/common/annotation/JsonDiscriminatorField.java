package ru.tinkoff.kora.json.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает, что класс/интерфейс должен быть запечатанным (<i>sealed</i>), а его тип десериализации будет зависеть от поля {@link #value()}, работает в связке с аннотацией {@link JsonDiscriminatorValue}.
 * <hr>
 * <b>English</b>: Annotation indicates that class/interface must be sealed and its deserialized type is will depend on {@link #value()} field, works in conjunctions with {@link JsonDiscriminatorValue} annotation
 * <br>
 * <br>
 * Пример / Example:
 * <pre>{@code
 * @Json
 * @JsonDiscriminatorField("type")
 * public sealed interface SealedDto {
 *
 *     @JsonDiscriminatorValue("type1")
 *     record FirstType(String value) implements SealedDto {}
 *
 *     @JsonDiscriminatorValue("type2")
 *     record SecondType(String val, int dig) implements SealedDto {}
 * }
 * }</pre>
 * <p>
 * Json for <i>type1</i>:
 * <pre>{@code
 * { "type": "type1", "value": "Movies" }
 * }</pre>
 * <p>
 * Json for <i>type2</i>:
 * <pre>{@code
 * { "type": "type2", "val": "Movies", "dig": 1 }
 * }</pre>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonDiscriminatorField {

    /**
     * @return <b>Русский</b>: Имя поле которое будет определять тип десериализуемого значения
     * <hr>
     * <b>English</b>: Name of the field that will determine the type of the deserializable value
     */
    String value();
}
