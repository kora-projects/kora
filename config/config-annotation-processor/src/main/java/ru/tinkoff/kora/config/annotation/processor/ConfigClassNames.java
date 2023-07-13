package ru.tinkoff.kora.config.annotation.processor;

import com.squareup.javapoet.ClassName;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;

import java.util.Optional;

public class ConfigClassNames {
    public static final ClassName config = CommonClassNames.config;
    public static final ClassName configSourceAnnotation = ClassName.get("ru.tinkoff.kora.config.common.annotation", "ConfigSource");
    public static final ClassName configValueExtractorAnnotation = ClassName.get("ru.tinkoff.kora.config.common.annotation", "ConfigValueExtractor");
    public static final ClassName configValue = ClassName.get("ru.tinkoff.kora.config.common", "ConfigValue");
    public static final ClassName configValuePath = ClassName.get("ru.tinkoff.kora.config.common", "ConfigValuePath");
    public static final ClassName pathElement = ClassName.get("ru.tinkoff.kora.config.common", "PathElement");
    public static final ClassName pathElementKey = pathElement.nestedClass("Key");



    public static final ClassName configValueExtractor = CommonClassNames.configValueExtractor;
    public static final ClassName configValueExtractionException = ClassName.get("ru.tinkoff.kora.config.common.extractor", "ConfigValueExtractionException");
    public static final ClassName objectValue = ClassName.get("ru.tinkoff.kora.config.common", "ConfigValue", "ObjectValue");
    public static final ClassName optional = ClassName.get(Optional.class);
}
