package io.koraframework.http.server.common.annotation;

import io.koraframework.common.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Tag(PrivateApi.class)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
public @interface PrivateApi {
}
