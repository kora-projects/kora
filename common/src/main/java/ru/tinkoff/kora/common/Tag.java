package ru.tinkoff.kora.common;

import ru.tinkoff.kora.common.naming.NameConverter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Указывает что проаннотированный компонент {@link Component} имеет определенный тег
 * и будет внедрен как зависимость именно там где требуется такой же тег.
 * <hr>
 * <b>English</b>: Indicates that the annotated component {@link Component} has a specific tag
 * and will be implemented as a dependency exactly where the same tag is required.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 *  {@code
 *  @KoraApp
 *  public interface Application {
 *
 *     @Tag(String.class)
 *     default Supplier<String> tagStrSupplier() {
 *         return () -> "1";
 *     }
 *
 *     default Supplier<String> myStr(@Tag(String.class) Supplier<String> tagStr) {
 *         return s -> tagStr.get() + "2";
 *     }
 *  }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.TYPE, ElementType.TYPE_USE})
public @interface Tag {

    /**
     * <b>Русский</b>: Специальный тип тега который означает что тэг подходит под любое условие внедрения зависимости, то есть соответствует любому тэгу.
     * <hr>
     * <b>English</b>: A special tag type which means that the tag matches any dependency enforcement condition, i.e. it matches any tag
     */
    final class Any {}

    Class<?>[] value();
}
