package io.koraframework.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Указывает что метод модуля возвращает объект, который сам является модулем.
 * Возвращаемый тип регистрируется в графе зависимостей контейнера как компонент,
 * а его методы-фабрики также обрабатываются как поставщики компонентов.
 * <hr>
 * <b>English</b>: Indicates that a module method returns an object that is itself a module.
 * The return type is registered in the container's dependency graph as a component,
 * and its factory methods are also processed as component providers.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 *  {@code
 *  public interface MyModule {
 *
 *      @FactoryModule
 *      default InnerModule innerModule() {
 *          return new InnerModule();
 *      }
 *
 *      interface InnerModule {
 *          default Supplier<String> strSupplier() {
 *              return () -> "value";
 *          }
 *      }
 *  }
 *  }
 * </pre>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface FactoryModule {
}
