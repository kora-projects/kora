package ru.tinkoff.kora.json.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Use on single-field value-class to indicate that
 * it should be (de)serialized as it's field, not as object.
 * <p>
 *     Given the following class:
 *     <pre>{@code
 *     @Json
 *     record ValueClass(String value) {}
 *     }</pre>
 *
 *     Then {@code new ValueClass("test")} will be (de)serialized as:
 *     <pre>{@code
 *     {"value": "test"}
 *     }</pre>
 *
 *     But after adding {@code @JsonUnboxed}:
 *     <pre>{@code
 *     @Json
 *     @JsonUnboxed
 *     record ValueClass(String value) {}
 *     }</pre>
 *
 *     {@code new ValueClass("test")} will be (de)serialized as:
 *     <pre>{@code
 *     "test"
 *     }</pre>
 * </p>
 * <p>
 *     Old-fashioned Java value-classes, Kotlin {@code data} and {@code value} classes are also supported.
 * </p>
 * <p>
 *     If annotation is placed on a class with more than one field,
 *     error will be raised.
 * </p>
 * <p>
 *     Don't be confused with Jackson's {@code @JsonUnwrapped} annotation which is used
 *     to move object's fields one level up.
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonUnboxed {
}
