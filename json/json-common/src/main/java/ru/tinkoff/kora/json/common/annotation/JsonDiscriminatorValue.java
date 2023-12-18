package ru.tinkoff.kora.json.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает, что класс/интерфейс является частью запечатанного класса, а аннотированный тип связан со значением поля {@link #value()}, работает в связке с верхней аннотацией {@link JsonDiscriminatorField}.
 * <hr>
 * <b>English</b>: Annotation indicates that class/interface is part of sealed class and annotated type is associated with {@link #value()} field value, works in conjunctions with top {@link JsonDiscriminatorField} annotation
 * @see JsonDiscriminatorField
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonDiscriminatorValue {

    /**
     * @return <b>Русский</b>: Значения поля {@link JsonDiscriminatorField} с которыми будет ассоциироваться проаннотированный тип
     * <hr>
     * <b>English</b>: The {@link JsonDiscriminatorField} field values with which the annotated type will be associated
     */
    String[] value();
}
