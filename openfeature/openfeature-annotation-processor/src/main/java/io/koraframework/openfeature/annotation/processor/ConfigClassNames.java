package io.koraframework.openfeature.annotation.processor;

import com.palantir.javapoet.ClassName;

public final class ConfigClassNames {

    private ConfigClassNames() {}

    public static final ClassName configSourceAnnotation = ClassName.get("io.koraframework.config.common.annotation", "ConfigSource");
}
