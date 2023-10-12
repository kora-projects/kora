package ru.tinkoff.kora.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Consumer;

/**
 * <b>Русский</b>: Указывает что проаннотированная фабрика является предоставляет компонентом для внедрения по умолчанию, будет использован
 * если другой такой же тип с таким же {@link Tag} не найдет в графе зависимостей контейнера.
 * Является Singleton, то есть дает гарантию, что у класса будет всего один экземпляр класса.
 * <hr>
 * <b>English</b>: Indicates that the annotated factory provides the default component for injection, will be used
 * if another component of the same type with the same {@link Tag} is not found in the container's dependency graph.
 * Is Singleton, that is, it provides a guarantee that the class will have only one instance of the class.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 *  {@code
 *  public interface MyModule {
 *
 *     @DefaultComponent
 *     default Supplier<String> strSupplier() {
 *         return () -> "1";
 *     }
 * }
 *  }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DefaultComponent {

}
