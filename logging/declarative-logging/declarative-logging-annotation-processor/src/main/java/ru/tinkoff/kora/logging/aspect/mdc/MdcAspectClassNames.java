package ru.tinkoff.kora.logging.aspect.mdc;

import com.squareup.javapoet.ClassName;

public class MdcAspectClassNames {
    public static final ClassName mdcAnnotation = ClassName.get("ru.tinkoff.kora.logging.common.annotation", "Mdc");
    public static final ClassName mdcContainerAnnotation = ClassName.get("ru.tinkoff.kora.logging.common.annotation", "Mdc", "MdcContainer");
    public static final ClassName mdc = ClassName.get("ru.tinkoff.kora.logging.common", "MDC");
}
