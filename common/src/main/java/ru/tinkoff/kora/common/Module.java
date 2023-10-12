package ru.tinkoff.kora.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Указывает что интерфейс является модулем в рамках основного проектного модуля в котором также находится {@link KoraApp}.
 * Используется {@link KoraApp} как часть контейнера зависимостей.
 * <hr>
 * <b>English</b>: Indicates that the interface is a module within an external project module.
 * Used by {@link KoraApp} as part of the dependency container.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 *  {@code
 *  @Module
 *  public interface MyModule {
 *     default Supplier<String> strSupplier() {
 *         return () -> "1";
 *     }
 *  }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface Module {
}
