package ru.tinkoff.kora.database.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Specifies a field that should not be used as a column in query results and parameters, but that should be used as column set of parent entity.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface Embedded {

    /**
     * @return common prefix for embedded field columns, default: snake_case field name with '_'
     */
    String value() default "";
}
