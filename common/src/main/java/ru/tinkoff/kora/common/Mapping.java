package ru.tinkoff.kora.common;

import java.lang.annotation.*;

@Repeatable(Mapping.Mappings.class)
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface Mapping {
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @interface Mappings {
        Mapping[] value();
    }

    // marker interface
    interface MappingFunction {}

    Class<? extends MappingFunction> value();
}
