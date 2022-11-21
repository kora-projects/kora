package ru.tinkoff.kora.json.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that class/interface must be sealed and its deserialized type is will depend on {@link #value()} field, works in conjunctions with {@link JsonDiscriminatorValue} annotation
 * <p>
 * Given classes:
 * <pre>
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
 * </pre>
 * <p>
 * Json for type1 will look like:
 * <pre>
 * {
 *   "type": "type1",
 *   "value": "Movies"
 * }
 * </pre>
 * <p>
 * And for type2 will look like:
 * <pre>
 * {
 *   "type": "type2",
 *   "val": "Movies",
 *   "dig": 1
 * }
 * </pre>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface JsonDiscriminatorField {

    /**
     * @return json field name that indicates deserialization type value
     */
    String value();
}
