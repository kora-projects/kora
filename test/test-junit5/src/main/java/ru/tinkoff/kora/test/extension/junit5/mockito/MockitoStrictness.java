package ru.tinkoff.kora.test.extension.junit5.mockito;

import org.mockito.quality.Strictness;

import java.lang.annotation.*;

/**
 * Specifies the strictness level for all mocks created in a test class.
 * <p>
 * The {@code MockStrictness} annotation is used to configure the strictness behavior of Mockito mocks
 * within a test class. It allows developers to define how strictly Mockito should enforce the behavior
 * of mocks, such as verifying stubbed methods and detecting unused stubs or unexpected interactions.
 * </p>
 * <p>
 * This annotation can be applied to a test class to set the default {@link Strictness} level for all
 * mocks created by Mockito in that class. The strictness level can be one of the values defined in
 * {@link Strictness}, with the default being {@link Strictness#STRICT_STUBS}.
 * <b>Strictness Levels:</b>
 * <ul>
 *     <li>{@link Strictness#STRICT_STUBS} (default): Ensures that only stubbed methods are called and
 *         reports unused stubs.</li>
 *     <li>{@link Strictness#LENIENT}: Allows unstubbed calls and does not report unused stubs.</li>
 *     <li>{@link Strictness#WARN}: Warns about potential issues without failing the test.</li>
 * </ul>
 *
 * @see Strictness
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MockitoStrictness {

    /**
     * Specifies the strictness level for all mocks in the test class.
     *
     * @return the {@link Strictness} level to apply to mocks, defaults to {@link Strictness#STRICT_STUBS}
     */
    Strictness value();
}
