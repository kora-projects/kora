package ru.tinkoff.kora.avro.common.annotation;

import ru.tinkoff.kora.common.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что для типа представлен в бинарном формате AVRO.
 * <hr>
 * <b>English</b>: Annotation specifies that for the type is represented in AVRO binary format.
 */
@Tag(AvroBinary.class)
@Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AvroBinary {

}
