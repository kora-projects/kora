package ru.tinkoff.kora.http.server.common.annotation;

import ru.tinkoff.kora.common.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Tag(PrivateApi.class)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
public @interface PrivateApi {
}
