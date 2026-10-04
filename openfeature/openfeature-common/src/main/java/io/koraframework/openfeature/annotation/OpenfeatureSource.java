package io.koraframework.openfeature.annotation;

import io.koraframework.common.annotation.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface OpenfeatureSource {

    /**
     * @return <b>Русский</b>: Путь к значениям по умолчанию в конфигурации и префикс ключей флагов.
     * <hr>
     * <b>English</b>: Configuration path for fallback values and prefix of flag keys.
     */
    String value();

    /** Selects a factory's Client and tags this source's flag registrar; Tag.class selects the default client. */
    Class<?> clientTag() default Tag.class;
}
