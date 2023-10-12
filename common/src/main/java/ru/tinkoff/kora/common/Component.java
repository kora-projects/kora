package ru.tinkoff.kora.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Указывает что проаннотированный класс является компонентом и должен быть помещен в граф зависимостей контейнера
 * и может использоваться для внедрения зависимостей.
 * <br>
 * Является Singleton всегда, то есть дает гарантию, что у класса будет всего один экземпляр класса.
 * <hr>
 * <b>English</b>: Indicates that the annotated class is a component and should be placed in the dependency graph of the container
 * and can be used to enforce dependencies.
 * <br>
 * Is always Singleton, that is, it provides a guarantee that the class will have only one instance of the class.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 *  {@code
 *  @Component
 *  public final class MyService { }
 *  }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Component {

}
