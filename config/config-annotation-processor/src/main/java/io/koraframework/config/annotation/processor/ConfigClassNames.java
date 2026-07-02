package io.koraframework.config.annotation.processor;

import com.palantir.javapoet.ClassName;
import io.koraframework.annotation.processor.common.CommonClassNames;

import java.util.Optional;

public class ConfigClassNames {
    public static final ClassName config = CommonClassNames.config;
    public static final ClassName configSourceAnnotation = ClassName.get("io.koraframework.config.common.annotation", "ConfigSource");
    public static final ClassName configValueMapperAnnotation = ClassName.get("io.koraframework.config.common.annotation", "ConfigMapper");
    public static final ClassName configValue = ClassName.get("io.koraframework.config.common", "ConfigValue");
    public static final ClassName configValuePath = ClassName.get("io.koraframework.config.common", "ConfigValuePath");
    public static final ClassName pathElement = ClassName.get("io.koraframework.config.common", "PathElement");
    public static final ClassName pathElementKey = pathElement.nestedClass("Key");



    public static final ClassName configValueMapper = CommonClassNames.configValueMapper;
    public static final ClassName configValueException = ClassName.get("io.koraframework.config.common.exception", "ConfigValueException");
    public static final ClassName objectValue = ClassName.get("io.koraframework.config.common", "ConfigValue", "ObjectValue");
    public static final ClassName optional = ClassName.get(Optional.class);
}
