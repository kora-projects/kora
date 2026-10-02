package io.koraframework.openfeature.annotation.processor;

import com.squareup.javapoet.ClassName;
import io.koraframework.annotation.processor.common.CommonClassNames;

import java.util.Optional;

public final class ConfigClassNames {

    private ConfigClassNames() {}

    public static final ClassName configSourceAnnotation = ClassName.get("io.koraframework.config.common.annotation", "ConfigSource");
    public static final ClassName configValueExtractor = CommonClassNames.configValueExtractor;
}
