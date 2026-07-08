package io.koraframework.config.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация для создания Extractor'ов отображения конфигурации без указанного пути.
 * Можно использовать затем для создания Extractor для конфигураций в рамках внешних библиотек.
 * Впоследствии интерфейс может быть использован для внедрения как зависимость.
 * <hr>
 * <b>English</b>: Annotation for creating configuration reflection classes within external libraries.
 * The interface can be then used for dependency injection.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 *  {@code
 * @ConfigMapper
 * public interface MyConfig {
 *
 *     @Nullable
 *     String user();
 *
 *     default String password() {
 *         return "admin";
 *     }
 * }
 *
 * public interface MyModule {
 *
 *     default MyConfig myConfig(Config config, ConfigValueMapper<MyConfig> ext) {
 *         var value = config.get("path.to.config");
 *         return ext.map(value);
 *     }
 * }
 *  }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigMapper {

    boolean mapNullAsEmptyObject() default true;
}
