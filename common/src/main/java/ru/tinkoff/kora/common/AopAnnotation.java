package ru.tinkoff.kora.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Специальная аннотация, которая указывает что проаннотированная аннотация является Аспектом и должна быть обработана соответствующим генератором.
 * <hr>
 * <b>English</b>: Special annotation, that indicates that the annotated annotation is an Aspect and should be processed by the corresponding generator.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface AopAnnotation {

}
