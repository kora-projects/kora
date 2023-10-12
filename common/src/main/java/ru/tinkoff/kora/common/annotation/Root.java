package ru.tinkoff.kora.common.annotation;

import ru.tinkoff.kora.common.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Указывает что {@link Component} является корнем в графе контейнера
 * и подлежит обязательной регистрации в графе даже если не требуется как зависимость другими компонентами.
 * <hr>
 * <b>English</b>: Indicates that {@link Component} is the root in the container graph
 * and must be registered in the graph even if it is not required as a dependency by other components.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 *  {@code
 *  @Root
 *  @Component
 *  class MyService { }
 *  }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Root {

}
