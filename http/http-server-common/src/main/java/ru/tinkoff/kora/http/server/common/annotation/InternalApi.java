package ru.tinkoff.kora.http.server.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import ru.tinkoff.kora.common.Tag;

@Tag(InternalApi.class)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
public @interface InternalApi {
}
