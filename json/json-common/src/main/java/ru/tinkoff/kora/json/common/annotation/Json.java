package ru.tinkoff.kora.json.common.annotation;

import ru.tinkoff.kora.common.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что для типа представлен в формате JSON. Если указан для типа то для данного типа будет создан читатель и писатель JSON и внедрен в контейнер зависимостей.
 * <hr>
 * <b>English</b>: Annotation specifies that for the type is represented in JSON format. If specified for a type, a JSON reader and writer will be created for that type and embedded in the dependency container.
 */
@Tag(Json.class)
@Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Json { }
