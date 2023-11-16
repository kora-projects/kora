package ru.tinkoff.kora.test.extension.junit5;

import ru.tinkoff.kora.common.Component;

import java.lang.annotation.*;

/**
 * Indicate that {@link Component} from graph is expected to be injected in test
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestComponent {

}
