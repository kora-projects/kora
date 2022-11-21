package ru.tinkoff.kora.kora.app.annotation.processor.interceptor;

import ru.tinkoff.kora.kora.app.annotation.processor.component.ResolvedComponent;

import javax.lang.model.type.TypeMirror;

public record ComponentInterceptor(ResolvedComponent component, TypeMirror interceptType) {
}
