package io.koraframework.http.server.common.system;

import io.koraframework.common.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Tag(SystemApi.class)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
public @interface SystemApi { }
