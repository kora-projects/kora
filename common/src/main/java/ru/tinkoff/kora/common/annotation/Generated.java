package ru.tinkoff.kora.common.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <b>Русский</b>: Указывает что исходный код был сгенерирован Kora.
 * <hr>
 * <b>English</b>: Annotation is used to mark source code that has been generated.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface Generated {

    /**
     * <b>Русский</b>: Элемент value должен содержать имя генератора кода.
     * Рекомендуется использовать полное имя генератора кода.
     * <br>
     * Например:
     * <pre>com.acme.generator.CodeGen.</pre>
     * <hr>
     * <b>English</b>: The value element must have the name of the code generator.
     * The recommended convention is to use the fully qualified name of the code generator.
     * <br>
     * For example:
     * <pre> com.acme.generator.CodeGen. </pre>
     * <br>
     * <br>
     *
     * @return <b>Русский</b>: Имя генератора кода.
     * <hr>
     * <b>English</b>: The name of the code generator.
     */
    String[] value();
}
