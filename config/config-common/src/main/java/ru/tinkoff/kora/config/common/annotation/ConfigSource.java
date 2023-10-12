package ru.tinkoff.kora.config.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация для создания пользовательских классов отражения конфигурации в рамках приложении.
 * Впоследствии интерфейс может быть использован для внедрения как зависимость.
 * <hr>
 * <b>English</b>: Annotation for creating custom configuration mapped classes within an application.
 * The interface can be then used for dependency injection.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 *  {@code
 * @ConfigSource("path.to.config")
 * public interface MyConfig {
 *
 *     @Nullable
 *     String user();
 *
 *     default String password() {
 *         return "admin";
 *     }
 * }
 *  }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigSource {

    /**
     * @return <b>Русский</b>: Путь к части отображаемой конфигурации внутри файла конфигурации
     * <hr>
     * <b>English</b>: Path to the part of the mapped configuration inside the configuration file
     */
    String value();
}
