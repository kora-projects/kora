package io.koraframework.logging.aspect.mdc;

import com.palantir.javapoet.ClassName;

public class MdcAspectClassNames {
    public static final ClassName mdcAnnotation = ClassName.get("io.koraframework.logging.common.annotation", "Mdc");
    public static final ClassName mdcContainerAnnotation = ClassName.get("io.koraframework.logging.common.annotation", "Mdc", "MdcContainer");
    public static final ClassName mdc = ClassName.get("io.koraframework.logging.common", "MDC");
}
