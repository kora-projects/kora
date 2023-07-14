package ru.tinkoff.kora.test.extension.junit5;

import java.lang.annotation.*;

/**
 * Indicate that annotated types will be Mocked and injected
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MockComponent {

}
