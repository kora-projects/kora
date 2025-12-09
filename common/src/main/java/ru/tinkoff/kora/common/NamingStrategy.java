package ru.tinkoff.kora.common;

import ru.tinkoff.kora.common.naming.NameConverter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Позволяет указывать реализацию конвертера строк разных конвенций именования {@link NameConverter}.
 * Применяется в таких модулях как Json и сущности базы данных и т.д.
 * <hr>
 * <b>English</b>: Allows you to specify a string converter implementation of different naming conventions {@link NameConverter}.
 * Applies to modules such as Json and database entities, etc.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface NamingStrategy {

    Class<? extends NameConverter> value();
}
