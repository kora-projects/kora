package ru.tinkoff.kora.database.common.annotation;

import org.intellij.lang.annotations.Language;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates SQL query.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Query {

    /**
     * @return SQL query
     */
    @Language("SQL")
    String value();
}
