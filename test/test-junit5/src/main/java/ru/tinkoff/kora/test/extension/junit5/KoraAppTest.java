package ru.tinkoff.kora.test.extension.junit5;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.KoraApp;

import java.lang.annotation.*;

/**
 * In case you want to initialize {@link KoraAppGraph} per Test Class, annotate your Test Class with {@link TestInstance.Lifecycle#PER_CLASS},
 * by default {@link TestInstance.Lifecycle#PER_METHOD} is used, indicating that new {@link KoraAppGraph} will be initialized each Test Method
 */
@ExtendWith(KoraJUnit5Extension.class)
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface KoraAppTest {

    /**
     * @return class annotated with {@link KoraApp}
     */
    Class<?> value();

    /**
     * @return {@link Component} that should be included in Context initialization in addition to {@link TestComponent} found in tests
     */
    Class<?>[] components() default {};

    /**
     * @return Modules that should be included in Context initialization in addition to {@link TestComponent} found in tests
     */
    Class<?>[] modules() default {};
}
