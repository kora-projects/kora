package ru.tinkoff.kora.common;

import java.lang.annotation.*;

/**
 * <b>Русский</b>: Позволяет указывать реализацию конвертера которая будет использоваться в рамках других модулей Kora,
 * на таких этапах как запрос из базы данных, запрос HTTP клиента, ответ HTTP сервера и т.д.
 * <hr>
 * <b>English</b>: Allow to specify the converter implementation to be used within other Kora modules,
 * such as database phase conversion, HTTP client, HTTP server, etc.
 */
@Repeatable(Mapping.Mappings.class)
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface Mapping {

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @interface Mappings {
        Mapping[] value();
    }

    /**
     * <b>Русский</b>: Маркерный интерфейс который должна реализовать реализация конвертера.
     * <hr>
     * <b>English</b>: Marker interface to be implemented by the converter implementation.
     */
    interface MappingFunction {}

    Class<? extends MappingFunction> value();
}
