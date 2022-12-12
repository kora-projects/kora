package ru.tinkoff.kora.common.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The Generated annotation is used to mark source code that has been generated.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface Generated {

    /**
     * The value element must have the name of the code generator.
     * The recommended convention is to use the fully qualified name of the code generator. For example: com.acme.generator.CodeGen.
     *
     * @return The name of the code generator
     */
    String[] value();
}
