package io.koraframework.config.annotation.processor;

import com.palantir.javapoet.ClassName;
import io.koraframework.annotation.processor.common.CommonClassNames;

import java.util.Optional;

public class ConfigClassNames {
    public static final ClassName config = CommonClassNames.config;
    public static final ClassName configSourceAnnotation = ClassName.get("io.koraframework.config.common.annotation", "ConfigSource");
    public static final ClassName configValueExtractorAnnotation = ClassName.get("io.koraframework.config.common.annotation", "ConfigValueExtractor");
    public static final ClassName configValue = ClassName.get("io.koraframework.config.common", "ConfigValue");
    public static final ClassName configValuePath = ClassName.get("io.koraframework.config.common", "ConfigValuePath");
    public static final ClassName pathElement = ClassName.get("io.koraframework.config.common", "PathElement");
    public static final ClassName pathElementKey = pathElement.nestedClass("Key");



    public static final ClassName configValueExtractor = CommonClassNames.configValueExtractor;
    public static final ClassName configValueExtractionException = ClassName.get("io.koraframework.config.common.extractor", "ConfigValueExtractionException");
    public static final ClassName objectValue = ClassName.get("io.koraframework.config.common", "ConfigValue", "ObjectValue");
    public static final ClassName optional = ClassName.get(Optional.class);
}
