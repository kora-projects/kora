package ru.tinkoff.kora.test.extension.junit5;

import ru.tinkoff.kora.common.Component;

import java.lang.annotation.*;

/**
 * Indicate that {@link Component} from {@link KoraAppTest#components()} is expected to be injected from graph
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestComponent {

}
