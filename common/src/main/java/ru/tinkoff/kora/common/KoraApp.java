package ru.tinkoff.kora.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Supplier;

/**
 * <b>Русский</b>: Указывает что интерфейс является главным модулем всего приложения который является агрегатом всех других {@link Module}
 * и является главной конфигурацией контейнера зависимостей.
 * <hr>
 * <b>English</b>: Indicates that interface is the main module of the entire application which is the aggregate of all other {@link Module}
 * and is the main configuration of all dependencies.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 *  {@code
 *  @KoraApp
 *  public interface Application {
 *     default Supplier<String> strSupplier() {
 *         return () -> "1";
 *     }
 * }
 *  }
 * </pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface KoraApp {

    interface d {
    default Supplier<String> mySup() {
        return () -> "1";
    }

    }

}
