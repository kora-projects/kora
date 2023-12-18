package ru.tinkoff.kora.json.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что для типа типа будет создан читатель JSON и внедрен в контейнер зависимостей.
 * <hr>
 * <b>English</b>: Annotation specifies that a JSON reader will be created for the type and embedded in the dependency container.
 */
@Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonReader { }
