package io.koraframework.openfeature.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface OpenfeatureSource {

    /**
     * @return <b>Русский</b>: Путь к части отображаемой конфигурации внутри файла конфигурации
     * <hr>
     * <b>English</b>: Path to the part of the mapped configuration inside the configuration file
     */
    String value();
}
