package io.koraframework.kora.app.annotation.processor.interceptor;

import io.koraframework.kora.app.annotation.processor.component.ResolvedComponent;

import javax.lang.model.type.TypeMirror;

public record ComponentInterceptor(ResolvedComponent component, TypeMirror interceptType) {
}
