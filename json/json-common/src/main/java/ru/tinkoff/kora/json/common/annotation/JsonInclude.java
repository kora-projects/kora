package ru.tinkoff.kora.json.common.annotation;

import ru.tinkoff.kora.common.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates which attribute value should be allowed for serialization
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Tag(JsonInclude.class)
public @interface JsonInclude {

    enum IncludeType {

        /**
         * Value that indicates that property is to be always included
         */
        ALWAYS,

        /**
         * Value that indicates that only properties with non-null values are to be included.
         */
        NON_NULL,

        /**
         * Value that indicates that only properties with null value,
         * or what is considered empty, are not to be included.
         * <p>
         * Note: Can be applied ONLY to when it is possible to check {@link java.util.Collection} or {@link java.util.Map} type in compile time
         * If type is generic this check can't be applied
         */
        NON_EMPTY
    }

    /**
     * @return which value types to include when serializing
     */
    IncludeType value() default IncludeType.NON_NULL;
}
